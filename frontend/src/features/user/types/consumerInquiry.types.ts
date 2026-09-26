/**
 * 소비자 1:1 문의.
 *
 * Backend 원본: `inquiry/dto/ConsumerInquiryResponse.java`.
 *
 * **`answer`는 항상 `null`이다(2026-09-18 시점).** 이 프로젝트에 관리자/CS 도메인이 아직
 * 없어 답변을 등록할 API가 없다 — `answered`도 늘 `false`다.
 */
export type InquiryType = 'DELIVERY' | 'EXCHANGE_RETURN' | 'PRODUCT' | 'ETC'

export interface ConsumerInquiry {
  inquiryId: number
  type: InquiryType
  /** 문의 대상 주문. `ETC` 유형은 null일 수 있다 */
  productOrderId: number | null
  /** 등록 시점 상품명 스냅샷. 주문이 없으면(ETC) null */
  targetLabel: string | null
  content: string
  answered: boolean
  answer: string | null
  answeredAt: string | null
  createdAt: string
}

/**
 * 문의 등록 요청.
 *
 * ⚠️ **`productOrderId`는 `type`이 `ETC`가 아니면 필수다.** 서버가 `422
 * INQUIRY_TARGET_REQUIRED`로 거절한다 — 화면은 제출 전에 먼저 막는다.
 */
export interface ConsumerInquiryCreateRequest {
  type: InquiryType
  productOrderId?: number
  content: string
}
