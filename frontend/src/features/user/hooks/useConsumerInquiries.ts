import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getConsumerInquiries } from '../api/consumerInquiryApi.ts'
import { consumerInquiryKeys } from '../api/consumerInquiryKeys.ts'
import type { ConsumerInquiry } from '../types/consumerInquiry.types.ts'

/**
 * 내 문의 목록.
 *
 * 페이징이 없다 — 배송지·찜과 같은 개인 소량 리소스로 본다. 등록에 성공하면
 * {@link useCreateConsumerInquiry}가 이 쿼리를 무효화해 방금 등록한 문의가 바로 보인다.
 */
export function useConsumerInquiries() {
  return useQuery<ConsumerInquiry[], AppError>({
    queryKey: consumerInquiryKeys.lists(),
    queryFn: getConsumerInquiries,
  })
}
