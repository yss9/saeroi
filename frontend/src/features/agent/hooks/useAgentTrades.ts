import { useQuery } from '@tanstack/react-query'
import { getTrades } from '../../trade/api/getTrades.ts'
import type { Trade } from '../../trade/types/trade.types.ts'

/**
 * 대화창이 보는 거래 목록.
 *
 * <p>오른쪽 패널의 '마친 절차' 와 '결제한 금액' 이 쓴다. 할 일 판정은 여기서 하지 않는다 —
 * 서버가 한 번 판정한 것을 진행 조회로 받는다({@code useJourneyProgress}).
 *
 * <p>대화를 걸지 않아도 상태가 갱신된다 — 도메인 API 만 읽으므로 크레딧이 들지 않는다.
 */
const REFRESH_MS = 20_000

export function useAgentTrades() {
  return useQuery({
    queryKey: ['agent', 'trades'],
    // 이 대화의 거래를 번호로 고르므로 넉넉히 받는다. 최근 10건만 받으면 그 사이 홈페이지에서
    // 산 거래가 많을 때 이 대화의 거래가 밀려난다. 서버 상한이 100이다.
    queryFn: () => getTrades({ side: 'BUY', page: 0, size: 100 }),
    refetchInterval: REFRESH_MS,
    // 창을 보고 있지 않을 때는 조회하지 않는다. 대화창을 열어둔 채 다른 일을 하는 경우가
    // 많아, 이게 없으면 종일 서버를 두드린다.
    refetchIntervalInBackground: false,
  })
}

/** 결제가 승인된 거래의 금액 합계. 아직 결제 전인 건은 세지 않는다. */
export function paidTotal(trades: Trade[]): number {
  return trades
    .filter((trade) => trade.status !== 'CANCELED')
    .reduce((sum, trade) => sum + (trade.paymentAmount || 0), 0)
}
