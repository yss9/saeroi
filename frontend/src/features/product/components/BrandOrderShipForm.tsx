import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import type { BrandOrder, BrandOrderShipRequest } from '../types/brandOrder.types.ts'
import './product.css'

export interface BrandOrderShipFormProps {
  order: BrandOrder
  onSubmit: (request: BrandOrderShipRequest) => void
  onCancel: () => void
  isSubmitting?: boolean
  error?: unknown
}

/**
 * 발송 처리 입력.
 *
 * **송장 정보는 선택이다.** 직접 전달이나 퀵처럼 운송장이 없는 방식이 있어, 둘 다 비우고
 * 발송할 수 있다.
 *
 * ⚠️ **운송장만 적고 택배사를 비울 수는 없다.** 조회할 방법이 없는 조합이라 서버가 400 으로
 * 거절한다 — 여기서 먼저 막는다. 송장을 다 입력하고 나서 거절당하면 그 입력이 헛수고다.
 */
export function BrandOrderShipForm({
  order,
  onSubmit,
  onCancel,
  isSubmitting = false,
  error,
}: BrandOrderShipFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<BrandOrderShipRequest>({ defaultValues: { carrier: '', trackingNumber: '' } })

  return (
    <form className="ship-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      {/*
        송장에 쓸 이름을 가장 먼저, 크게 둔다. 주문자와 다를 수 있어 — 선물이나 가족 집으로
        보내는 주문이 그렇다 — 주문자 이름을 적으면 물건이 엉뚱한 사람 앞으로 간다.
      */}
      <div className="ship-form__receiver">
        <span className="ship-form__label">받는 분</span>
        <strong>{order.shipping.receiverName}</strong>
        <span>{order.shipping.receiverPhone}</span>
        <address>
          [{order.shipping.zipCode}] {order.shipping.address}
          {order.shipping.addressDetail ? ` ${order.shipping.addressDetail}` : ''}
        </address>
        <p className="ship-form__buyer">주문자 {order.buyerName}</p>
      </div>

      {/* 같은 모델이라도 개체마다 다른 물건이다. 무엇을 집어야 하는지가 여기 있다. */}
      <ul className="ship-form__items">
        {order.items.map((item) => (
          <li key={item.itemCode}>
            <strong>{item.productName}</strong>
            <code>{item.itemCode}</code>
            <span>#{item.serialNumber}</span>
          </li>
        ))}
      </ul>

      <div className="ship-form__fields">
        <Input
          label="택배사"
          placeholder="CJ대한통운"
          hint="직접 전달이면 비워 두세요."
          error={errors.carrier?.message}
          {...register('carrier', {
            maxLength: { value: 50, message: '택배사는 50자 이하로 입력해 주세요.' },
          })}
        />
        <Input
          label="운송장 번호"
          inputMode="numeric"
          placeholder="123456789012"
          error={errors.trackingNumber?.message}
          {...register('trackingNumber', {
            maxLength: { value: 100, message: '운송장 번호는 100자 이하로 입력해 주세요.' },
            // 서버가 거절하는 조합이다. 여기서 막지 않으면 제출 뒤에야 알게 된다.
            //
            // 옆 칸은 `watch` 대신 검증 함수의 두 번째 인자로 읽는다 — watch 는 글자를
            // 칠 때마다 폼 전체를 다시 그리고, lint 도 메모이즈할 수 없다고 경고한다.
            validate: (value, values) =>
              !value?.trim() || Boolean(values.carrier?.trim()) || '택배사를 함께 입력해 주세요.',
          })}
        />
      </div>

      {error !== undefined && error !== null && <ErrorMessage error={error} />}

      <div className="ship-form__actions">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          취소
        </Button>
        <Button type="submit" loading={isSubmitting}>
          발송 처리
        </Button>
      </div>
    </form>
  )
}
