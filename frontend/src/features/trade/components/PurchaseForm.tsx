import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { MagnifyingGlass } from '@phosphor-icons/react'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'
import { QuantityInput } from '../../../components/common/QuantityInput.tsx'
import { calculateTotalPrice, formatKg, formatWon, gramToKg, kgToGram } from '../../../utils/quantity.ts'
import type { Listing } from '../../marketplace/types/marketplace.types.ts'
import { useCompanyProfile } from '../../company/hooks/useCompanyProfile.ts'
import { useCreateTrade } from '../hooks/useCreateTrade.ts'
import { useTradeOnChain } from '../hooks/useTradeOnChain.ts'
import type { CreateTradeRequest, Trade } from '../types/trade.types.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import './trade.css'

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
    setValue,
    setFocus,
    getValues,
    formState: { errors, dirtyFields },
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

  const isBrand = useAuthStore((state) => state.user?.role === 'BRAND')
  const { profile } = useCompanyProfile(isBrand)

  // 구매 배송지는 보통 업체의 기본 배송지와 같다. 다만 사용자가 다른 곳으로 받도록
  // 이미 수정했다면, 늦게 도착한 프로필 응답이 그 선택을 덮어쓰면 안 된다.
  useEffect(() => {
    if (!profile) return

    const companyDefaults = [
      ['receiverName', profile.representativeName],
      ['receiverPhone', profile.companyPhone],
      ['zipCode', profile.zipCode],
      ['address', profile.address],
      ['addressDetail', profile.addressDetail ?? ''],
    ] as const

    for (const [field, value] of companyDefaults) {
      if (!dirtyFields[field] && !getValues(field) && value) {
        setValue(field, value)
      }
    }
  }, [dirtyFields, getValues, profile, setValue])

  const [addressSearchError, setAddressSearchError] = useState('')
  const [isSearchingAddress, setIsSearchingAddress] = useState(false)

  /**
   * 주소는 <b>검색으로만</b> 채운다. 가입·배송지 화면과 같은 판단이다 — 이 값이 택배
   * 송장에 인쇄되므로 오타가 그대로 배송 사고가 되고, 우편번호와 주소가 서로 다른 곳을
   * 가리켜도 폼은 그것을 알 수 없다.
   *
   * 이 화면만 직접 타이핑으로 남아 있었다. 소재를 사는 브랜드가 다른 화면과 다른 방식으로
   * 주소를 넣게 되고, 그쪽에서 걸러지던 실수가 여기서는 그대로 통과했다.
   */
  async function searchAddress() {
    setAddressSearchError('')
    setIsSearchingAddress(true)
    try {
      await openKakaoPostcode(({ zonecode, address: found }) => {
        setValue('zipCode', zonecode, { shouldDirty: true, shouldValidate: true })
        setValue('address', found, { shouldDirty: true, shouldValidate: true })
        setFocus('addressDetail')
      })
    } catch {
      setAddressSearchError('주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsSearchingAddress(false)
    }
  }

  const { mutateAsync, isPending, isError, error } = useCreateTrade()
  const chain = useTradeOnChain()
  const [pendingTrade, setPendingTrade] = useState<{
    created: Trade
    request: CreateTradeRequest
  } | null>(null)

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

  const onSubmit = handleSubmit(async (values) => {
    if (!canSubmit) return
    try {
      const request: CreateTradeRequest = pendingTrade?.request ?? {
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
      }
      const created = pendingTrade?.created ?? (await mutateAsync(request))
      if (pendingTrade === null) setPendingTrade({ created, request })

      const chainListingId = listing.chainListingId
      if (chainListingId === null) {
        onCreated?.(created.tradeId)
        return
      }

      // 서버가 확정한 gram 수량으로 주문한다. 연결까지 끝났을 때만 상세로 이동한다.
      const linked = await chain.order(created.tradeId, chainListingId, created.quantity)
      if (linked) {
        setPendingTrade(null)
        onCreated?.(created.tradeId)
      }
    } catch {
      // API와 지갑 오류는 각 훅의 오류 상태로 화면에 표시한다.
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="purchase-form">
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
          inputMode="numeric"
          autoComplete="postal-code"
          placeholder="06236"
          readOnly
          error={errors.zipCode?.message}
          {...register('zipCode', { required: '주소 검색으로 우편번호를 채워 주세요.' })}
        />

        <div className="purchase-form__address-search">
          <Input
            label="주소"
            autoComplete="street-address"
            placeholder="주소 검색을 이용해 주세요."
            readOnly
            error={errors.address?.message}
            {...register('address', {
              required: '주소를 검색해 주세요.',
              maxLength: { value: 255, message: '주소는 255자를 넘을 수 없습니다.' },
            })}
          />
          <button type="button" onClick={() => void searchAddress()} disabled={isSearchingAddress}>
            <MagnifyingGlass aria-hidden="true" />
            {isSearchingAddress ? '불러오는 중' : '주소 검색'}
          </button>
        </div>

        {/* 두 칸이 읽기 전용이라, 검색이 실패하면 손으로 메울 방법이 없다. */}
        {addressSearchError ? (
          <p className="purchase-form__address-error" role="alert">
            {addressSearchError}
          </p>
        ) : null}

        {/* 선택이라는 사실은 칸 안에서 말한다 — 칸 밑에 한 줄을 더 쓰면 그 줄 때문에
            옆 칸들과 높이가 어긋난다. */}
        <Input
          label="상세 주소"
          placeholder="선택 입력"
          error={errors.addressDetail?.message}
          {...register('addressDetail', {
            maxLength: { value: 255, message: '상세 주소는 255자를 넘을 수 없습니다.' },
          })}
        />
      </fieldset>

      {/* 얼마나 살지 · 얼마인지 · 요청 버튼. 셋을 한 칸에 이어 붙여 배송지와 위를 맞춘다. */}
      <div className="purchase-form__order">
        <div className="purchase-form__quantity">
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
                max={gramToKg(listing.availableQuantity)}
                disabled={listing.status !== 'ON_SALE'}
                error={
                  exceedsAvailable
                    ? `판매 가능 수량(${formatKg(listing.availableQuantity)})을 넘었습니다.`
                    : undefined
                }
              />
            )}
          />
        </div>

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

        {isError && <ErrorMessage error={error} />}

        {chain.error && !chain.isRejected && <ErrorMessage error={chain.error} />}

        <Button
          type="submit"
          disabled={!canSubmit}
          loading={isPending || chain.isBusy}
          fullWidth
        >
          {chain.status === 'SIGNING'
            ? '지갑에서 요청을 확인해 주세요'
            : chain.status === 'PENDING'
              ? '거래 이력을 저장하는 중…'
              : chain.status === 'LINKING'
                ? '저장 결과를 확인하는 중…'
                : listing.status === 'ON_SALE'
                  ? pendingTrade
                    ? '구매 요청 완료하기'
                    : '구매 요청'
                  : '판매가 종료된 소재입니다'}
        </Button>
      </div>
    </form>
  )
}
