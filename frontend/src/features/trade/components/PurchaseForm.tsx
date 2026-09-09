import { Controller, useForm, useWatch } from 'react-hook-form'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import { QuantityInput } from '../../../components/common/QuantityInput.tsx'
import { calculateTotalPrice, formatKg, formatWon, kgToGram } from '../../../utils/quantity.ts'
import type { Listing } from '../../marketplace/types/marketplace.types.ts'
import { useCreateTrade } from '../hooks/useCreateTrade.ts'
import { useTradeOnChain } from '../hooks/useTradeOnChain.ts'

export interface PurchaseFormProps {
  listing: Listing
  onCreated?: (tradeId: number) => void
}

/** 폼 입력값. 수량은 kg 으로 입력하고 전송 직전에 gram 으로 바꾼다. */
interface PurchaseFormValues {
  quantityKg: number | null
  receiverName: string
  receiverPhone: string
  zipCode: string
  address: string
  addressDetail: string
}

/**
 * 부분 구매 폼 (Brand Market).
 *
 * 정책 07.1: 판매글의 **일부만 살 수 있다.** 30kg 짜리에서 10kg 만 사면
 * 판매글은 20kg 남은 채로 유지된다.
 *
 * **배송지를 여기서 받는다.** 나중에 채우는 API 가 없고, 배송 행이 없는 거래는 발송·수령이
 * 서버에서 404 로 막힌다 — 즉 배송지를 빠뜨린 거래는 판매자도 구매자도 끝낼 방법이 없다.
 *
 * 필드가 여섯 개가 되면서 React Hook Form 으로 옮겼다 (예전에는 수량 하나라 `useState`
 * 였다). input 마다 `useState` 를 두면 검증·에러 표시가 흩어지고 입력마다 폼 전체가
 * 리렌더된다. 수량은 `QuantityInput` 이 제어 컴포넌트라 `Controller` 로 연결한다.
 */
export function PurchaseForm({ listing, onCreated }: PurchaseFormProps) {
  const {
    control,
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PurchaseFormValues>({
    defaultValues: {
      quantityKg: null,
      receiverName: '',
      receiverPhone: '',
      zipCode: '',
      address: '',
      addressDetail: '',
    },
  })

  const { mutate, isPending, isError, error } = useCreateTrade()
  const chain = useTradeOnChain()

  // 입력 중인 수량으로 금액을 미리 보여준다. watch() 대신 useWatch() 를 쓰는 이유는
  // ListingForm 과 같다 — watch() 가 돌려주는 함수는 렌더마다 새로 만들어진다.
  const quantityKg = useWatch({ control, name: 'quantityKg' })

  const quantityGram = quantityKg === null || quantityKg === undefined ? 0 : kgToGram(quantityKg)
  const exceedsAvailable = quantityGram > listing.availableQuantity
  const isEmpty = quantityGram <= 0

  // 화면에 보여주는 금액은 **미리보기** 다. 실제 금액은 서버가 Listing 단가로 계산한다.
  // 클라이언트가 금액을 보내면 조작해서 싸게 살 수 있다.
  const previewTotal = calculateTotalPrice(quantityGram, listing.pricePerKg)

  const canSubmit = !isEmpty && !exceedsAvailable && listing.status === 'ON_SALE'

  const onSubmit = handleSubmit((values) => {
    if (!canSubmit) return
    mutate(
      {
        listingId: listing.listingId,
        // 단위를 함께 보낸다. 서버가 gram 으로 환산하므로 여기서 이미 gram 인 값에는 'G' 다.
        quantity: quantityGram,
        unit: 'G',
        // 이 값은 **희망**이고 확정이 아니다. 실제로 어느 게이트웨이가 청구했는지는 서버가
        // 승인 시점에 덮어쓴다 (PaymentService#approve) — 키가 없으면 Mock 이 붙기 때문에
        // 화면이 단정할 수 없다. 그래서 여기서는 기본 경로인 카카오페이를 보낸다.
        paymentMethod: 'KAKAO_PAY',
        receiverName: values.receiverName.trim(),
        receiverPhone: values.receiverPhone.trim(),
        zipCode: values.zipCode.trim(),
        address: values.address.trim(),
        // 상세 주소만 선택이다. 빈 값은 필드째 뺀다 — 공백을 주소의 일부로 저장할 이유가 없다.
        ...(values.addressDetail.trim() ? { addressDetail: values.addressDetail.trim() } : {}),
      },
      {
        // **구매 요청이 만들어지면 바로 체인 주문으로 이어간다.**
        //
        // 체인 주문은 결제와 무관하다 — 컨트랙트에 금액이 없고(정책 v3) 남는 것은
        // "누가 무엇을 얼마나 사기로 했는가" 뿐이다. 그래서 수량을 예약하는 이 시점이
        // 오프체인 예약과 짝이 맞는 자리다.
        //
        // 결제 복귀 화면에서 하지 않는 이유는 그때는 사용자가 아무것도 누르지 않은
        // 상태라, 지갑 팝업이 스스로 뜨는 것처럼 보이고 브라우저가 막을 수도 있다.
        //
        // 판매 글이 체인에 없으면(chainListingId 가 null) 부를 인자가 없어 건너뛴다.
        // 그 경우 거래 상세의 패널이 이어받는다.
        onSuccess: (trade) => {
          // 수량은 서버가 확정한 값(trade.quantity, gram)을 쓴다. 폼 입력을 다시 환산하면
          // 서버가 반올림한 값과 어긋날 수 있고, 체인과 DB 가 다른 수량을 갖게 된다.
          //
          // 지역 변수로 받아 좁힌다 — 클로저 안에서는 프로퍼티 접근이 좁혀지지 않는다.
          const chainListingId = listing.chainListingId
          if (chainListingId === null) {
            onCreated?.(trade.tradeId)
            return
          }
          void chain
            .order(trade.tradeId, chainListingId, trade.quantity)
            .finally(() => onCreated?.(trade.tradeId))
        },
      },
    )
  })

  return (
    <form onSubmit={onSubmit} noValidate className="purchase-form">
      <Controller
        control={control}
        name="quantityKg"
        render={({ field }) => (
          <QuantityInput
            label="구매 수량"
            unit="KG"
            value={field.value}
            onChange={field.onChange}
            min={0}
            max={listing.availableQuantity / 1000}
            disabled={listing.status !== 'ON_SALE'}
            error={
              exceedsAvailable
                ? `판매 가능 수량(${formatKg(listing.availableQuantity)})을 넘었습니다.`
                : undefined
            }
          />
        )}
      />

      <dl className="purchase-form__summary">
        <div>
          <dt>kg당 단가</dt>
          <dd>{formatWon(listing.pricePerKg)}</dd>
        </div>
        <div>
          <dt>결제 예정 금액</dt>
          <dd className="purchase-form__total">{formatWon(previewTotal)}</dd>
        </div>
      </dl>

      {/* 길이 제한은 서버 검증과 같은 값이다. 여기서 막지 않으면 다 입력하고 제출한
          뒤에야 400 을 본다. */}
      <fieldset className="purchase-form__shipping">
        <legend>배송지</legend>

        <Input
          label="수취인"
          autoComplete="name"
          error={errors.receiverName?.message}
          {...register('receiverName', {
            required: '수취인 이름을 입력해 주세요.',
            maxLength: { value: 100, message: '수취인 이름은 100자를 넘을 수 없습니다.' },
          })}
        />

        <Input
          label="연락처"
          autoComplete="tel"
          placeholder="010-0000-0000"
          error={errors.receiverPhone?.message}
          {...register('receiverPhone', {
            required: '연락처를 입력해 주세요.',
            maxLength: { value: 20, message: '연락처는 20자를 넘을 수 없습니다.' },
          })}
        />

        <Input
          label="우편번호"
          autoComplete="postal-code"
          error={errors.zipCode?.message}
          {...register('zipCode', {
            required: '우편번호를 입력해 주세요.',
            maxLength: { value: 20, message: '우편번호는 20자를 넘을 수 없습니다.' },
          })}
        />

        <Input
          label="주소"
          autoComplete="street-address"
          error={errors.address?.message}
          {...register('address', {
            required: '주소를 입력해 주세요.',
            maxLength: { value: 255, message: '주소는 255자를 넘을 수 없습니다.' },
          })}
        />

        <Input
          label="상세 주소"
          hint="선택 입력입니다."
          error={errors.addressDetail?.message}
          {...register('addressDetail', {
            maxLength: { value: 255, message: '상세 주소는 255자를 넘을 수 없습니다.' },
          })}
        />
      </fieldset>

      {/* 구매 요청과 결제는 별개다. 요청 시점에 수량이 예약되고, 결제는 그다음이다.
          이 순서를 모르면 "결제도 안 했는데 왜 수량이 줄었지" 로 읽힌다. */}
      <p className="purchase-form__note">
        구매를 요청하면 해당 수량이 예약되고, 이어서 결제를 진행합니다.
      </p>

      {isError && <ErrorMessage error={error} />}

      {chain.error && !chain.isRejected && <ErrorMessage error={chain.error} />}

      <Button
        type="submit"
        disabled={!canSubmit}
        loading={isPending || chain.isBusy}
        fullWidth
      >
        {chain.status === 'SIGNING'
          ? '지갑에서 서명해 주세요'
          : chain.status === 'PENDING'
            ? '블록 확정을 기다리는 중…'
            : chain.status === 'LINKING'
              ? '온체인 결과를 기록하는 중…'
              : listing.status === 'ON_SALE'
                ? '구매 요청'
                : '판매가 종료된 소재입니다'}
      </Button>
    </form>
  )
}
