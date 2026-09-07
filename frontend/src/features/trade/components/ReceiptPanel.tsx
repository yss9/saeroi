import { useState } from 'react'
import { Button, ConfirmModal, ErrorMessage, StatusBadge } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useConfirmTradeReceipt } from '../hooks/useConfirmTradeReceipt.ts'
import {
  TRADE_STATUS_LABEL,
  type Trade,
  type TradeReceiptResult,
} from '../types/trade.types.ts'
import './trade.css'

export interface ReceiptPanelProps {
  trade: Trade
}

/**
 * 구매자 수령 확인 패널.
 *
 * **소유권이 넘어오는 자리다.** 결제도 배송도 소유권을 옮기지 않는다 (정책 07.2) —
 * 이 버튼을 누르는 순간 서버가 판매자 보유량을 줄이고 내 보유량을 늘리며 판매 잠금을
 * 소비한다. 그래서 확인 단계를 두고, 결과로 받은 수량을 그대로 보여준다.
 *
 * 되돌릴 수 없다. 물건을 받지 못했다면 누르지 않는 것이 유일한 방어이며, 발송된 거래는
 * `/cancel` 도 막혀 있어 분쟁 절차(정책 09)로 가야 한다. 그 절차는 아직 구현되지 않았고
 * `DISPUTED` 상태도 서버 ENUM 에 없다 — 그래서 문구로 분명히 알린다.
 */
export function ReceiptPanel({ trade }: ReceiptPanelProps) {
  const { mutate, isPending, isError, isSuccess, error, data } = useConfirmTradeReceipt(
    trade.tradeId,
  )
  const [confirming, setConfirming] = useState(false)

  // `isSuccess` 를 함께 보는 이유는 ShipmentPanel 과 같다 — 상세가 캐시에 없는 경로로
  // 열렸다면 훅이 병합할 대상이 없어 `trade.status` 가 그대로다. 그래도 방금 수령을
  // 확인한 결과는 화면에 남아야 한다.
  const done = isSuccess || trade.status === 'RECEIVED' || trade.status === 'COMPLETED'

  return (
    <section className="receipt-panel">
      <header className="receipt-panel__header">
        <h2>수령 확인</h2>
        <StatusBadge tone={done ? 'success' : 'neutral'}>
          {TRADE_STATUS_LABEL[trade.status]}
        </StatusBadge>
      </header>

      {trade.status === 'CREATED' && (
        <p className="receipt-panel__note">결제를 완료하면 판매자가 소재를 발송합니다.</p>
      )}

      {trade.status === 'PAID' && (
        <p className="receipt-panel__note">
          판매자의 발송을 기다리는 중입니다. 발송되면 여기에서 수령을 확인할 수 있습니다.
        </p>
      )}

      {trade.status === 'CANCELED' && (
        <p className="receipt-panel__note">취소된 거래입니다.</p>
      )}

      {trade.status === 'SHIPPED' && !isSuccess && (
        <>
          <dl className="receipt-panel__summary">
            <div>
              <dt>수령 예정 수량</dt>
              <dd>{formatKg(trade.quantity)}</dd>
            </div>
            <div>
              <dt>발송 시각</dt>
              <dd>{formatDateTime(trade.shippedAt)}</dd>
            </div>
            {/* 서버가 배송 정보를 내려주기 전에는 이 항목이 나오지 않는다.
                없다고 수령 확인을 막지는 않는다 — 물건은 이미 도착했을 수 있다. */}
            {trade.shipping?.trackingNumber && (
              <div>
                <dt>운송장</dt>
                <dd className="receipt-panel__tracking">
                  {trade.shipping.carrier ? `${trade.shipping.carrier} · ` : ''}
                  {trade.shipping.trackingNumber}
                </dd>
              </div>
            )}
          </dl>

          <p className="receipt-panel__warning">
            받은 소재의 수량과 상태를 먼저 확인해 주세요. 수령을 확인하면 소유권이 이전되고
            되돌릴 수 없습니다.
          </p>

          {isError && <ErrorMessage error={error} />}

          <Button onClick={() => setConfirming(true)} loading={isPending} fullWidth>
            수령 확인
          </Button>
        </>
      )}

      {done && <ReceiptResult trade={trade} result={data ?? null} />}

      <ConfirmModal
        open={confirming}
        title="수령을 확인할까요?"
        message={`${formatKg(trade.quantity)}을 받았음을 확인합니다. 소유권이 즉시 이전되며 되돌릴 수 없습니다.`}
        confirmLabel="수령 확정"
        loading={isPending}
        onConfirm={() => mutate(undefined, { onSettled: () => setConfirming(false) })}
        onClose={() => setConfirming(false)}
      />
    </section>
  )
}

/**
 * 수령 결과.
 *
 * 서버가 이전 수량과 **양쪽 잔량**을 함께 돌려준다. 그 값을 그대로 보여주는 이유는,
 * 이 요청의 결과가 곧 "얼마가 옮겨졌는가" 이기 때문이다. 보유 소재 화면을 따로 열어
 * 확인하게 만들면 사용자는 방금 한 일이 반영됐는지 알 수 없다.
 *
 * 새로고침하면 `mutation.data` 가 사라지므로 그때는 거래의 시각만 보여준다.
 * 잔량은 보유 소재 화면이 이미 갱신돼 있다 (`useConfirmTradeReceipt` 가 무효화한다).
 */
function ReceiptResult({
  trade,
  result,
}: {
  trade: Trade
  result: TradeReceiptResult | null
}) {
  return (
    <dl className="receipt-panel__result">
      <div>
        <dt>수령 확인 시각</dt>
        <dd>{formatDateTime(trade.receivedAt ?? result?.receivedAt ?? null)}</dd>
      </div>
      {result && (
        <>
          <div>
            <dt>이전된 수량</dt>
            <dd>{formatKg(result.transferredQuantity)}</dd>
          </div>
          <div>
            <dt>내 보유 수량</dt>
            <dd>{formatKg(result.buyerQuantity)}</dd>
          </div>
        </>
      )}
    </dl>
  )
}

function formatDateTime(value: string | null): string {
  if (value === null) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
}
