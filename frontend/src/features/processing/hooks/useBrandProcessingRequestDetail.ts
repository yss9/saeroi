import { useQuery } from '@tanstack/react-query'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { processingJobApi } from '../api/processingJobApi.ts'

/**
 * 요청 하나와 그 작업.
 *
 * **견적 조회가 없어졌다.** 요청이 만들어지는 순간 작업과 금액이 함께 정해지므로
 * (`POST /v1/processing-requests` 가 `jobId`·`fee` 를 돌려준다) 화면이 볼 것은 요청과
 * 작업 둘뿐이다.
 *
 * 작업은 내 작업 목록에서 골라 낸다 — 요청 하나에 작업 하나라 골라내기가 그대로 답이고,
 * 다른 화면이 이미 쓰는 조회라 대개 새 요청이 나가지 않는다.
 */
export function useBrandProcessingRequestDetail(requestId: number) {
  const detail = useQuery({
    queryKey: processingKeys.detail(requestId),
    queryFn: ({ signal }) => processingRequestApi.detail(requestId, signal),
  })
  const jobs = useQuery({ queryKey: processingKeys.list({ resource: 'jobs' }), queryFn: processingJobApi.list })
  const job = jobs.data?.find(item => item.requestId === requestId)

  return { detail, jobs, job }
}
