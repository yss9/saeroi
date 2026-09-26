import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { createConsumerInquiry } from '../api/consumerInquiryApi.ts'
import { consumerInquiryKeys } from '../api/consumerInquiryKeys.ts'
import type { ConsumerInquiry, ConsumerInquiryCreateRequest } from '../types/consumerInquiry.types.ts'

/**
 * 문의 등록.
 *
 * 성공하면 목록을 다시 읽는다 — 지금까지 겪던 증상(등록해도 문의내역에 안 보임)이
 * 서버 저장이 없어서였으므로, 저장된 뒤에는 캐시에 직접 끼워 넣지 않고 서버가 준
 * 최신순 목록을 다시 받는 편이 안전하다.
 */
export function useCreateConsumerInquiry() {
  const queryClient = useQueryClient()

  return useMutation<ConsumerInquiry, AppError, ConsumerInquiryCreateRequest>({
    mutationFn: createConsumerInquiry,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: consumerInquiryKeys.lists() })
    },
  })
}
