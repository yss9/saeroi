import { useId, useState } from 'react'
import {
  groupRejections,
  rejectionMessage,
  validateFiles,
  type FileRejection,
} from './fileValidation.ts'
import { LocalFilePreview } from './LocalFilePreview.tsx'

export interface FileUploaderProps {
  /**
   * 허용 형식. 확장자(`.jpg`) · 미디어 타입(`image/png`) · 와일드카드(`image/*`) 를
   * 섞어 쓸 수 있다.
   *
   * ⚠️ **정책을 이 컴포넌트에 하드코딩하지 않는다.** 도메인마다 받는 형식이 다르고
   * (증빙은 pdf 를 받고 제품 이미지는 받지 않는다), 서버 정책과 어긋나면 사용자가 고른
   * 파일이 업로드 단계에서 거절된다. 값의 출처는 각 도메인의 `types` 파일 한 곳이다.
   */
  accept?: string
  multiple?: boolean
  /**
   * 개별 파일 최대 크기(바이트).
   *
   * 기본값 10MB 의 근거: 전 파트 공통 업로드 정책
   * (`backend:docs/superpowers/specs/2026-09-07-file-upload-policy.md`) 이 파일당 10MB 를
   * 상한으로 정했고 서버도 같은 값으로 막는다. 그보다 **큰 값을 넘기면 앞에서 통과한
   * 파일이 서버에서 거절된다** — 기본값은 편의이고 정책은 도메인이 명시하는 것이 맞다.
   */
  maxSizeBytes?: number
  /**
   * 한 번에 고를 수 있는 개수. 기본값 없음(무제한)이다 — 정책 문서의 요청당 10개는
   * **증빙 업로드 엔드포인트의 규칙**이고, 다른 도메인(제품 이미지는 1장)에 적용하면
   * 틀린 안내가 된다. 필요한 도메인이 넘긴다.
   */
  maxFiles?: number
  label?: string
  /** 라벨 아래 보조 설명. 허용 형식·상한을 사람 말로 적는 자리다 */
  hint?: string
  disabled?: boolean
  /**
   * 고른 파일의 **로컬** 미리보기를 그린다 (이미지만).
   *
   * 서버를 거치지 않는다 — 브라우저가 이미 파일을 갖고 있고, 업로드 전에는 서버에
   * 그 파일이 없다. 기본값 `false` 인 것은 이미 자기 방식으로 미리보기를 그리는
   * 화면(제품 등록 폼)에 두 번 그려지지 않게 하기 위한 것이다.
   */
  showPreview?: boolean
  /**
   * 업로드가 진행 중이다. 진행 표시를 이 컴포넌트가 그린다 — 파일을 고른 자리와
   * 진행 표시가 떨어져 있으면 사용자는 무엇이 올라가는 중인지 알기 어렵다.
   */
  uploading?: boolean
  /** 0~100. 모르면 넘기지 않는다 (진행률 없이 "올리는 중" 만 표시한다) */
  progress?: number | null
  /**
   * 검증을 통과한 파일만 넘어온다. 하나도 통과하지 못하면 **빈 배열로 부른다** —
   * 부르지 않으면 이전 선택이 화면에 남아 사용자는 새로 고른 파일이 반영된 줄 안다.
   */
  onChange: (files: File[]) => void
  /** 거절 사유를 화면 밖(로깅·폼 상태)에서도 알아야 할 때만 쓴다. 문구는 여기서 그린다 */
  onReject?: (rejections: FileRejection[]) => void
}

/** 정책 문서(`2026-09-07-file-upload-policy.md`)의 파일당 상한. 서버도 같은 값이다 */
const DEFAULT_MAX_SIZE = 10 * 1024 * 1024

/**
 * 공통 파일 선택·검증 컴포넌트. S15P21E104-253.
 *
 * <h2>업로드는 하지 않는다</h2>
 *
 * <p>선택된 `File` 객체만 넘긴다. 실제 전송은 각 도메인의 Feature API 가 한다 —
 * 엔드포인트·폼 필드 이름·성공 후 무효화할 캐시가 도메인마다 다르다. 여기서 보내면
 * 그 차이를 prop 으로 다시 받아야 하고, 그건 도메인 로직이 공통 컴포넌트로 새는 것이다.
 *
 * <h2>정책은 prop 으로 받는다</h2>
 *
 * <p>확장자·크기·개수를 **여기에 박아 두지 않는다.** 증빙은 `.pdf` 를 받고 제품 이미지는
 * 받지 않으며, 개수 상한도 엔드포인트마다 다르다. 기본값을 둔 것은 크기 하나뿐이고
 * 그 근거는 `maxSizeBytes` 주석에 있다.
 *
 * <h2>왜 클라이언트에서도 검증하는가</h2>
 *
 * <p>중복이 아니다. `accept` 속성은 **파일 선택창의 힌트일 뿐 강제가 아니다** — 사용자가
 * "모든 파일" 로 바꾸거나 드래그로 놓으면 그대로 들어온다. 서버 거절은 요청을 보낸 뒤에
 * 오고 그때까지 사용자는 올라가는 중이라고 생각한다. 앞에서 막으면 **고르는 순간**
 * 이유를 알 수 있다. 다만 앞의 검증은 UX 이고 **진짜 통제는 서버**다 — 이름을 바꾼
 * `.heic` 는 여기서 통과하고 서버가 `FILE_TYPE_MISMATCH` 로 잡는다.
 *
 * <h2>거절을 조용히 하지 않는다</h2>
 *
 * <p>거절된 파일을 그냥 버리면 사용자는 10개를 골랐는데 몇 개가 왜 빠졌는지 모른다.
 * 사유별로 이름을 밝힌다.
 */
export function FileUploader({
  accept,
  multiple = false,
  maxSizeBytes = DEFAULT_MAX_SIZE,
  maxFiles,
  label = '파일 선택',
  hint,
  disabled,
  showPreview = false,
  uploading = false,
  progress = null,
  onChange,
  onReject,
}: FileUploaderProps) {
  const inputId = useId()
  const [rejections, setRejections] = useState<FileRejection[]>([])
  const [dragging, setDragging] = useState(false)
  const [accepted, setAccepted] = useState<File[]>([])

  function handleSelected(selected: File[]) {
    const result = validateFiles(selected, { accept, maxSizeBytes, maxFiles })

    setRejections(result.rejected)
    onReject?.(result.rejected)
    setAccepted(result.accepted)
    onChange(result.accepted)
  }

  const disabledAll = disabled || uploading

  return (
    <div className="ui-field ui-uploader">
      <label className="ui-field__label" htmlFor={inputId}>
        {label}
      </label>

      {/*
        드롭 영역과 <input type="file"> 을 같이 둔다. input 을 숨기지 않는 이유 —
        숨기면 라벨 클릭으로만 열 수 있고, 스크린 리더·키보드 경로가 브라우저 구현에
        기대게 된다. 보이는 input 은 그 자체로 접근 가능하다.
      */}
      <div
        className={`ui-uploader__drop${dragging ? ' ui-uploader__drop--active' : ''}`}
        onDragOver={(event) => {
          if (disabledAll) return
          // preventDefault 를 하지 않으면 브라우저가 파일을 새 탭으로 열어 버려
          // 작성 중인 폼이 그대로 사라진다.
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          if (disabledAll) return
          event.preventDefault()
          setDragging(false)
          const dropped = Array.from(event.dataTransfer.files)
          // multiple 이 아니면 첫 장만 받는다. 드래그는 여러 개를 한 번에 놓기 쉽고,
          // 조용히 마지막 것을 쓰면 사용자가 의도한 파일과 다른 것이 올라간다.
          handleSelected(multiple ? dropped : dropped.slice(0, 1))
        }}
      >
        <input
          id={inputId}
          className="ui-field__control"
          type="file"
          accept={accept}
          multiple={multiple}
          disabled={disabledAll}
          onChange={(event) => {
            handleSelected(Array.from(event.target.files ?? []))
            // 같은 파일을 다시 고를 수 있어야 한다. value 를 비우지 않으면 브라우저가
            // change 를 발생시키지 않아, 실패 후 같은 파일로 재시도할 수 없다.
            event.target.value = ''
          }}
        />
        <span className="ui-uploader__drop-hint" aria-hidden="true">
          여기로 파일을 끌어다 놓을 수도 있습니다
        </span>
      </div>

      {hint && <p className="ui-field__hint">{hint}</p>}

      {rejections.length > 0 && (
        // 사유가 여러 가지여도 alert 는 하나다. 여러 개로 나누면 스크린 리더가 마지막
        // 것만 읽거나 순서가 뒤섞인다.
        <div className="ui-field__error" role="alert">
          {groupRejections(rejections).map(([reason, files]) => (
            <p key={reason}>{rejectionMessage(reason, files, { maxSizeBytes, maxFiles })}</p>
          ))}
        </div>
      )}

      {showPreview && accepted.length > 0 && (
        <ul className="ui-uploader__previews" aria-label="선택한 파일 미리보기">
          {accepted.map((file) => (
            // 서버를 거치지 않은 로컬 미리보기다. 아직 업로드되지 않았다.
            <li key={fileKey(file)}>
              <LocalFilePreview file={file} />
              <span>{file.name}</span>
            </li>
          ))}
        </ul>
      )}

      {uploading && (
        <div className="ui-uploader__progress" role="status">
          {progress === null ? (
            <span>파일을 올리는 중…</span>
          ) : (
            <>
              {/*
                progress 를 텍스트로도 말한다. <progress> 값만 두면 스크린 리더 구현에
                따라 읽히지 않는다.
              */}
              <progress max={100} value={progress} />
              <span>{Math.round(progress)}% 올리는 중…</span>
            </>
          )}
        </div>
      )}
    </div>
  )
}

/** 같은 이름이라도 크기가 다르면 다른 파일이다. key 충돌로 미리보기가 겹치지 않게 한다 */
function fileKey(file: File): string {
  return `${file.name}-${file.size}-${file.lastModified}`
}
