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

/**
 * 지금 화면이 따라가야 할 거래.
 *
 * <p>끝나지 않은 것 중 <b>가장 최근 것</b>이다. 여러 건을 동시에 진행할 수 있지만 상단
 * 막대는 하나만 그린다 — 다섯 단계짜리 막대를 여러 줄 쌓으면 그것만으로 화면이 찬다.
 * 나머지는 오른쪽 목록이 맡는다.
 *
 * <p>진행 중인 것이 없으면 <b>이번 대화에서 만든 거래</b> 중 최신을 보여준다. 방금 수령을
 * 확정했는데 막대가 사라지면 담당자는 자기가 한 일이 반영됐는지 확인할 곳을 잃는다.
 *
 * <p>그렇다고 지난 대화의 거래까지 끌어오지는 않는다. 새 대화를 시작한 사람에게 지난번
 * 거래가 전부 초록 체크로 떠 있으면 무엇이 지금 일인지 알 수 없다 — 이 창은 "지금 무엇을
 * 하고 있는가" 를 보여주는 곳이다.
 */
export function currentTrade(trades: Trade[], startedAt: string | null): Trade | null {
  const open = trades.filter(
    (trade) => trade.status !== 'COMPLETED' && trade.status !== 'CANCELED',
  )
  if (open.length > 0) return open[0]
  return sinceStart(trades, startedAt)[0] ?? null
}

/**
 * 이번 대화가 시작된 뒤에 만들어진 거래.
 *
 * <p>대화와 거래를 잇는 값이 서버에 없어 <b>시각으로 자른다.</b> 대화는 항상 담당자의 첫
 * 발화로 시작하고 거래는 그 뒤에만 만들어질 수 있으므로, 이 기준이면 이번 여정에서 만든
 * 것만 남는다. 정확한 연결이 필요해지면 그때 서버에 대화 id 를 붙이면 된다.
 */
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
