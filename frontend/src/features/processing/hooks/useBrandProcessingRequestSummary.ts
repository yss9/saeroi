import { useQueries } from '@tanstack/react-query'
import { processingKeys } from '../api/processingKeys.ts'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import type { ProcessingRequestStatus } from '../types/processing.types.ts'

/**
 * 요청 현황 세 숫자.
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
  undefined, // 전체
  'QUOTING',
  'SELECTED',
]

export interface BrandProcessingRequestSummary {
  total: number
  quoting: number
  selected: number
  /** 셋 중 하나라도 아직 오지 않았는가. 0 을 사실인 것처럼 보여주지 않기 위해 쓴다. */
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

  const [all, quoting, selected] = results

  return {
    total: all.data?.total ?? 0,
    quoting: quoting.data?.total ?? 0,
    selected: selected.data?.total ?? 0,
    isPending: results.some((result) => result.isPending),
  }
}
