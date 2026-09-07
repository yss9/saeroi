import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import {
  Button,
  ConfirmModal,
  ErrorMessage,
  Input,
  StatusBadge,
} from '../../../components/common/index.ts'
import type { AppError } from '../../../utils/errors.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { formatTradeDateTime } from '../utils/tradeDateTime.ts'
import { useShipTrade } from '../hooks/useShipTrade.ts'
import {
  TRADE_STATUS_LABEL,
  type ShipTradeRequest,
  type Trade,
  type TradeShipResult,
} from '../types/trade.types.ts'
import './trade.css'

export interface ShipmentPanelProps {
  trade: Trade
}

/**
 * 판매자 발송 처리 패널.
 *
 * `PAID` 인 거래에서만 발송할 수 있다. 결제 전(`CREATED`)에 보내면 서버가
 * `TRADE_NOT_SHIPPABLE` 로 거절하는데, 판매자 입장에서는 "왜 안 되는지" 가 더 중요하므로
 * 버튼을 숨기고 이유를 적는다.
 *
 * **발송은 되돌릴 수 없다.** 발송 뒤에는 `/cancel` 이 막히고(취소는 발송 전까지만)
 * 재발송도 서버가 거부한다. 그래서 제출과 실제 요청 사이에 확인 단계를 둔다.
 *
 * 송장은 선택이다 — 직접 수령이나 화물처럼 송장이 없는 전달 방식이 있다. 다만 운송장만
 * 입력하면 조회할 방법이 없어 서버가 거절하므로(`TRADE_CARRIER_REQUIRED`) 여기서 먼저 막는다.
 */
export function ShipmentPanel({ trade }: ShipmentPanelProps) {
  const {
    control,
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ShipTradeRequest>({ defaultValues: { carrier: '', trackingNumber: '' } })

  const { mutate, isPending, isError, isSuccess, error, data } = useShipTrade(trade.tradeId)

  // 확인 단계에서 들고 있을 입력값. 확인 모달을 닫으면 버린다.
  const [pending, setPending] = useState<ShipTradeRequest | null>(null)

  // useWatch 를 쓰는 이유는 ListingForm 과 같다 — watch() 가 돌려주는 함수는 렌더마다
  // 새로 만들어져 메모이제이션할 수 없다.
  const carrier = useWatch({ control, name: 'carrier' })

  const onSubmit = handleSubmit((values) => setPending(values))

  // `isSuccess` 를 함께 보는 이유 — 훅이 상세 캐시에 병합하므로 보통은 `trade.status` 가
  // 곧바로 SHIPPED 로 바뀌어 넘어온다. 다만 상세가 캐시에 없는 경로로 이 패널이 열렸다면
  // (목록에서 받은 거래를 그대로 넘기는 경우) 병합할 대상이 없어 상태가 그대로다.
  // 그때도 방금 발송한 사실은 화면에 남아야 한다.
  const shipped =
    isSuccess ||
    (trade.status !== 'CREATED' && trade.status !== 'PAID' && trade.status !== 'CANCELED')

  return (
    <section className="shipment-panel">
      <header className="shipment-panel__header">
        <h2>발송</h2>
        <StatusBadge tone={shipped ? 'success' : 'neutral'}>
          {TRADE_STATUS_LABEL[trade.status]}
        </StatusBadge>
      </header>

      <dl className="shipment-panel__summary">
        <div>
          <dt>구매자</dt>
          <dd>{trade.buyerName}</dd>
        </div>
        <div>
          <dt>발송 수량</dt>
          <dd>{formatKg(trade.quantity)}</dd>
        </div>
      </dl>

      <ShippingAddress trade={trade} />

      {trade.status === 'CREATED' && (
        <p className="shipment-panel__note">
          구매자가 아직 결제하지 않았습니다. 결제가 완료되면 발송 처리를 할 수 있습니다.
        </p>
      )}

      {trade.status === 'CANCELED' && (
        <p className="shipment-panel__note">취소된 거래입니다. 발송하지 마세요.</p>
      )}

      {shipped && <ShippedReceipt trade={trade} justShipped={data ?? null} />}

      {trade.status === 'PAID' && !isSuccess && (
        <form onSubmit={onSubmit} noValidate className="shipment-panel__form">
          <Input
            label="택배사"
            placeholder="예: CJ대한통운"
            hint="직접 전달이라 송장이 없으면 비워 두세요."
            error={errors.carrier?.message}
            {...register('carrier', {
              maxLength: { value: 50, message: '택배사는 50자를 넘을 수 없습니다.' },
            })}
          />

          <Input
            label="운송장 번호"
            placeholder="예: 123456789012"
            error={errors.trackingNumber?.message}
            {...register('trackingNumber', {
              maxLength: { value: 100, message: '운송장 번호는 100자를 넘을 수 없습니다.' },
              // 서버도 같은 규칙으로 거절한다(TRADE_CARRIER_REQUIRED). 택배사를 모르면
              // 운송장 번호로 조회할 방법이 없어 기록해도 쓸모가 없다.
              validate: (value) =>
                !value?.trim() || carrier?.trim()
                  ? true
                  : '운송장 번호를 입력하려면 택배사도 입력해 주세요.',
            })}
          />

          {/* 문구만 바꾸고 code·kind 는 그대로 넘긴다 — ErrorMessage 는 AppError 를
              그대로 다룰 수 있고, 나중에 kind 로 분기할 여지도 남는다. */}
          {isError && <ErrorMessage error={{ ...error, message: shipErrorMessage(error) }} />}

          <Button type="submit" loading={isPending} fullWidth>
            발송 처리
          </Button>
        </form>
      )}

      {/* 발송은 취소할 수 없다. 송장을 비워 둔 채 누르는 실수도 여기서 한 번 걸린다. */}
      <ConfirmModal
        open={pending !== null}
        title="발송 처리할까요?"
        message={confirmMessage(pending)}
        confirmLabel="발송 확정"
        loading={isPending}
        onConfirm={() => {
          if (pending === null) return
          mutate(pending, { onSettled: () => setPending(null) })
        }}
        onClose={() => setPending(null)}
      />
    </section>
  )
}

/**
 * 배송지.
 *
 * `null` 인 거래는 배송지 기능이 들어오기 전에 만들어진 건이다. 그 거래는 서버에서도
 * 발송이 막히므로(`TRADE_SHIPPING_NOT_FOUND`) 빈 자리를 그냥 두지 않고 사유를 적는다 —
 * 판매자에게는 어디로 보낼지가 이 화면의 핵심 정보다.
 */
function ShippingAddress({ trade }: { trade: Trade }) {
  const shipping = trade.shipping

  if (!shipping) {
    return (
      <p className="shipment-panel__note shipment-panel__note--warning">
        이 거래에는 배송지 정보가 없습니다. 배송지 입력이 적용되기 전에 만들어진 거래라
        발송 처리가 되지 않습니다 — 운영자에게 알려 주세요.
      </p>
    )
  }

  return (
    <dl className="shipment-panel__address">
      <div>
        <dt>수취인</dt>
        <dd>
          {shipping.receiverName} · {shipping.receiverPhone}
        </dd>
      </div>
      <div>
        <dt>주소</dt>
        <dd>
          ({shipping.zipCode}) {shipping.address}
          {shipping.addressDetail ? ` ${shipping.addressDetail}` : ''}
        </dd>
      </div>
    </dl>
  )
}

/**
 * 발송 완료 표시.
 *
 * 송장을 두 곳에서 찾는다 — 방금 발송한 응답(`justShipped`)과 거래의 배송 정보다.
 * 서버가 `TradeResponse` 에 배송 정보를 담기 전까지는 새로고침하면 송장이 사라지므로,
 * 최소한 방금 처리한 판매자에게는 자기가 입력한 값이 보여야 한다.
 */
function ShippedReceipt({
  trade,
  justShipped,
}: {
  trade: Trade
  justShipped: TradeShipResult | null
}) {
  const carrier = trade.shipping?.carrier ?? justShipped?.carrier ?? null
  const trackingNumber = trade.shipping?.trackingNumber ?? justShipped?.trackingNumber ?? null
  const shippedAt = trade.shippedAt ?? justShipped?.shippedAt ?? null

  return (
    <>
      <dl className="shipment-panel__result">
        <div>
          <dt>발송 시각</dt>
          <dd>{formatTradeDateTime(shippedAt)}</dd>
        </div>
        <div>
          <dt>송장</dt>
          <dd>{formatInvoice(carrier, trackingNumber)}</dd>
        </div>
      </dl>

      {/* 발송했다고 끝이 아니다. 소유권은 구매자가 수령을 확인해야 넘어가고,
          그때까지 이 수량의 잠금이 유지되어 다시 팔 수 없다 (정책 07.2). */}
      {trade.status === 'SHIPPED' && (
        <p className="shipment-panel__note">
          구매자의 수령 확인을 기다리는 중입니다. 수령이 확인되면 소유권이 이전되고 이 수량의
          판매 잠금이 해제됩니다.
        </p>
      )}
    </>
  )
}

function confirmMessage(pending: ShipTradeRequest | null): string {
  const invoice = formatInvoice(pending?.carrier ?? null, pending?.trackingNumber ?? null)
  return `${invoice} 으로 발송 처리합니다. 발송한 뒤에는 거래를 취소할 수 없습니다.`
}

/** 송장 표기. 택배사만·운송장만·둘 다 없음이 모두 정상 상태라 세 경우를 다 다룬다. */
function formatInvoice(carrier: string | null, trackingNumber: string | null): string {
  const name = carrier?.trim()
  const number = trackingNumber?.trim()

  if (!name && !number) return '송장 없이 전달'
  // `??` 가 아니라 `||` 다 — 입력하다 지운 필드는 null 이 아니라 빈 문자열로 온다.
  return `${name || '택배사 미입력'}${number ? ` · ${number}` : ''}`
}

/**
 * 배송지 없음(`TRADE_SHIPPING_NOT_FOUND`)만 서버 문구를 바꿔 준다.
 *
 * 서버 문구는 "배송지 정보가 없는 거래입니다." 인데, 판매자가 할 수 있는 일이 없는
 * 상황이라 그대로 보여주면 자기 입력을 고치려 든다. 지금 이 오류가 나올 수 있는 거래는
 * 배송지 입력이 적용되기 전에 만들어진 건뿐이다.
 */
function shipErrorMessage(error: AppError): string {
  if (error.code === 'TRADE_SHIPPING_NOT_FOUND') {
    return '이 거래에 배송지 정보가 없어 발송 처리를 할 수 없습니다. 배송지 입력이 적용되기 전에 만들어진 거래라 판매자가 해결할 수 없습니다 — 운영자에게 알려 주세요.'
  }
  return error.message
}
