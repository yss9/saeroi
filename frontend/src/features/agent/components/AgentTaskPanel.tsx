import { paidTotal, useAgentTrades } from '../hooks/useAgentTrades.ts'
import { useJourneyProgress } from '../hooks/useJourneyProgress.ts'
import type { ReactNode } from 'react'
import type { ConversationDetail, JourneySpend, JourneyTask, PendingAction } from '../types/agent.types.ts'
import type { Trade } from '../../trade/types/trade.types.ts'

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
 * <h2>할 일은 서버가 판정한다</h2>
 *
 * <p>'해야 할 일' 과 '기다리는 중' 은 진행 막대와 같은 조회의 `tasks` 를 그대로 그린다. 예전에는
 * 여기서 거래 상태를 보고 스스로 판정했는데(`pendingStepOf`), 그러면 막대·패널·대화가 한 화면에서
 * 서로 다른 말을 했고, 거래만 볼 줄 알아 가공·생산·판매의 할 일은 아예 보이지 않았다.
 *
 * <h2>대화가 만든 카드는 여기서 누르지 않는다</h2>
 *
 * <p>결제·서명 카드는 대화 흐름 안에 인라인으로 남는다 — 무엇에 동의하는지는 그 앞뒤 맥락과
 * 함께 읽어야 하기 때문이다. 여기서는 "왼쪽에 카드가 떠 있다" 는 사실만 알린다.
 */
export function AgentTaskPanel({
  conversation,
  pinnedCards = [],
  renderCard,
}: {
  conversation: ConversationDetail | null
  /** 여기에 고정할 카드. 넓은 화면에서만 온다 — 좁으면 대화 안에 남는다 */
  pinnedCards?: PendingAction[]
  /** 카드를 그리는 함수. 서명 방법 표가 대화 쪽에 있어서 그리기만 넘겨받는다 */
  renderCard?: (action: PendingAction) => ReactNode
}) {
  const { data } = useAgentTrades()
  const { data: progress } = useJourneyProgress(conversation?.id ?? null)
  // **이 대화에서 산 거래만 본다.** 대화 하나는 제품 하나다. 계정의 모든 거래를 보이면 에코백을
  // 만드는 대화에 홈페이지에서 산 다른 소재의 결제·수령이 섞인다. 그 거래도 담당자가 물으면
  // 대화가 처리한다 — 먼저 꺼내지 않을 뿐이다.
  const projectTradeIds = new Set(conversation?.projectTradeIds ?? [])
  const trades = (data?.content ?? []).filter(
    (trade) => trade.status !== 'CANCELED' && projectTradeIds.has(trade.tradeId),
  )

  const openCards = conversation?.actions.filter((action) => action.status === 'PENDING') ?? []
  // 여기 고정된 카드는 여기서 그대로 보이므로 따로 알릴 필요가 없다. 나머지만 알린다.
  //
  // **그릴 방법이 없으면 고정하지 않은 것으로 본다.** 그러지 않으면 카드가 대화에서도
  // 빠지고 여기서도 안 그려져 어디에도 보이지 않는다.
  const pinnedIds = new Set(renderCard ? pinnedCards.map((action) => action.id) : [])
  const inlineCards = openCards.filter((action) => !pinnedIds.has(action.id))
  // 서버가 이 대화의 프로젝트에서 판정한 남은 일. 모델이 읽는 목록과 같다.
  const tasks = progress?.tasks ?? []
  const actionable = tasks.filter((task) => task.actionable)
  const waiting = tasks.filter((task) => !task.actionable)
  const journey = trades
  // **결제한 금액은 서버가 모은다** — 소재 구매와 가공비. 예전 서버라 값이 없으면 거래만으로 그린다.
  const spending: JourneySpend[] = progress?.spending ?? journey
    .filter((trade) => trade.paymentAmount > 0)
    .map((trade) => ({ kind: 'MATERIAL', label: trade.materialName, detail: null, amount: trade.paymentAmount }))
  const spendTotal = spending.length > 0 ? spending.reduce((sum, line) => sum + line.amount, 0) : paidTotal(journey)

  return (
    <aside className="agent-tasks" aria-label="진행 절차">
      {/*
        **카드를 여기에 고정한다.** 대화 안에 두면 모델이 계속 말하면서 카드가 위로 밀려
        담당자가 도로 스크롤해야 했다 — 소재를 세 건 살 때 결제 카드 세 장이 그렇게 묻혔다.
        이 패널은 따로 스크롤되므로 대화가 흘러도 그대로 남는다.

        좁은 화면에서는 이 패널이 대화 아래로 내려가므로 `pinnedCards` 가 비어서 오고,
        카드는 대화 안에 그대로 남는다.
      */}
      {pinnedCards.length > 0 && renderCard && (
        <section className="agent-tasks__block agent-tasks__block--alert">
          <h2>확인 대기 {pinnedCards.length}건</h2>
          <div className="agent-tasks__cards">
            {pinnedCards.map((action) => (
              <div key={action.id}>{renderCard(action)}</div>
            ))}
          </div>
        </section>
      )}

      {/* 대화 안에 남아 있는 카드(좁은 화면)는 여기서 알리기만 한다. */}
      {inlineCards.length > 0 && (
        <section className="agent-tasks__block agent-tasks__block--alert">
          <h2>확인 대기</h2>
          <p className="agent-tasks__hint">
            왼쪽 대화에 {inlineCards.length}건의 카드가 떠 있습니다.
          </p>
          <ul>
            {inlineCards.map((action) => (
              <li key={action.id}>{cardLabel(action.actionType)}</li>
            ))}
          </ul>
        </section>
      )}

      {actionable.length > 0 && (
        <section className="agent-tasks__block agent-tasks__block--alert">
          <h2>해야 할 일</h2>
          <ul>
            {actionable.map((task) => (
              <TaskLine key={taskKey(task)} task={task} />
            ))}
          </ul>
        </section>
      )}

      {waiting.length > 0 && (
        <section className="agent-tasks__block">
          <h2>기다리는 중</h2>
          <ul>
            {waiting.map((task) => (
              <TaskLine key={taskKey(task)} task={task} />
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

      {spendTotal > 0 && (
        <section className="agent-tasks__block agent-tasks__block--spend">
          <h2>결제한 금액</h2>
          <p className="agent-tasks__total">{spendTotal.toLocaleString()}원</p>
          {/* 합계만 두면 "무엇에 썼는지" 를 확인하러 다른 화면을 열게 된다. 소재와 가공을 나눠 소계를 보인다. */}
          {SPEND_GROUPS.map(({ kind, title }) => {
            const lines = spending.filter((line) => line.kind === kind)
            if (lines.length === 0) return null
            const subtotal = lines.reduce((sum, line) => sum + line.amount, 0)
            return (
              <div key={kind} className="agent-tasks__spend-group">
                <p className="agent-tasks__spend-title">
                  <span>{title}</span>
                  <span className="agent-tasks__amount">{subtotal.toLocaleString()}원</span>
                </p>
                <ul>
                  {lines.map((line, index) => (
                    <li key={`${kind}-${index}`}>
                      <span className="agent-tasks__name">
                        {line.label}
                        {line.detail && <small className="agent-tasks__detail">{line.detail}</small>}
                      </span>
                      <span className="agent-tasks__amount">{line.amount.toLocaleString()}원</span>
                    </li>
                  ))}
                </ul>
              </div>
            )
          })}
        </section>
      )}
    </aside>
  )
}

/**
 * 남은 일 한 줄. 무엇에 대한 일인지를 위에, 할 일 문장을 아래에 둔다 — 문장이 한 줄 배지에
 * 들어가지 않고, 배지에 욱여넣으면 이름이 밀려 무엇의 일인지 안 보인다.
 */
function TaskLine({ task }: { task: JourneyTask }) {
  return (
    <li className="agent-tasks__task">
      <span className="agent-tasks__name">
        {DOMAIN_LABEL[task.domain] ?? ''} {task.subject}
      </span>
      <span className={task.actionable ? 'agent-tasks__step is-todo' : 'agent-tasks__step'}>
        {task.label}
      </span>
    </li>
  )
}

/** 배치 하나에 할 일이 둘일 수 있어(체인 기록, 제품 연결) 문장까지 키에 넣는다. */
const SPEND_GROUPS: { kind: JourneySpend['kind']; title: string }[] = [
  { kind: 'MATERIAL', title: '소재 구매' },
  { kind: 'PROCESSING', title: '가공비' },
]

function taskKey(task: JourneyTask): string {
  return `${task.domain}-${task.referenceId}-${task.label}`
}

const DOMAIN_LABEL: Record<string, string> = {
  TRADE: '소재',
  PROCESSING: '가공',
  PRODUCTION: '생산',
  PRODUCT: '제품',
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
