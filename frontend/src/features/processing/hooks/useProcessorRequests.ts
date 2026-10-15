import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { processingRequestApi, type ProcessingRequestListFilter } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'

const PAGE_SIZE = 20

/**
 * 가공사에게 온 요청.
 *
 * **더 이상 화면 하나를 채우지 않는다.** 견적 단계가 사라져 결제 전 요청에는 가공사가 할
 * 일이 없고, 이 조회가 남은 이유는 작업 줄에 브랜드·소재·공정 이름을 적기 위해서다.
 * 그래서 쪽 넘김도 상태 필터도 두지 않는다 — 최근 한 쪽이면 지금 진행 중인 작업을 덮는다.
 */
export function useProcessorRequests() {
  const filters = useMemo<ProcessingRequestListFilter>(() => ({ page: 0, size: PAGE_SIZE }), [])

  return useQuery({
    queryKey: processingKeys.list({ requests: filters }),
    queryFn: ({ signal }) => processingRequestApi.list(filters, signal),
  })
}
