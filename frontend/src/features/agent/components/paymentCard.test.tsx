import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { PaymentCard } from './PaymentCard.tsx'
import type { PendingAction } from '../types/agent.types.ts'

/**
 * 결제 카드는 **무엇을, 어디로 받는지**까지 보인다.
 *
 * 받는 분과 주소는 모델이 대화에서 받아 채운 값이다. 카드에 수량과 금액만 있던 동안 담당자는
 * 어디로 배송되는지 확인하지 못한 채 결제했다 — 모델이 기본 배송지를 조회하지 않고 지어내도
 * 알아챌 자리가 없었다.
 */
describe('PaymentCard', () => {
  afterEach(cleanup)

  it('소재 구매 카드에 소재와 받는 분과 배송지를 보인다', () => {
    renderCard({
      listingId: 21,
      quantityGram: 12_500,
      totalPrice: 25_000,
      materialName: '폐데님 원단',
      receiverName: '김담당',
      receiverPhone: '010-1234-5678',
      zipCode: '06236',
      address: '서울 강남구 테헤란로 212',
      addressDetail: '12층',
    })

    expect(screen.getByText('폐데님 원단')).toBeTruthy()
    expect(screen.getByText('김담당 · 010-1234-5678')).toBeTruthy()
    expect(screen.getByText('(06236) 서울 강남구 테헤란로 212 12층')).toBeTruthy()
  })

  it('누구에게서 무엇을 사는지 판매자·등급·출처·단가를 보인다', () => {
    // 판매 글이 한 곳뿐이자 모델이 판매자와 등급을 말하지 않았다. 카드에는 늘 있어야 한다.
    renderCard({
      listingId: 21,
      quantityGram: 10_000,
      totalPrice: 20_000,
      materialName: '폐데님 원단',
      sellerName: '김아줌마',
      grade: 'B',
      originText: '폐소재',
      pricePerKg: 2_000,
    })

    expect(screen.getByText('김아줌마')).toBeTruthy()
    expect(screen.getByText('B등급 · 폐소재 · kg당 2,000원')).toBeTruthy()
  })

  it('배송지가 없는 가공비 카드에는 배송 줄을 그리지 않는다', () => {
    renderCard({ jobId: 6, fee: 30_000 }, 'pay_processing_fee')

    expect(screen.queryByText('받는 분')).toBeNull()
    expect(screen.queryByText('배송지')).toBeNull()
  })
})

function renderCard(payload: object, toolName = 'purchase_material') {
  const action: PendingAction = {
    id: 1,
    messageId: 1,
    actionType: 'PAYMENT',
    toolName,
    summary: '테스트',
    payloadJson: JSON.stringify(payload),
    resultJson: null,
    status: 'PENDING',
    stepTotal: 1,
    stepDone: 0,
    expiresAt: '2999-01-01T00:00:00',
  }
  render(
    <QueryClientProvider client={new QueryClient()}>
      <PaymentCard action={action} />
    </QueryClientProvider>,
  )
}
