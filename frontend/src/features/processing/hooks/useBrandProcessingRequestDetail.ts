import { useQuery } from '@tanstack/react-query'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { processingJobApi } from '../api/processingJobApi.ts'

/**
 * 요청 상세가 읽는 것.
 *
 * **견적 목록은 더 부르지 않는다.** 백엔드가 견적 단계를 들어내면서
 * `GET /processing-requests/{id}/quotes` 가 사라졌는데, 화면이 그 호출을 그대로 하고
 * 있으면 404 하나가 상세 전체를 에러로 덮는다. 그 아래에 있던 결제 진입점까지 같이
 * 가려져서, 브랜드는 결제할 방법이 없어진다 — 배포에서 실제로 났던 일이다.
 *
 * 금액과 결제 기한은 이제 요청이 만든 작업(`processing_job`)에 적혀 있으므로
 * 요청과 작업, 둘만 읽으면 된다.
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
