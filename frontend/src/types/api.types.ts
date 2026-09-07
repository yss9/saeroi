// Backend 공통 응답 규격.
//
// Spring 쪽 원본: backend/src/main/java/com/ssafy/upcycle/common/response/ApiResponse.java
// 모든 응답이 이 봉투(envelope)로 감싸여 오므로, apiClient 가 인터셉터에서 `data` 를
// 벗겨낸다. Feature API 함수는 벗겨진 알맹이 타입만 다루면 된다.

/** 성공·실패를 모두 포함하는 Backend 응답 봉투. */
export interface ApiEnvelope<T> {
  success: boolean
  /** 성공 시 'SUCCESS', 실패 시 ErrorCode 의 code (예: 'VALIDATION_ERROR') */
  code: string
  message: string
  data: T
  /** ISO-8601 문자열 (Java LocalDateTime 직렬화 결과) */
  timestamp: string
}

/** 검증 실패 시 `data` 로 내려오는 필드별 오류. */
export interface ValidationError {
  field: string
  message: string
}

/**
 * 목록 조회 응답.
 *
 * Spring 쪽 원본: `common` 이 아니라 `trade/dto/PageResponse.java` 다. Spring Data 의
 * `Page` 를 그대로 직렬화하지 않고 화면이 쓰는 여섯 가지만 고정한 것이라, 서버 구현이
 * 바뀌어도 이 모양은 유지된다.
 *
 * `page` 는 **0부터** 다. 서버 기준을 그대로 쓴다 — 한쪽에서 1을 더하면 다른 쪽에서
 * 빼야 하고, 그 규칙은 언젠가 반드시 한 군데서 빠진다.
 */
export interface PageResponse<T> {
  content: T[]
  /** 0-based */
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}
