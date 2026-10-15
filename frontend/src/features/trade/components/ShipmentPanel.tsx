import { useState } from 'react'
import { Copy, Warning } from '@phosphor-icons/react'
import { useForm, useWatch } from 'react-hook-form'
import {
  Button,
  ConfirmModal,
  ErrorMessage,
  Input,
  Modal,
} from '../../../components/common/index.ts'
import type { AppError } from '../../../utils/errors.ts'
import { formatTradeDateTime } from '../utils/tradeDateTime.ts'
import { useCancelTrade } from '../hooks/useCancelTrade.ts'
import { useShipTrade } from '../hooks/useShipTrade.ts'
import { useTradeChainAction } from '../hooks/useTradeChainAction.ts'
import {
  TRADE_STATUS_LABEL,
  type ShipTradeRequest,
  type Trade,
  type TradeShipResult,
} from '../types/trade.types.ts'
import './trade.css'
import { CARRIERS } from '../../../utils/carriers.ts'

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
  const chain = useTradeChainAction(trade.tradeId)

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

  // 서버가 허용하는 취소 구간과 같다 — CREATED·PAID 뿐이고 그 밖에서는
  // PAYMENT_STATE_INVALID 로 거절된다. 발송한 뒤에도 버튼이 남아 있으면 판매자는
  // 취소가 가능하다고 읽는다.
  const cancelable = !isSuccess && (trade.status === 'CREATED' || trade.status === 'PAID')

  return (
    <section className="shipment-panel">
      {/* 머리와 본문을 1px 선으로 가른다 — 그림자 대신 선으로 층을 만드는 것이 이
          콘솔의 규칙이고, 훑을 때 어디가 제목이고 어디가 내용인지 기준이 된다. */}
      <header className="shipment-panel__header">
        <h2>{shipped ? '발송 완료' : trade.status === 'PAID' ? '발송하기' : '발송'}</h2>
        <span className={`console-badge ${trade.status === 'PAID' ? 'console-badge--wait' : 'console-badge--flat'}`}>
          {trade.status === 'PAID' ? '발송 필요' : TRADE_STATUS_LABEL[trade.status]}
        </span>
      </header>

      {/*
        구매자와 수량을 여기서 또 적지 않는다. 상세 화면 오른쪽 요약이 이미 들고 있어서,
        같은 값이 한 화면에 두 번 나오면 채워진 것이 아니라 부풀린 것처럼 읽힌다.
        이 패널이 맡는 것은 <b>보내는 데 필요한 것</b> — 배송지와 송장이다.
      */}
      <ShippingAddress trade={trade} />

      {trade.status === 'CREATED' && (
        <p className="shipment-panel__note">
          구매자가 아직 결제하지 않았습니다. 결제가 완료되면 발송 처리를 할 수 있습니다.
        </p>
      )}

      {trade.status === 'CANCELED' && <CanceledNotice trade={trade} />}

      {shipped && <ShippedReceipt trade={trade} justShipped={data ?? null} />}

      {/*
        <b>온체인 주문이 먼저다.</b> 컨트랙트의 markShipped(orderId) 는 주문 번호 없이는
        부를 수도 없어서, 그대로 발송하면 서버만 앞서 나간다. 실제로 그렇게 새어 나갔다 —
        주문 서명을 건너뛴 거래가 발송·수령까지 끝나 DB 는 소재를 넘겨줬는데 체인은 그
        거래를 모르는 채로 굳었고, 그 소재를 가공에 보낼 때 insufficient material 로
        거절당해서야 드러났다.

        그 시점에는 되돌릴 수 없다. 온체인 기록은 결제 직후 구간에서만 남길 수 있는데
        거래는 이미 그 구간을 지나쳤기 때문이다. 서버도 막지만(TRADE_CHAIN_ORDER_REQUIRED),
        눌러서 오류를 보는 것보다 왜 지금 못 하는지 먼저 읽는 편이 낫다.
      */}
      {trade.status === 'PAID' && !isSuccess && trade.chainTradeId === null && (
        <p className="shipment-panel__note">
          이 거래는 아직 블록체인에 기록되지 않았습니다. 구매자가 주문을 서명해 온체인에
          기록한 뒤에 발송할 수 있습니다.
        </p>
      )}

      {trade.status === 'PAID' && !isSuccess && trade.chainTradeId !== null && (
        <form onSubmit={onSubmit} noValidate className="shipment-panel__form">
          {/*
            <b>고르게 하되 가두지 않는다.</b> 목록에 없는 택배사나 화물 업체를 쓰는 경우가
            있어 select 로 바꾸면 그때 아무것도 적을 수 없다. datalist 는 자주 쓰는 것을
            제안만 하고 직접 입력을 막지 않는다.
          */}
          <Input
            label="택배사"
            placeholder="예: CJ대한통운"
            hint="직접 전달이라 송장이 없으면 비워 두세요."
            list="shipment-carriers"
            error={errors.carrier?.message}
            {...register('carrier', {
              maxLength: { value: 50, message: '택배사는 50자를 넘을 수 없습니다.' },
            })}
          />
          <datalist id="shipment-carriers">
            {CARRIERS.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>

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

          {/*
            <b>규칙을 제출 전에 알린다.</b> 예전에는 운송장만 적고 누른 뒤에야 오류로
            알았다. 서버도 같은 조합을 거절하는데(TRADE_CARRIER_REQUIRED), 왜 막히는지를
            먼저 말해 주면 한 번 헛수고할 일이 없다.
          */}
          <p className="shipment-panel__rule">
            <Warning aria-hidden="true" weight="fill" />
            운송장 번호만 적고 택배사를 비우면 조회가 되지 않아 등록되지 않습니다.
          </p>

          {/* 문구만 바꾸고 code·kind 는 그대로 넘긴다 — ErrorMessage 는 AppError 를
              그대로 다룰 수 있고, 나중에 kind 로 분기할 여지도 남는다. */}
          {isError && <ErrorMessage error={{ ...error, message: shipErrorMessage(error) }} />}

          {/*
            시안에는 버튼이 하나("발송 확정")지만 우리는 확인 모달을 한 번 더 거친다.
            둘 다 "발송 확정" 이면 같은 이름의 버튼이 한 화면에 둘 생긴다 — 누른 뒤에도
            같은 글자가 다시 떠서 눌리지 않은 것처럼 읽힌다. 시작은 "처리", 확인은 "확정".
          */}
          <p className="shipment-panel__warn">발송을 확정하면 되돌릴 수 없습니다.</p>
          <Button type="submit" loading={isPending} fullWidth>
            발송 처리
          </Button>
        </form>
      )}

      {/* 발송 전까지만 취소할 수 있다. 그 뒤는 분쟁 절차다 (정책 09). */}
      {cancelable && <CancelSection trade={trade} />}

      {/* 발송은 취소할 수 없다. 송장을 비워 둔 채 누르는 실수도 여기서 한 번 걸린다. */}
      <ConfirmModal
        open={pending !== null}
        title="발송 처리할까요?"
        message={confirmMessage(pending)}
        confirmLabel="발송 확정"
        loading={isPending}
        onConfirm={() => {
          if (pending === null) return

          // **서명이 먼저다.** 체인에 발송이 남은 뒤에 서버가 DB 를 기록한다 — 서버도
          // 같은 순서를 검사한다(TRADE_CHAIN_SHIPMENT_REQUIRED).
          //
          // 예전에는 반대였다. 그래서 판매자가 서명 창을 끄면 DB 는 SHIPPED 인데 체인은
          // CREATED 로 남았고, 그러면 구매자가 수령 서명을 할 수 없다 — 컨트랙트의
          // confirmReceipt 는 체인이 SHIPPED 일 때만 받는다. 거래가 그 자리에서 멈춘다.
          //
          // 서명이 실패하면 발송을 기록하지 않는다. 서버가 어차피 거절한다.
          if (trade.chainTradeId === null) {
            setPending(null)
            return
          }
          const waybill = pending
          void chain
            .ship(trade.chainTradeId)
            .then(() => mutate(waybill, { onSettled: () => setPending(null) }))
            .catch(() => setPending(null))
        }}
        onClose={() => setPending(null)}
      />
    </section>
  )
}

/** 취소 사유 최대 길이. 서버 검증(`TradeCancelRequest`)과 같은 값이다. */
const CANCEL_REASON_MAX = 255

/**
 * 판매자 거래 취소.
 *
 * **사유는 선택이다.** 강제하면 의미 없는 문자열이 쌓이고, 급히 취소해야 하는 상황에서
 * 취소 자체가 늦어진다. 다만 판매자가 취소하는 건은 구매자가 이유를 알아야 하는 경우가
 * 많아(재고 부족·파손 등) 입력 자리를 눈에 보이게 둔다.
 *
 * ConfirmModal 을 쓰지 않고 Modal 을 직접 쓴다 — ConfirmModal 은 문구만 받고 입력을 받을
 * 자리가 없다. 사유를 받으면서 확인 단계를 유지해야 하므로 이쪽이 맞다.
 */
function CancelSection({ trade }: { trade: Trade }) {
  const [open, setOpen] = useState(false)
  // 모달 안의 단일 선택 입력이라 useState 로 둔다. 교차 검증이 없고 길이 하나만 보므로
  // React Hook Form 을 끌어올 이유가 없다 (ARCHITECTURE §3 의 "폼이 많은 화면" 이 아니다).
  const [reason, setReason] = useState('')
  const { mutate, isPending, isError, error } = useCancelTrade(trade.tradeId)
  const chain = useTradeChainAction(trade.tradeId)

  const tooLong = reason.length > CANCEL_REASON_MAX

  function close() {
    if (isPending) return
    setOpen(false)
    setReason('')
  }

  return (
    <>
      <div className="shipment-panel__actions">
        <Button variant="secondary" onClick={() => setOpen(true)}>
          거래 취소
        </Button>
      </div>

      {isError && <ErrorMessage error={error} />}

      <Modal
        open={open}
        onClose={close}
        title="거래를 취소할까요?"
        dismissible={!isPending}
        footer={
          <>
            <Button variant="secondary" onClick={close} disabled={isPending}>
              닫기
            </Button>
            <Button
              variant="danger"
              loading={isPending}
              disabled={tooLong}
              onClick={() =>
                mutate(
                  { reason },
                  {
                    // **서버 취소가 끝나면 체인의 주문도 취소해야 한다.**
                    //
                    // createOrder 가 판매 글의 remainingQuantity 를 깎아 두었다. 서버는
                    // 취소하면서 그 수량을 되돌리는데 체인은 그대로라, 두 잔량이 어긋난다.
                    // 그 뒤 다음 구매자가 화면에 보이는 잔량을 전부 사면 서버는 통과하고
                    // 체인만 되돌려진다 — "10kg 남았는데 10kg 사면 부족" 이 그것이다.
                    onSuccess: () => {
                      if (trade.chainTradeId !== null) void chain.cancel(trade.chainTradeId)
                      setOpen(false)
                    },
                  },
                )
              }
            >
              거래 취소
            </Button>
          </>
        }
      >
        <p className="cancel-form__lead">
          취소하면 이 수량이 판매글로 되돌아가고 거래는 되돌릴 수 없습니다.
          {/* 결제까지 끝난 거래는 환불이 함께 일어난다. 판매자가 그것을 모르고 누르면
              "왜 환불이 됐냐" 가 된다. */}
          {trade.status === 'PAID' && ' 결제가 완료된 거래라 환불이 함께 요청됩니다.'}
        </p>

        <label className="cancel-form__label" htmlFor="cancel-reason">
          취소 사유 <span className="cancel-form__optional">(선택)</span>
        </label>
        <textarea
          id="cancel-reason"
          className="cancel-form__textarea"
          rows={3}
          value={reason}
          placeholder="예: 재고 부족으로 발송이 어렵습니다"
          aria-invalid={tooLong ? true : undefined}
          aria-describedby="cancel-reason-hint"
          onChange={(event) => setReason(event.target.value)}
        />
        <p
          id="cancel-reason-hint"
          className={`cancel-form__hint${tooLong ? ' cancel-form__hint--error' : ''}`}
          role={tooLong ? 'alert' : undefined}
        >
          {tooLong
            ? `취소 사유는 ${CANCEL_REASON_MAX}자를 넘을 수 없습니다.`
            : `적지 않아도 취소됩니다. ${reason.length}/${CANCEL_REASON_MAX}자`}
        </p>
      </Modal>
    </>
  )
}

/**
 * 취소된 거래 안내.
 *
 * 시각은 항상 있고 사유는 사람이 적었을 때만 있다. 사유가 없다고 "사유 없음" 처럼
 * 빈 자리를 만들지 않는다 — 사유 없는 취소가 정상 경로다.
 *
 * ⚠️ **취소해도 판매글의 잠금은 풀리지 않는다.** 되돌린 수량이 다시 판매 중이 되기
 * 때문이다. "이제 이 수량을 자유롭게 쓸 수 있다" 고 안내하면 틀린다 — 잠금이 풀리는 것은
 * 판매글 자체를 내릴 때뿐이다.
 */
function CanceledNotice({ trade }: { trade: Trade }) {
  return (
    <div className="shipment-panel__canceled">
      <p className="shipment-panel__note shipment-panel__note--warning">
        취소된 거래입니다. 발송하지 마세요.
      </p>
      <dl className="shipment-panel__result">
        <div>
          <dt>취소 시각</dt>
          <dd>{formatTradeDateTime(trade.canceledAt)}</dd>
        </div>
        {trade.cancelReason && (
          <div>
            <dt>취소 사유</dt>
            <dd className="shipment-panel__reason">{trade.cancelReason}</dd>
          </div>
        )}
      </dl>
      <p className="shipment-panel__note">
        되돌린 수량은 판매글에 다시 올라가 있습니다. 판매를 멈추려면 판매글을 내려야 합니다.
      </p>
    </div>
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

  const full = `(${shipping.zipCode}) ${shipping.address}${shipping.addressDetail ? ` ${shipping.addressDetail}` : ''}`

  return (
    <section className="shipment-panel__address">
      <header>
        <span>배송지</span>
        {/*
          <b>주소를 손으로 옮겨 적게 두지 않는다.</b> 택배사 화면이나 송장에 그대로
          붙여 넣는 값이라, 눈으로 읽어 다시 치면 그 자리에서 오타가 난다.
        */}
        <CopyButton value={full} label="주소 복사" />
      </header>

      <dl>
        <div>
          <dt>수취인</dt>
          <dd>
            {shipping.receiverName} <span aria-hidden="true">·</span>{' '}
            <span className="console__num">{shipping.receiverPhone}</span>
          </dd>
        </div>
        <div>
          <dt>주소</dt>
          <dd>
            <span className="console__num">({shipping.zipCode})</span> {shipping.address}
            {shipping.addressDetail ? ` ${shipping.addressDetail}` : ''}
          </dd>
        </div>
      </dl>
    </section>
  )
}

/**
 * 값을 클립보드로 옮기는 작은 버튼.
 *
 * 눌렀다는 사실을 문구로 바꿔 알린다 — 아무 반응이 없으면 눌렸는지 알 수 없어 한 번 더
 * 누르게 된다. 클립보드 접근이 막힌 환경(비보안 컨텍스트)에서는 조용히 넘긴다.
 */
function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false)

  return (
    <button
      type="button"
      className="shipment-panel__copy"
      onClick={() => {
        void navigator.clipboard
          ?.writeText(value)
          .then(() => {
            setCopied(true)
            setTimeout(() => setCopied(false), 1800)
          })
          .catch(() => {})
      }}
    >
      <Copy aria-hidden="true" />
      {copied ? '복사됨' : label}
    </button>
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
          예약된 판매 수량이 다시 판매 가능해집니다.
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
