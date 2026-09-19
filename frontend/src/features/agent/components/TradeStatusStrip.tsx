import { useQuery } from '@tanstack/react-query'
import { getTrades } from '../../trade/api/getTrades.ts'

/**
 * 대화창 안의 진행 현황 표시줄.
 *
 * <h2>왜 필요한가</h2>
 *
 * 여정에는 상대방(판매자·가공업체)이 움직여야 넘어가는 구간이 있다. 그 사이 담당자는
 * 대화창을 보고 있는데, 대화는 자기가 말을 걸 때만 갱신된다 — 판매자가 방금 발송해도
 * 화면에는 아무 변화가 없다.
 *
 * <h2>왜 LLM 을 쓰지 않는가</h2>
 *
 * 주기적으로 모델을 부르면 <b>가만히 있어도 크레딧이 탄다.</b> 크레딧이 토큰당 차감되는
 * 환경에서 폴링에 LLM 을 끼우는 것은 가장 나쁜 선택이다.
 *
 * 그래서 여기서는 <b>도메인 API 만</b> 주기적으로 읽는다. 상태가 바뀐 것을 담당자가 눈으로
 * 보고, 설명이 필요하면 그때 대화로 물어본다. 그 순간에만 모델이 돈다.
 */
const REFRESH_MS = 20_000

const STATUS_LABEL: Record<string, string> = {
  CREATED: '결제 대기',
  PAID: '결제 완료 · 발송 대기',
  SHIPPED: '배송 중',
  RECEIVED: '수령 완료',
  CANCELED: '취소됨',
}

export function TradeStatusStrip() {
  const { data } = useQuery({
    queryKey: ['agent', 'trade-status'],
    queryFn: () => getTrades({ side: 'BUY', page: 0, size: 3 }),
    refetchInterval: REFRESH_MS,
    // 창을 보고 있지 않을 때는 조회하지 않는다. 대화창을 열어둔 채 다른 일을 하는 경우가
    // 많아, 이게 없으면 종일 서버를 두드린다.
    refetchIntervalInBackground: false,
  })

  const trades = (data?.content ?? []).filter(
    (trade) => trade.status !== 'RECEIVED' && trade.status !== 'CANCELED',
  )

  if (trades.length === 0) return null

  return (
    <aside className="agent-status" aria-label="진행 중인 거래">
      {trades.map((trade) => (
        <div key={trade.tradeId} className="agent-status__row">
          <span className="agent-status__name">{trade.materialName}</span>
          <span className={`agent-status__badge is-${trade.status?.toLowerCase()}`}>
            {STATUS_LABEL[trade.status ?? ''] ?? trade.status}
          </span>
        </div>
      ))}
    </aside>
  )
}
