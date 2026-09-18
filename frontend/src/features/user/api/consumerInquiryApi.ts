// 소비자 1:1 문의 API.
//
// ⚠️ 경로가 `/consumers/me/inquiries` 다 — 사용자를 경로 변수로 받지 않는다.
// 서버가 JWT 에서 얻어 남의 문의를 가리키는 요청 자체가 만들어지지 않는다.

import { apiClient } from '../../../services/apiClient.ts'
import type {
  ConsumerInquiry,
  ConsumerInquiryCreateRequest,
} from '../types/consumerInquiry.types.ts'

const PATH = '/v1/consumers/me/inquiries'

/** 내 문의 목록 — `GET /v1/consumers/me/inquiries`. 최신순으로 온다. */
export async function getConsumerInquiries(): Promise<ConsumerInquiry[]> {
  const { data } = await apiClient.get<ConsumerInquiry[]>(PATH)
  return data
}

/**
 * 문의 등록 — `POST /v1/consumers/me/inquiries`
 *
 * `type`이 `ETC`가 아닌데 `productOrderId`가 없으면 `422 INQUIRY_TARGET_REQUIRED`,
 * 남의 주문을 가리키면 `404 INQUIRY_ORDER_NOT_FOUND`로 거절된다.
 */
export async function createConsumerInquiry(
  request: ConsumerInquiryCreateRequest,
): Promise<ConsumerInquiry> {
  const { data } = await apiClient.post<ConsumerInquiry>(PATH, request)
  return data
}
