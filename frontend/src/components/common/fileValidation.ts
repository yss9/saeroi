/**
 * 파일 선택 검증. `FileUploader` 가 쓰고, 폼 제출 직전에 한 번 더 보고 싶은 화면도 쓴다.
 *
 * `FileUploader.tsx` 와 분리한 이유는 두 가지다. 컴포넌트 파일이 컴포넌트 외의 것을
 * 내보내면 Fast Refresh 가 동작하지 않고(`react-refresh/only-export-components`),
 * 검증 자체는 React 와 무관해서 렌더 없이 테스트할 수 있는 편이 낫다.
 * (같은 이유로 `quantity.ts` 가 `QuantityInput.tsx` 옆에 따로 있다.)
 *
 * 정책 근거: 전 파트 공통 업로드 정책
 * `backend:docs/superpowers/specs/2026-09-07-file-upload-policy.md`.
 */

/**
 * 파일이 거절된 이유.
 *
 * 코드로 두는 이유는 **문구를 여기서 만들기 때문**이다. 호출부가 문구를 만들면
 * 도메인마다 다른 말로 같은 실패를 설명하게 되고, 사용자는 화면마다 다른 안내를 본다.
 * 서버 `FileStorageErrorCode` 와 이름을 맞춰 뒀다 — 앞에서 막은 것과 뒤에서 거절된 것이
 * 같은 이유일 때 같은 말이 나와야 한다.
 */
export type FileRejectionReason =
  /** 확장자·미디어 타입이 `accept` 에 없다 (`FILE_TYPE_NOT_ALLOWED`) */
  | 'TYPE'
  /** `maxSizeBytes` 초과 (`FILE_TOO_LARGE`) */
  | 'SIZE'
  /** 0바이트 (`FILE_EMPTY`) */
  | 'EMPTY'
  /** `maxFiles` 초과 (`FILE_COUNT_EXCEEDED`) */
  | 'COUNT'

export interface FileRejection {
  file: File
  reason: FileRejectionReason
}

export interface FileValidationPolicy {
  /** `<input accept>` 와 **같은 문자열**을 받는다 (아래 `matchesAccept` 주석 참고) */
  accept?: string
  maxSizeBytes: number
  maxFiles?: number
}

/**
 * 정책에 맞는 것과 아닌 것을 가른다.
 *
 * 순서가 있다 — **개수를 먼저 본다.** 개수 초과는 요청 단위 실패라서 일부만 받으면
 * 사용자가 고른 것과 올라간 것이 달라진다. 그래서 초과면 전부 거절한다.
 *
 * ⚠️ **`svg` 를 `accept` 에 넣지 마라.** 사용자에게는 이미지지만 브라우저에는 스크립트
 * 컨테이너다. 우리 오리진에서 서빙하면 저장형 XSS 가 된다 (정책 문서에서 같은 이유로
 * 화이트리스트에 없다). 이 함수는 `accept` 를 그대로 따르므로 막아 주지 않는다.
 */
export function validateFiles(
  files: File[],
  { accept, maxSizeBytes, maxFiles }: FileValidationPolicy,
): { accepted: File[]; rejected: FileRejection[] } {
  if (maxFiles !== undefined && files.length > maxFiles) {
    return { accepted: [], rejected: files.map((file) => ({ file, reason: 'COUNT' as const })) }
  }

  const accepted: File[] = []
  const rejected: FileRejection[] = []

  for (const file of files) {
    if (!matchesAccept(file, accept)) rejected.push({ file, reason: 'TYPE' })
    else if (file.size === 0) rejected.push({ file, reason: 'EMPTY' })
    else if (file.size > maxSizeBytes) rejected.push({ file, reason: 'SIZE' })
    else accepted.push(file)
  }

  return { accepted, rejected }
}

/**
 * `accept` 문법(확장자 · 미디어 타입 · 와일드카드)을 그대로 판정한다.
 *
 * 브라우저가 파일 선택창에 쓰는 것과 **같은 문자열로 검사**하는 것이 요점이다. 별도
 * 형식으로 정책을 한 번 더 적으면 선택창에서 보이는 것과 통과하는 것이 어긋난다.
 */
function matchesAccept(file: File, accept?: string): boolean {
  const rules = (accept ?? '')
    .split(',')
    .map((rule) => rule.trim().toLowerCase())
    .filter(Boolean)
  if (rules.length === 0) return true

  const name = file.name.toLowerCase()
  const type = file.type.toLowerCase()

  return rules.some((rule) => {
    if (rule.startsWith('.')) return name.endsWith(rule)
    if (rule.endsWith('/*')) return type.startsWith(rule.slice(0, -1))
    // 미디어 타입 규칙인데 파일에 타입이 없는 경우(일부 OS·확장자 없는 파일)는
    // 판정을 못 한다. 통과시키고 서버가 보게 한다 — 앞에서 막으면 올릴 수 없는 파일이
    // 되고, 서버는 실제 내용으로 검사하므로 위험이 늘지 않는다.
    return type === '' || type === rule
  })
}

/** 사유별로 묶는다. 같은 이유로 빠진 파일은 한 문장으로 말해야 읽힌다 */
export function groupRejections(rejections: FileRejection[]): [FileRejectionReason, File[]][] {
  const grouped = new Map<FileRejectionReason, File[]>()
  for (const { file, reason } of rejections) {
    grouped.set(reason, [...(grouped.get(reason) ?? []), file])
  }
  return [...grouped]
}

export function rejectionMessage(
  reason: FileRejectionReason,
  files: File[],
  { maxSizeBytes, maxFiles }: Pick<FileValidationPolicy, 'maxSizeBytes' | 'maxFiles'>,
): string {
  const names = files.map((file) => file.name).join(', ')
  switch (reason) {
    case 'COUNT':
      // 조용히 앞에서 몇 개만 받으면 사용자는 무엇이 왜 빠졌는지 모른다.
      return `한 번에 ${maxFiles}개까지 올릴 수 있습니다. 나눠서 올려 주세요.`
    case 'SIZE':
      return `용량이 커서 제외됨(개당 ${formatBytes(maxSizeBytes)} 이하): ${names}`
    case 'TYPE':
      return `허용되지 않는 형식이라 제외됨: ${names}`
    case 'EMPTY':
      return `내용이 없는 파일이라 제외됨: ${names}`
  }
}

/**
 * 상한을 사람이 읽는 단위로. 1MB 미만이면 KB 로 말한다 — `0.0MB` 는 "0" 으로 읽혀
 * 아무것도 올릴 수 없다는 뜻이 된다.
 */
function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`
  const megabytes = bytes / (1024 * 1024)
  return `${Number.isInteger(megabytes) ? megabytes : megabytes.toFixed(1)}MB`
}

/**
 * `<img>` 로 그릴 수 있는 형식인가.
 *
 * **`svg` 는 여기서도 제외된다.** 로컬 blob URL 이라 저장형 XSS 는 아니지만, 그려지는
 * 것을 보면 사용자는 올릴 수 있다고 믿는다 — 서버는 거절한다.
 */
export function isPreviewableImage(contentType: string): boolean {
  return /^image\/(jpeg|png|webp|gif|avif)$/.test(contentType.toLowerCase())
}
