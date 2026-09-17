import { formatKg } from '../../../utils/quantity.ts'
import { formatTradeDateTime } from '../utils/tradeDateTime.ts'
import type { Trade, TradeStatus } from '../types/trade.types.ts'
import './trade.css'

/** 이 거래에서 보는 사람의 위치. 역할(Role)이 아니다 — 같은 사용자가 거래마다 다르다. */
export type TradeViewer = 'BUYER' | 'SELLER'

export interface TradeTimelineProps {
  trade: Trade
  /** 당사자가 아니면 넘기지 않는다. "다음 할 일" 안내를 생략한다 */
  viewer?: TradeViewer
}

/** 단계의 진행 상태. */
type StepState = 'DONE' | 'CURRENT' | 'PENDING' | 'STOPPED'

interface Step {
  key: string
  label: string
  /** 이 단계가 끝난 시각. 아직이면 null */
  at: string | null
  state: StepState
  /** 시각 아래 한 줄. 송장처럼 그 단계에서만 의미 있는 값 */
  detail?: string | null
  /** 지금 이 단계를 진행해야 하는 사람 */
  actor: TradeViewer
}

/**
 * 거래 진행 타임라인.
 *
 * 상태 하나(`status`)만 보여주면 "결제 완료" 라는 사실은 알 수 있어도 **다음에 무엇을
 * 기다리는지, 언제 거기까지 왔는지**를 알 수 없다. 그래서 네 단계와 각 시각을 함께 놓는다.
 *
 *   구매 요청 → 결제 완료 → 발송 → 수령 확인(거래 완료)
 *
 * 서버의 `TradeStatus` 는 다섯 개(`RECEIVED`, `COMPLETED` 가 따로)지만 단계는 네 개다.
 * 수령 확인 요청 하나가 한 트랜잭션에서 `RECEIVED` 를 거쳐 `COMPLETED` 까지 끝내므로
 * `RECEIVED` 로 멈춘 거래는 사용자가 볼 수 없다 — 도달하지 않는 단계를 그려 두면
 * "여기서 또 뭘 해야 하나" 로 읽힌다.
 *
 * **판매자와 구매자가 같은 타임라인을 본다.** 진행된 사실은 양쪽에 같고, 다른 것은
 * "지금 누구 차례인가" 뿐이라 그것만 `viewer` 로 갈라 말한다.
 *
 * 추가 조회가 없다. 필요한 시각이 모두 `Trade` 에 있다 — 이력을 따로 부르면 상세 화면이
 * 요청 두 개를 기다리게 되고, 두 응답의 시점이 어긋나면 상태와 시각이 맞지 않는다.
 */
export function TradeTimeline({ trade, viewer }: TradeTimelineProps) {
  const steps = buildSteps(trade)
  const current = steps.find((step) => step.state === 'CURRENT')
  const isCanceled = trade.status === 'CANCELED'

  return (
    <section className="trade-timeline" aria-label="거래 진행 상태">
      <ol className="trade-timeline__steps">
        {steps.map((step) => (
          <li
            key={step.key}
            className={[
              'trade-timeline__step',
              `trade-timeline__step--${step.state.toLowerCase()}`,
              // 지금 차례가 <b>보는 사람 자신</b>인가. 표시는 화면이 정하고 여기서는
              // 사실만 붙인다 — "여기까지 왔다" 와 "당신이 할 차례다" 는 다른 말이라,
              // 손댈 수 없는 단계까지 재촉하는 표시를 받으면 안 된다.
              step.state === 'CURRENT' && viewer && step.actor === viewer
                ? 'trade-timeline__step--mine'
                : '',
            ]
              .filter(Boolean)
              .join(' ')}
            // 진행 중인 단계를 보조기술에도 알린다. 색과 굵기만으로 구분하면
            // 화면을 못 보는 사용자에게는 네 항목이 똑같이 읽힌다.
            aria-current={step.state === 'CURRENT' ? 'step' : undefined}
          >
            <span className="trade-timeline__marker" aria-hidden="true" />
            <div className="trade-timeline__body">
              <span className="trade-timeline__label">{step.label}</span>
              {step.at !== null ? (
                <time className="trade-timeline__at" dateTime={step.at}>
                  {formatTradeDateTime(step.at)}
                </time>
              ) : (
                <span className="trade-timeline__at">{PLACEHOLDER[step.state]}</span>
              )}
              {step.detail && <span className="trade-timeline__detail">{step.detail}</span>}
            </div>
          </li>
        ))}
      </ol>

      {/* 취소 사유는 사람이 적었을 때만 있다 — 사유 없는 취소가 정상 경로라 "사유 없음"
          같은 빈 자리를 만들지 않는다. 취소 시각은 위 첫 단계 옆에 붙는다. */}
      {isCanceled && trade.cancelReason && (
        <p className="trade-timeline__cancel-reason" role="status">
          취소 사유: {trade.cancelReason}
        </p>
      )}

      {/* 지금 누구를 기다리는지. 상태 배지만으로는 "내가 뭘 해야 하나" 에 답하지 못한다. */}
      {!isCanceled && current && viewer && (
        <p className="trade-timeline__next" role="status">
          {nextActionMessage(current, viewer, trade)}
        </p>
      )}
    </section>
  )
}

/** 아직 끝나지 않은 단계의 시각 자리. 빈 칸을 두면 화면이 깨진 것처럼 보인다. */
const PLACEHOLDER: Record<StepState, string> = {
  DONE: '-',
  CURRENT: '진행 중',
  PENDING: '대기',
  STOPPED: '진행되지 않음',
}

/**
 * 단계와 상태를 만든다.
 *
 * 판정 기준은 **시각의 유무가 아니라 `status`** 다. 배송지 기능 이전에 만들어진 거래처럼
 * 상태는 넘어갔는데 시각이 비어 있는 데이터가 실제로 있고, 시각으로 판정하면 그런 거래가
 * 영원히 "진행 중" 으로 남는다. 시각은 표시에만 쓴다.
 */
function buildSteps(trade: Trade): Step[] {
  const reached = REACHED_INDEX[trade.status]
  const canceled = trade.status === 'CANCELED'

  const definitions: Omit<Step, 'state'>[] = [
    {
      key: 'created',
      label: canceled ? '구매 요청 · 취소' : '구매 요청',
      at: trade.createdAt,
      // 취소는 어느 단계에서 났는지 상태가 말해 주지 않는다. 그래서 시각만 첫 줄에 붙여
      // "언제 끝났는가" 를 남긴다 — 따로 문단을 두면 같은 말이 두 번이다.
      detail: canceled && trade.canceledAt !== null
        ? `${formatKg(trade.quantity)} · ${formatTradeDateTime(trade.canceledAt)} 취소`
        : formatKg(trade.quantity),
      actor: 'BUYER',
    },
    { key: 'paid', label: '결제 완료', at: trade.paidAt, actor: 'BUYER' },
    {
      key: 'shipped',
      label: '발송',
      at: trade.shippedAt,
      // 송장은 발송 단계에서만 의미가 있다. 없이 보내는 전달 방식도 정상이라
      // 없으면 줄 자체를 만들지 않는다.
      detail: invoiceOf(trade),
      actor: 'SELLER',
    },
    {
      key: 'completed',
      label: '수령 확인',
      at: trade.receivedAt,
      detail: signatureNote(trade),
      actor: 'BUYER',
    },
  ]

  return definitions.map((step, index) => ({ ...step, state: stateOf(step, index, reached, canceled) }))
}

function stateOf(
  step: Omit<Step, 'state'>,
  index: number,
  reached: number,
  canceled: boolean,
): StepState {
  if (index < reached) return 'DONE'

  // 취소된 거래는 어느 단계에서 취소됐는지 status 만으로 알 수 없다 (하나로 덮인다).
  // 그래서 **시각이 남아 있는 단계는 실제로 일어난 일로 본다** — 결제 후 취소된 거래의
  // 결제를 "진행되지 않음" 으로 적으면 사실과 다르다.
  //
  // 남은 단계는 '대기' 가 아니라 '진행되지 않음' 이다. 기다리면 진행될 것처럼 읽히면 안 된다.
  if (canceled) return step.at === null ? 'STOPPED' : 'DONE'

  return index === reached ? 'CURRENT' : 'PENDING'
}

/**
 * 상태별로 **몇 번째 단계까지 끝났는가**.
 *
 * 이 값이 곧 "지금 진행해야 할 단계의 인덱스" 가 된다. `CANCELED` 는 **어느 단계에서
 * 취소됐는지를 상태가 말해주지 않는다** — 취소 시각(`canceledAt`)은 있지만 그것은 "언제" 이고
 * "어디까지 갔었는지" 가 아니다. 그래서 각 단계의 시각이 남아 있는지로 판정한다
 * (`stateOf` 참고).
 */
const REACHED_INDEX: Record<TradeStatus, number> = {
  CREATED: 1,
  PAID: 2,
  SHIPPED: 3,
  RECEIVED: 4,
  COMPLETED: 4,
  CANCELED: 1,
}

function invoiceOf(trade: Trade): string | null {
  const carrier = trade.shipping?.carrier
  const trackingNumber = trade.shipping?.trackingNumber
  if (!carrier && !trackingNumber) return null
  return [carrier, trackingNumber].filter(Boolean).join(' · ')
}

/**
 * 지갑 서명 안내.
 *
 * `buyerSignedAt` 은 수령 시각과 다른 값이다 — 검증 단계 `TRANSACTION_VERIFIED` 의 근거다.
 * 다만 지금 서버는 둘을 같은 시각으로 남기므로, 같은 값을 두 번 보여줄 이유가 없다.
 * 온체인 서명이 붙어 시각이 갈라지면 그때부터 이 줄이 나타난다.
 */
function signatureNote(trade: Trade): string | null {
  if (trade.buyerSignedAt === null || trade.buyerSignedAt === trade.receivedAt) return null
  return `지갑 서명 ${formatTradeDateTime(trade.buyerSignedAt)}`
}

/** 지금 이 단계를 누가 해야 하는지, 보는 사람 기준으로 말한다. */
function nextActionMessage(step: Step, viewer: TradeViewer, trade: Trade): string {
  const mine = step.actor === viewer

  if (step.key === 'paid') {
    return mine ? '결제를 진행해 주세요.' : '구매자의 결제를 기다리는 중입니다.'
  }
  if (step.key === 'shipped') {
    return mine
      ? '결제가 완료되었습니다. 소재를 발송하고 발송 처리를 해주세요.'
      : '판매자의 발송을 기다리는 중입니다.'
  }
  // 수령 확인. 소유권이 넘어가는 단계라 양쪽 모두에게 그 사실을 알린다.
  return mine
    ? `소재를 받으셨다면 수령 확인을 해주세요. 확인 시 ${formatKg(trade.quantity)}의 소유권이 이전됩니다.`
    : '구매자의 수령 확인을 기다리는 중입니다. 확인되면 소유권이 이전됩니다.'
}
