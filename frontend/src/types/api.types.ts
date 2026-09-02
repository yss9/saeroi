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
 * TODO(backend 협의): 페이지네이션 규격이 아직 확정되지 않았다. Spring Data 의
 * Page 를 그대로 쓸지, 커서 기반으로 갈지 정해지면 이 타입을 맞춘다.
 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
