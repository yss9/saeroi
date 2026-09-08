// 빌드 시점에 번들로 인라인되는 설정값(VITE_*).
//
// 주의: 미설정 키는 `undefined` 가 아니라 **빈 문자열**로 들어온다.
// Jenkins 파이프라인이 EC2 `.env` 의 `VITE_BESU_CHAIN_ID=` 같은 줄을 그대로
// `--build-arg` 로 넘기기 때문이다. 그래서 아래에서 빈 문자열을 null 로 정규화한다.
// 이 처리를 빼면 `if (chainId)` 류의 검사가 조용히 통과해 버린다.
//
// 여기 있는 값은 전부 브라우저에 그대로 노출되는 공개 값이다. 비밀값을 넣지 말 것.

/** 빈 문자열·공백만 있는 값을 null 로 정규화한다. */
function optional(value: string | undefined): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

export const env = {
  /** 백엔드 API 기준 경로. nginx 가 같은 오리진에서 /api/ 를 backend 로 넘긴다. */
  apiBaseUrl: optional(import.meta.env.VITE_API_BASE_URL) ?? '/api',

  /** SSAFY Besu 네트워크. 1번(Blockchain) 산출물이 나오기 전까지 둘 다 null 이다. */
  besuRpcUrl: optional(import.meta.env.VITE_BESU_RPC_URL),

  /**
   * 기대 체인 ID. `.env` 에는 10진수로 들어올 수도 16진수로 들어올 수도 있어
   * 지갑이 쓰는 16진수 문자열로 통일한다. 값이 없으면 null → 체인 검증 UI 자체가 꺼진다.
   */
  besuChainIdHex: toChainIdHex(optional(import.meta.env.VITE_BESU_CHAIN_ID)),

  /**
   * 블록 익스플로러 주소. 없으면 트랜잭션 해시를 텍스트로만 보여준다.
   *
   * 링크가 되면 사용자가 **우리 화면 밖에서** 사실을 확인할 수 있다. "체인에 기록됐다" 는
   * 우리 말을 믿으라고 하는 것과, 직접 볼 수 있게 하는 것은 다르다 — 블록체인을 쓰는 이유가
   * 그것이기도 하다.
   */
  besuExplorerUrl: optional(import.meta.env.VITE_BESU_EXPLORER_URL),
} as const

/** '1337' 또는 '0x539' 를 모두 '0x539' 로 만든다. 해석 불가면 null. */
export function toChainIdHex(raw: string | null): string | null {
  if (!raw) return null
  if (raw.startsWith('0x') || raw.startsWith('0X')) {
    return /^0[xX][0-9a-fA-F]+$/.test(raw) ? `0x${raw.slice(2).toLowerCase()}` : null
  }
  if (!/^\d+$/.test(raw)) return null
  return `0x${Number(raw).toString(16)}`
}
