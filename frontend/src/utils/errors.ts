// 앱 전역 에러 모델.
//
// 목적: Page 나 Component 가 axios 에러나 MetaMask 의 EIP-1193 에러를 직접
// 파싱하지 않게 하는 것이다. 변환은 apiClient / walletService 에서 한 번만 하고,
// UI 는 아래 `AppError` 의 `kind` 만 보고 분기한다.

import type { ValidationError } from '../types/api.types.ts'

export const ERROR_KIND = {
  /** Backend 가 4xx/5xx 를 규격대로 내려준 경우 */
  API_ERROR: 'API_ERROR',
  /** 응답 자체를 받지 못한 경우 (오프라인, 타임아웃, CORS) */
  NETWORK_ERROR: 'NETWORK_ERROR',

  /** window.ethereum 부재 — 확장 프로그램 미설치 */
  WALLET_NOT_FOUND: 'WALLET_NOT_FOUND',
  /** 지갑은 있으나 이 사이트에 연결된 계정이 없음 */
  WALLET_DISCONNECTED: 'WALLET_DISCONNECTED',
  /** 사용자가 서명 팝업에서 취소 (EIP-1193 code 4001) — 에러가 아니라 정상 흐름 */
  SIGNATURE_REJECTED: 'SIGNATURE_REJECTED',
  /** 연결된 체인이 기대 체인과 다름 */
  WRONG_NETWORK: 'WRONG_NETWORK',

  /** 트랜잭션은 전송됐으나 receipt.status 가 0 이거나 revert 됨 */
  TRANSACTION_FAILED: 'TRANSACTION_FAILED',

  /** 위 어디에도 해당하지 않음 */
  UNKNOWN: 'UNKNOWN',
} as const

export type ErrorKind = (typeof ERROR_KIND)[keyof typeof ERROR_KIND]

export interface AppError {
  kind: ErrorKind
  /** 사용자에게 그대로 보여줄 수 있는 문구 */
  message: string
  /** Backend ErrorCode 의 code (예: 'RESOURCE_NOT_FOUND'). API 에러가 아니면 null */
  code: string | null
  /** HTTP 상태. API 에러가 아니면 null */
  status: number | null
  /** VALIDATION_ERROR 일 때 필드별 오류 */
  validationErrors: ValidationError[]
  /** 디버깅용 원본. 로깅에만 쓰고 화면에 출력하지 않는다. */
  cause: unknown
  /** 업무 오류 응답의 data. 화면에서 코드별로 검증한 뒤 사용한다. */
  details?: unknown
}

/** 기본 문구. Backend 가 message 를 주면 그쪽이 우선한다. */
const DEFAULT_MESSAGE: Record<ErrorKind, string> = {
  API_ERROR: '요청을 처리하지 못했습니다.',
  NETWORK_ERROR: '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.',
  WALLET_NOT_FOUND: 'MetaMask가 설치되어 있지 않습니다.',
  WALLET_DISCONNECTED: '지갑이 연결되어 있지 않습니다.',
  SIGNATURE_REJECTED: '요청을 취소했습니다.',
  WRONG_NETWORK: '현재 지갑 연결 환경에서는 사용할 수 없습니다. 연결 환경을 변경해 주세요.',
  TRANSACTION_FAILED: '이력을 저장하지 못했습니다.',
  UNKNOWN: '알 수 없는 오류가 발생했습니다.',
}

export function createAppError(
  kind: ErrorKind,
  overrides: Partial<Omit<AppError, 'kind'>> = {},
): AppError {
  return {
    kind,
    message: overrides.message ?? DEFAULT_MESSAGE[kind],
    code: overrides.code ?? null,
    status: overrides.status ?? null,
    validationErrors: overrides.validationErrors ?? [],
    cause: overrides.cause ?? null,
    details: overrides.details ?? null,
  }
}

/** 이 객체가 우리가 만든 AppError 인지 판별한다. */
export function isAppError(value: unknown): value is AppError {
  return (
    typeof value === 'object' &&
    value !== null &&
    'kind' in value &&
    'message' in value &&
    typeof (value as AppError).message === 'string'
  )
}

/**
 * 무엇이 오든 화면에 띄울 문구 하나로 줄인다.
 * catch 블록에서 타입을 좁히기 귀찮을 때 쓰는 마지막 수단.
 */
export function toErrorMessage(error: unknown): string {
  if (isAppError(error)) return error.message
  if (error instanceof Error) return error.message
  // 문자열은 그대로 쓴다. `ErrorMessage.error` 주석이 "문자열 무엇이든 받는다" 라고
  // 약속하고 세 페이지가 그렇게 쓰고 있는데, 여기서 버리면 호출부가 적은 문구가
  // 조용히 "알 수 없는 오류" 로 바뀐다 — 원인을 아는 상황에서 모른다고 말하는 셈이다.
  if (typeof error === 'string' && error.trim() !== '') return error
  return DEFAULT_MESSAGE.UNKNOWN
}
