import { useQueries, useQuery } from '@tanstack/react-query'
import { processingJobApi } from '../api/processingJobApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import type { ProcessingJobStatus, ProcessingRequestStatus } from '../types/processing.types.ts'

/**
 * 요청 현황 네 숫자.
 *
 * **목록 필터와 따로 센다.** 예전에는 목록이 받아 온 응답에서 숫자를 꺼내 썼다. 그래서
 * "견적 대기" 를 누르면 목록이 걸러지면서 위의 "전체 요청" 까지 그 개수로 바뀌었다 —
 * 전체가 0 으로 떨어지는 것처럼 보였다. 위의 숫자는 아래에서 무엇을 누르든 그대로여야
 * 한다. 그게 현황이다.
 *
 * **쪽수와도 따로 센다.** 한 페이지만 세면 2쪽부터의 건이 빠져 "현재 페이지" 라는 단서를
 * 달아야 했고, 그 단서는 숫자를 읽는 사람에게 아무 도움이 되지 않았다. 상태별로 따로
 * 물어 서버가 세어 준 `total` 을 그대로 쓴다.
 *
 * `size: 1` 로 부른다 — 필요한 것은 목록이 아니라 total 하나뿐이다.
 */
const COUNTED: readonly (ProcessingRequestStatus | undefined)[] = [
  'QUOTING',
  'SELECTED',
]

/**
 * 가공이 끝나 브랜드가 받을 차례인 작업. 결과 제출부터 수령 전까지다.
 *
 * 둘을 한 칸으로 묶는 이유 — 브랜드가 하는 일이 같다. 결과물이 오기를 기다리거나,
 * 와서 수령을 확인하거나. 둘 다 "가공은 끝났다" 이다.
 */
const DONE_STATUSES: readonly ProcessingJobStatus[] = ['RESULT_SUBMITTED', 'RETURN_SHIPPED']

export interface BrandProcessingRequestSummary {
  quoting: number
  /** 견적을 수락하고 아직 가공이 끝나지 않은 건. */
  selected: number
  /** 가공이 끝나 수령을 기다리는 건. */
  processed: number
  received: number
  /** 넷 중 하나라도 아직 오지 않았는가. 0 을 사실인 것처럼 보여주지 않기 위해 쓴다. */
  isPending: boolean
}

export function useBrandProcessingRequestSummary(): BrandProcessingRequestSummary {
  const results = useQueries({
    queries: COUNTED.map((status) => ({
      queryKey: processingKeys.list({ brandRequestCount: status ?? 'ALL' }),
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        processingRequestApi.list({ page: 0, size: 1, ...(status ? { status } : {}) }, signal),
    })),
  })

  /*
    **가공 완료·수령 완료는 요청이 모른다.** 요청은 견적을 수락한 순간 `SELECTED` 에서
    멈추고 그 뒤는 작업(`processing_job`)에 적히므로, 서버에 상태로 물을 수가 없다 —
    내 작업 목록을 받아 여기서 센다. 작업은 요청 하나당 하나뿐이라 겹쳐 세지 않는다.
  */
  const jobs = useQuery({
    queryKey: processingKeys.list({ resource: 'jobs' }),
    queryFn: processingJobApi.list,
  })

  const [quoting, selected] = results
  const all = jobs.data ?? []
  const processed = all.filter((job) => DONE_STATUSES.includes(job.status)).length
  const received = all.filter((job) => job.status === 'CONFIRMED').length

  return {
    quoting: quoting.data?.total ?? 0,
    // 수락한 건에서 이미 끝난 것을 뺀다. 넷이 겹치면 합이 전체보다 커져 무엇을 세는
    // 숫자인지 알 수 없다.
    selected: Math.max((selected.data?.total ?? 0) - processed - received, 0),
    processed,
    received,
    isPending: results.some((result) => result.isPending) || jobs.isPending,
  }
}
