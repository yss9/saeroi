import { Button, StatusBadge } from '../../../components/common/index.ts'
import { formatWon } from '../../../utils/quantity.ts'
import { usePayment } from '../hooks/usePayment.ts'
import {
  TRADE_STATUS_LABEL,
  type PaymentPhase,
  type TradeDetail,
} from '../types/trade.types.ts'

export interface PaymentPanelProps {
  trade: TradeDetail
}

const PHASE_MESSAGE: Record<PaymentPhase, string> = {
  IDLE: '',
  PREPARING: '결제를 준비하고 있습니다…',
  AWAITING_PG: '결제창에서 결제를 완료해 주세요.',
  CONFIRMING: '결제를 확인하고 있습니다…',
  SUCCEEDED: '결제가 완료되었습니다.',
  CANCELED: '거래가 취소되었습니다.',
  FAILED: '결제를 완료하지 못했습니다.',
}

const PHASE_TONE: Record<PaymentPhase, 'neutral' | 'info' | 'success' | 'danger'> = {
  IDLE: 'neutral',
  PREPARING: 'info',
  AWAITING_PG: 'info',
  CONFIRMING: 'info',
  SUCCEEDED: 'success',
  CANCELED: 'neutral',
  FAILED: 'danger',
}

/**
 * 결제 패널.
 *
 * 결제는 사이트를 떠났다 돌아오는 흐름이라, 서버 상태(TradeStatus)만으로는
 * "지금 어디까지 왔는지" 를 보여줄 수 없다. usePayment 의 PaymentPhase 가
 * 그 구간을 채운다.
 *
 * TODO(3번): 실제 카카오페이 연동 시 결제창 복귀 처리를 붙인다.
 * 복귀 URL 로 돌아오면 `pg_token` 쿼리를 읽어 `confirm(tradeId, pgToken)` 을 호출한다.
 * 라우트는 `/trades/:tradeId/payment/return` 정도가 적당하다.
 */
export function PaymentPanel({ trade }: PaymentPanelProps) {
  const payment = usePayment()

  const alreadyPaid = trade.status !== 'CREATED'
  const isCanceled = trade.status === 'CANCELED'

  return (
    <section className="payment-panel">
      <header className="payment-panel__header">
        <h2>결제</h2>
        <StatusBadge tone={alreadyPaid && !isCanceled ? 'success' : 'neutral'}>
          {TRADE_STATUS_LABEL[trade.status]}
        </StatusBadge>
      </header>

      <dl className="payment-panel__summary">
        <div>
          <dt>결제 금액</dt>
          <dd className="payment-panel__total">{formatWon(trade.totalPrice)}</dd>
        </div>
        {trade.paymentMethod && (
          <div>
            <dt>결제 수단</dt>
            <dd>카카오페이</dd>
          </div>
        )}
        {/* 결제 참조(tid)는 문의·정산 대조에 쓰인다. 결제 후에만 존재한다. */}
        {trade.paymentReference && (
          <div>
            <dt>결제 번호</dt>
            <dd className="payment-panel__reference">{trade.paymentReference}</dd>
          </div>
        )}
        {trade.cancelReference && (
          <div>
            <dt>취소 번호</dt>
            <dd className="payment-panel__reference">{trade.cancelReference}</dd>
          </div>
        )}
      </dl>

      {payment.phase !== 'IDLE' && (
        <p className="payment-panel__phase" role="status" aria-live="polite">
          <StatusBadge tone={PHASE_TONE[payment.phase]}>{payment.phase}</StatusBadge>
          <span>{payment.message ?? PHASE_MESSAGE[payment.phase]}</span>
        </p>
      )}

      <div className="payment-panel__actions">
        {!alreadyPaid && (
          <Button
            onClick={() => void payment.start(trade.id)}
            loading={payment.isBusy}
            fullWidth
          >
            결제하기
          </Button>
        )}

        {/* 배송 시작 전까지만 취소할 수 있다. 그 뒤는 분쟁 절차다 (정책 07.3). */}
        {(trade.status === 'CREATED' || trade.status === 'PAID') && (
          <Button
            variant="secondary"
            onClick={() => void payment.cancel(trade.id, '구매자 요청')}
            disabled={payment.isBusy}
          >
            거래 취소
          </Button>
        )}
      </div>

      {/* Mock 구간 안내. 실제 연동 전까지 결제창이 뜨지 않는다는 것을 명시한다 —
          없으면 "버튼을 눌렀는데 아무 일도 안 난다" 로 읽힌다. */}
      {payment.phase === 'AWAITING_PG' && (
        <div className="payment-panel__mock">
          <p>결제 API가 아직 연결되지 않아 결제창이 열리지 않습니다.</p>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => void payment.confirm(trade.id, 'MOCK-PG-TOKEN')}
          >
            결제 완료로 처리 (테스트)
          </Button>
        </div>
      )}
    </section>
  )
}
