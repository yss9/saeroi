import { useQuery } from '@tanstack/react-query'
import { getTrades } from '../../trade/api/getTrades.ts'
import type { Trade } from '../../trade/types/trade.types.ts'

/**
 * 대화창이 보는 거래 목록.
 *
 * <p>상단 진행 막대와 오른쪽 할 일 목록이 <b>같은 쿼리 키</b>를 쓴다. react-query 가
 * 중복 요청을 합쳐 주므로 둘이 따로 불러도 네트워크는 한 번이다. 각자 조회하게 두면
 * 20초마다 같은 응답을 두 번 받게 되고, 두 화면이 서로 다른 시점의 상태를 보여준다.
 *
 * <p>대화를 걸지 않아도 상태가 갱신된다 — 도메인 API 만 읽으므로 크레딧이 들지 않는다.
 */
const REFRESH_MS = 20_000

export function useAgentTrades() {
  return useQuery({
    queryKey: ['agent', 'trades'],
    queryFn: () => getTrades({ side: 'BUY', page: 0, size: 10 }),
    refetchInterval: REFRESH_MS,
    // 창을 보고 있지 않을 때는 조회하지 않는다. 대화창을 열어둔 채 다른 일을 하는 경우가
    // 많아, 이게 없으면 종일 서버를 두드린다.
    refetchIntervalInBackground: false,
  })
}

export function sinceStart(trades: Trade[], startedAt: string | null): Trade[] {
  if (!startedAt) return []
  const start = Date.parse(startedAt)
  if (Number.isNaN(start)) return []
  return trades.filter((trade) => Date.parse(trade.createdAt) >= start)
}

/** 이 거래에서 담당자가 다음에 해야 할 일. 없으면 상대를 기다리는 중이다. */
export function pendingStepOf(trade: Trade): string | null {
  if (trade.status === 'CREATED') return '결제'
  if (trade.status === 'PAID' && trade.chainTradeId == null) return '온체인 기록 서명'
  if (trade.status === 'SHIPPED') return '수령 확정'
  return null
}

/** 결제가 승인된 거래의 금액 합계. 아직 결제 전인 건은 세지 않는다. */
export function paidTotal(trades: Trade[]): number {
  return trades
    .filter((trade) => trade.status !== 'CANCELED')
    .reduce((sum, trade) => sum + (trade.paymentAmount || 0), 0)
}
