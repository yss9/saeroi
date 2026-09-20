import { paidTotal, pendingStepOf, sinceStart, useAgentTrades } from '../hooks/useAgentTrades.ts'
import type { ConversationDetail } from '../types/agent.types.ts'
import type { Trade } from '../../trade/types/trade.types.ts'

/** 상대를 기다리는 구간. 담당자가 할 수 있는 일이 없다는 것을 분명히 말해 준다. */
const WAITING_LABEL: Record<string, string> = {
  PAID: '판매자 발송 대기',
  SHIPPED: '배송 중',
}

/**
 * 오른쪽 절차 패널.
 *
 * <h2>맨 위는 담당자가 눌러야 할 것</h2>
 *
 * <p>이 창에서 담당자만 할 수 있는 일은 결제와 서명뿐이다. 그 두 가지가 대화 안에 묻히면
 * 언제 무엇을 눌러야 하는지 알 수 없어, 결국 거래 목록 화면을 따로 열게 된다 — 그 이동을
 * 없애는 것이 이 기능의 요점이다.
 *
 * <p><b>할 일이 없으면 그 칸을 통째로 감춘다.</b> "없음" 이라고 적힌 빈 줄은 정보가 아니라
 * 소음이고, 매번 읽고 넘겨야 하는 것이 늘어난다.
 *
 * <h2>대화가 만든 카드는 여기서 누르지 않는다</h2>
 *
 * <p>결제·서명 카드는 대화 흐름 안에 인라인으로 남는다 — 무엇에 동의하는지는 그 앞뒤 맥락과
 * 함께 읽어야 하기 때문이다. 여기서는 "왼쪽에 카드가 떠 있다" 는 사실만 알린다.
 */
export function AgentTaskPanel({ conversation }: { conversation: ConversationDetail | null }) {
  const { data } = useAgentTrades()
  const trades = (data?.content ?? []).filter((trade) => trade.status !== 'CANCELED')

  const openCards = conversation?.actions.filter((action) => action.status === 'PENDING') ?? []
  const actionable = trades.filter((trade) => pendingStepOf(trade) != null)
  const waiting = trades.filter(
    (trade) => pendingStepOf(trade) == null && WAITING_LABEL[trade.status] != null,
  )
  // 이력은 **이번 대화에서 만든 거래만** 센다. 계정의 지난 거래까지 합치면 새 대화를
  // 시작한 첫 화면에 지난달 지출이 떠 있게 된다 — 지금 일인지 지난 일인지 구분되지 않는다.
  //
  // 반대로 '해야 할 일'과 '기다리는 중'은 전체를 본다. 지난 대화에서 산 소재가 아직 오는
  // 중이라면 그건 여전히 담당자의 일이고, 대화를 끝냈다고 물건이 멈추지는 않는다.
  const startedAt = conversation?.messages[0]?.createdAt ?? null
  const journey = sinceStart(trades, startedAt)
  const spent = paidTotal(journey)

  return (
    <aside className="agent-tasks" aria-label="진행 절차">
      {openCards.length > 0 && (
        <section className="agent-tasks__block agent-tasks__block--alert">
          <h2>확인 대기</h2>
          <p className="agent-tasks__hint">
            왼쪽 대화에 {openCards.length}건의 카드가 떠 있습니다.
          </p>
          <ul>
            {openCards.map((action) => (
              <li key={action.id}>{cardLabel(action.actionType)}</li>
            ))}
          </ul>
        </section>
      )}

      {actionable.length > 0 && (
        <section className="agent-tasks__block agent-tasks__block--alert">
          <h2>해야 할 일</h2>
          <ul>
            {actionable.map((trade) => (
              <li key={trade.tradeId}>
                <span className="agent-tasks__name">{trade.materialName}</span>
                <span className="agent-tasks__badge is-todo">{pendingStepOf(trade)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {waiting.length > 0 && (
        <section className="agent-tasks__block">
          <h2>기다리는 중</h2>
          <ul>
            {waiting.map((trade) => (
              <li key={trade.tradeId}>
                <span className="agent-tasks__name">{trade.materialName}</span>
                <span className="agent-tasks__badge">{WAITING_LABEL[trade.status]}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {journey.length > 0 && (
        <section className="agent-tasks__block">
          <h2>마친 절차</h2>
          <ul className="agent-tasks__done">
            {doneLines(journey).map((line) => (
              <li key={line}>
                <span className="agent-tasks__check" aria-hidden="true">
                  ✓
                </span>
                {line}
              </li>
            ))}
          </ul>
        </section>
      )}

      {spent > 0 && (
        <section className="agent-tasks__block agent-tasks__block--spend">
          <h2>결제한 금액</h2>
          <p className="agent-tasks__total">{spent.toLocaleString()}원</p>
          {/* 합계만 두면 "무엇에 썼는지" 를 확인하러 다른 화면을 열게 된다. */}
          <ul>
            {journey
              .filter((trade) => trade.paymentAmount > 0)
              .map((trade) => (
                <li key={trade.tradeId}>
                  <span className="agent-tasks__name">{trade.materialName}</span>
                  <span>{trade.paymentAmount.toLocaleString()}원</span>
                </li>
              ))}
          </ul>
        </section>
      )}
    </aside>
  )
}

function cardLabel(actionType: string): string {
  if (actionType === 'PAYMENT') return '결제 승인이 필요합니다'
  if (actionType === 'WALLET_SIGN') return '지갑 서명이 필요합니다'
  return '확인이 필요합니다'
}

/**
 * 끝난 절차를 한 줄씩. 거래별로 나열하면 같은 문장이 반복되므로 <b>건수로 묶는다</b>.
 * 여기서 확인하려는 것은 "몇 건이 어디까지 갔나" 이지 개별 거래가 아니다.
 */
function doneLines(trades: Trade[]): string[] {
  const lines: string[] = []
  const paid = trades.filter((trade) => trade.paidAt != null).length
  const onChain = trades.filter((trade) => trade.chainTradeId != null).length
  const received = trades.filter((trade) => trade.receivedAt != null).length

  if (paid > 0) lines.push(`결제 완료 ${paid}건`)
  if (onChain > 0) lines.push(`온체인 기록 ${onChain}건`)
  if (received > 0) lines.push(`수령 완료 ${received}건`)
  return lines
}
