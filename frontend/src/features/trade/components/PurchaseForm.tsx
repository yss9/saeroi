import { useState } from 'react'
import { Button, ErrorMessage } from '../../../components/common/index.ts'
import { QuantityInput } from '../../../components/common/QuantityInput.tsx'
import { calculateTotalPrice, formatKg, formatWon, kgToGram } from '../../../utils/quantity.ts'
import type { Listing } from '../../marketplace/types/marketplace.types.ts'
import { useCreateTrade } from '../hooks/useCreateTrade.ts'

export interface PurchaseFormProps {
  listing: Listing
  onCreated?: (tradeId: number) => void
}

/**
 * 부분 구매 폼 (Brand Market).
 *
 * 정책 07.1: 판매글의 **일부만 살 수 있다.** 30kg 짜리에서 10kg 만 사면
 * 판매글은 20kg 남은 채로 유지된다.
 *
 * 수량 입력에 useState 를 쓰는 이유 — 필드가 하나뿐이고 실시간으로 금액을 다시
 * 계산해야 한다. React Hook Form 은 필드가 여럿이고 검증이 복잡할 때 유리하다.
 */
export function PurchaseForm({ listing, onCreated }: PurchaseFormProps) {
  const [quantityKg, setQuantityKg] = useState<number | null>(null)
  const { mutate, isPending, isError, error } = useCreateTrade()

  const quantityGram = quantityKg === null ? 0 : kgToGram(quantityKg)
  const exceedsAvailable = quantityGram > listing.availableQuantity
  const isEmpty = quantityGram <= 0

  // 화면에 보여주는 금액은 **미리보기** 다. 실제 금액은 서버가 Listing 단가로 계산한다.
  // 클라이언트가 금액을 보내면 조작해서 싸게 살 수 있다.
  const previewTotal = calculateTotalPrice(quantityGram, listing.pricePerKg)

  const canSubmit = !isEmpty && !exceedsAvailable && listing.status === 'ON_SALE'

  function submit() {
    if (!canSubmit) return
    mutate(
      // 단위를 함께 보낸다. 서버가 gram 으로 환산하므로 여기서 이미 gram 인 값에는 'G' 다.
      // 결제 수단은 PG 연동 전이라 MOCK 이다 — 스키마의 ENUM 에 있는 값이고,
      // 연동되면 KAKAO_PAY 로 바뀐다.
      { listingId: listing.listingId, quantity: quantityGram, unit: 'G', paymentMethod: 'MOCK' },
      { onSuccess: (trade) => onCreated?.(trade.tradeId) },
    )
  }

  return (
    <div className="purchase-form">
      <QuantityInput
        label="구매 수량"
        unit="KG"
        value={quantityKg}
        onChange={setQuantityKg}
        min={0}
        max={listing.availableQuantity / 1000}
        disabled={listing.status !== 'ON_SALE'}
        error={exceedsAvailable ? `판매 가능 수량(${formatKg(listing.availableQuantity)})을 넘었습니다.` : undefined}
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

      {/* 구매 요청과 결제는 별개다. 요청 시점에 수량이 예약되고, 결제는 그다음이다.
          이 순서를 모르면 "결제도 안 했는데 왜 수량이 줄었지" 로 읽힌다. */}
      <p className="purchase-form__note">
        구매를 요청하면 해당 수량이 예약되고, 이어서 결제를 진행합니다.
      </p>

      {isError && <ErrorMessage error={error} />}

      <Button onClick={submit} disabled={!canSubmit} loading={isPending} fullWidth>
        {listing.status === 'ON_SALE' ? '구매 요청' : '판매가 종료된 소재입니다'}
      </Button>
    </div>
  )
}
