import { currentTrade, useAgentTrades } from '../hooks/useAgentTrades.ts'
import type { Trade } from '../../trade/types/trade.types.ts'

/**
 * 지금 따라가는 거래가 어디까지 왔는지.
 *
 * <p><b>대화 위에 항상 떠 있다.</b> 담당자가 스크롤을 올려 예전 답변을 뒤지지 않고도
 * "물건이 어디쯤인지" 를 알 수 있어야 한다. 그것이 이 창에서 가장 자주 하는 질문이다.
 *
 * <p>끝난 단계는 초록 체크, 아직인 단계는 회색 체크다. 남은 단계를 아예 감추지 않는 이유는,
 * 앞으로 무엇이 남았는지가 <b>다음에 무엇을 해야 하는지</b>를 알려주기 때문이다.
 *
 * <p>단계는 서버가 준 시각으로 판정한다. 상태값 하나로 정하지 않는 이유는 온체인 기록이
 * 상태에 나타나지 않기 때문이다 — {@code PAID} 인 거래는 체인 기록 전일 수도, 후일 수도
 * 있고 그 둘은 담당자가 할 일이 다르다.
 */
export function TradeProgressBar({ startedAt }: { startedAt: string | null }) {
  const { data } = useAgentTrades()
  const trade = currentTrade(data?.content ?? [], startedAt)
  if (!trade) return null

  const steps = stepsOf(trade)
  const activeIndex = steps.findIndex((step) => !step.done)

  return (
    <nav className="agent-progress" aria-label="거래 진행 상황">
      <p className="agent-progress__subject">
        <span className="agent-progress__name">{trade.materialName}</span>
        <span className="agent-progress__amount">
          {(trade.quantity / 1000).toLocaleString()} kg · {trade.totalPrice.toLocaleString()}원
        </span>
      </p>

      <ol className="agent-progress__steps">
        {steps.map((step, index) => (
          <li
            key={step.label}
            className={[
              'agent-progress__step',
              step.done ? 'is-done' : 'is-todo',
              index === activeIndex ? 'is-current' : '',
            ]
              .join(' ')
              .trim()}
          >
            <span className="agent-progress__check" aria-hidden="true">
              ✓
            </span>
            <span className="agent-progress__label">{step.label}</span>
          </li>
        ))}
      </ol>
    </nav>
  )
}

/**
 * 취소된 거래는 진행이 아니라 끝이다. 다섯 단계를 절반쯤 체크해 두면 아직 진행 중으로
 * 읽히므로 한 줄로 바꿔 말한다.
 */
function stepsOf(trade: Trade): Array<{ label: string; done: boolean }> {
  if (trade.status === 'CANCELED') {
    return [{ label: '취소됨', done: true }]
  }
  return [
    { label: '주문', done: true },
    { label: '결제', done: trade.paidAt != null },
    // 온체인 기록은 선택이다. 판매 글이 체인에 없으면 이 단계는 영영 채워지지 않는다.
    { label: '온체인 기록', done: trade.chainTradeId != null },
    { label: '발송', done: trade.shippedAt != null },
    { label: '수령', done: trade.receivedAt != null },
  ]
}
