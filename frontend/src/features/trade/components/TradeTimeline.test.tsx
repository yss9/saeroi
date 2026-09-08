// 거래 진행 타임라인.
//
// 이 컴포넌트가 답해야 하는 것은 둘이다 — **어디까지 왔는가**, 그리고 **지금 누구
// 차례인가**. 상태 배지만으로는 후자를 알 수 없어서 만든 화면이다.
//
// 틀리면 안 되는 지점:
//
//   1) **취소된 거래를 "대기" 로 보여주지 않는다.** 기다리면 진행될 것처럼 읽히면
//      사용자가 오지 않을 발송을 계속 기다린다.
//   2) **취소 후에도 실제로 일어난 일은 지운다고 없어지지 않는다.** 결제 후 취소된
//      거래의 결제를 "진행되지 않음" 으로 적으면 거래 이력이 거짓이 된다.
//   3) **상태가 넘어갔는데 시각이 비어 있는 거래**(배송지 기능 이전 데이터)가 실제로
//      있다. 시각으로 진행을 판정하면 그런 거래가 영원히 "진행 중" 으로 남는다.

import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { Trade } from '../types/trade.types.ts'
import { TradeTimeline, type TradeViewer } from './TradeTimeline.tsx'

function trade(overrides: Partial<Trade> = {}): Trade {
  return {
    tradeId: 7,
    tradeCode: 'TRD-000007',
    chainTradeId: null,
    listingId: 3,
    materialId: 1,
    materialCode: 'MAT-000001',
    materialName: '세척된 폐데님',
    sellerId: 2,
    sellerName: '수거업체 A',
    buyerId: 5,
    buyerName: '브랜드 B',
    sellerWallet: '0x1111111111111111111111111111111111111111',
    buyerWallet: '0x2222222222222222222222222222222222222222',
    quantity: 12_000,
    pricePerKg: 4_200,
    totalPrice: 50_400,
    paymentMethod: 'MOCK',
    paymentReference: null,
    paymentAmount: 0,
    status: 'CREATED',
    createdAt: '2026-09-01T10:00:00',
    paidAt: null,
    shippedAt: null,
    receivedAt: null,
    buyerSignedAt: null,
    completedAt: null,
    canceledAt: null,
    cancelReason: null,
    shipping: null,
    ...overrides,
  }
}

const PAID_AT = '2026-09-02T11:30:00'

/**
 * 기대하는 표기를 **런타임에 계산한다.**
 *
 * `'26. 9. 2. 오전 11:30'` 처럼 박아 두면 ICU 데이터에 묶인 테스트가 된다 — Node 가
 * small-icu 로 빌드된 환경(CI 에이전트가 그럴 수 있다)에서는 ko-KR 표기가 나오지 않고,
 * ICU 버전이 올라가면 구분점 사이의 공백 같은 세부가 바뀐다. 로컬에서만 초록인 테스트가
 * 된다.
 *
 * 그래서 형식이 아니라 **"변환을 거쳤는가"** 를 본다. 형식 자체는
 * `formatTradeDateTime` 의 책임이다 (BrandProcessingRequest.test 도 같은 방식이다).
 */
function localized(value: string): string {
  return new Date(value).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
}

function renderTimeline(value: Trade, viewer?: TradeViewer) {
  render(<TradeTimeline trade={value} viewer={viewer} />)
}

/** 단계 하나를 이름으로 찾는다. 상태는 li 의 class 로 드러난다. */
function step(label: string): HTMLElement {
  const item = screen.getByText(label).closest('li')
  if (item === null) throw new Error(`단계를 찾을 수 없습니다: ${label}`)
  return item
}

afterEach(cleanup)

describe('TradeTimeline', () => {
  it('진행된 단계와 현재 단계를 구분한다', () => {
    renderTimeline(trade({ status: 'PAID', paidAt: '2026-09-02T11:30:00' }))

    expect(step('구매 요청').className).toContain('--done')
    expect(step('결제 완료').className).toContain('--done')
    // 결제까지 끝났으면 다음은 발송이다.
    expect(step('발송').className).toContain('--current')
    expect(step('수령 확인').className).toContain('--pending')
  })

  it('진행 중인 단계를 보조기술에 알린다', () => {
    renderTimeline(trade({ status: 'PAID', paidAt: '2026-09-02T11:30:00' }))

    // 색과 굵기만으로 구분하면 화면을 못 보는 사용자에게는 네 항목이 똑같이 읽힌다.
    expect(step('발송')).toHaveAttribute('aria-current', 'step')
    expect(step('수령 확인')).not.toHaveAttribute('aria-current')
  })

  it('끝난 단계의 시각을 사용자 시간대로 보여준다', () => {
    renderTimeline(trade({ status: 'PAID', paidAt: PAID_AT }))

    const paid = within(step('결제 완료'))
    const rendered = paid.getByText(localized(PAID_AT))

    expect(rendered.tagName).toBe('TIME')
    // 기계가 읽는 값은 원본으로 남기고, 사람이 읽는 자리에만 변환된 값을 둔다.
    expect(rendered).toHaveAttribute('dateTime', PAID_AT)
    expect(paid.queryByText(PAID_AT)).not.toBeInTheDocument()
  })

  it('발송 단계에 송장을 함께 보여준다', () => {
    renderTimeline(
      trade({
        status: 'SHIPPED',
        paidAt: '2026-09-02T11:30:00',
        shippedAt: '2026-09-03T09:00:00',
        shipping: {
          receiverName: '브랜드 B 자재팀',
          receiverPhone: '010-0000-0000',
          zipCode: '06236',
          address: '서울 강남구 테헤란로 1',
          addressDetail: null,
          carrier: 'CJ대한통운',
          trackingNumber: '123456789012',
          shippedAt: '2026-09-03T09:00:00',
          deliveredAt: null,
        },
      }),
    )

    expect(within(step('발송')).getByText('CJ대한통운 · 123456789012')).toBeInTheDocument()
  })

  it('송장 없이 전달한 발송은 송장 줄을 만들지 않는다', () => {
    renderTimeline(trade({ status: 'SHIPPED', shippedAt: '2026-09-03T09:00:00' }))

    // 송장 없는 전달도 정상이다. 빈 줄을 두면 값이 누락된 것처럼 보인다.
    expect(within(step('발송')).queryByText('·')).not.toBeInTheDocument()
  })

  it('완료된 거래는 모든 단계가 끝나고 다음 할 일이 없다', () => {
    renderTimeline(
      trade({
        status: 'COMPLETED',
        paidAt: '2026-09-02T11:30:00',
        shippedAt: '2026-09-03T09:00:00',
        receivedAt: '2026-09-05T14:00:00',
        buyerSignedAt: '2026-09-05T14:00:00',
        completedAt: '2026-09-05T14:00:00',
      }),
      'BUYER',
    )

    for (const label of ['구매 요청', '결제 완료', '발송', '수령 확인']) {
      expect(step(label).className).toContain('--done')
    }
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  // 수령 확인 요청 하나가 RECEIVED 를 거쳐 COMPLETED 까지 끝낸다. 그 사이 상태로
  // 화면에 남는 거래는 없지만, 응답을 그대로 받은 직후에는 RECEIVED 로 보일 수 있다.
  it('RECEIVED 도 완료로 본다', () => {
    renderTimeline(trade({ status: 'RECEIVED', receivedAt: '2026-09-05T14:00:00' }))

    expect(step('수령 확인').className).toContain('--done')
  })

  describe('취소된 거래', () => {
    it('남은 단계를 대기가 아니라 진행되지 않음으로 표시한다', () => {
      renderTimeline(trade({ status: 'CANCELED' }))

      expect(step('발송').className).toContain('--stopped')
      expect(step('수령 확인').className).toContain('--stopped')
      expect(within(step('발송')).getByText('진행되지 않음')).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent('취소되었습니다')
    })

    it('취소 전에 끝난 결제는 그대로 남긴다', () => {
      // 결제 후 취소된 거래다. 결제는 실제로 일어났고, 그 사실은 취소로 사라지지 않는다.
      renderTimeline(trade({ status: 'CANCELED', paidAt: '2026-09-02T11:30:00' }))

      expect(step('결제 완료').className).toContain('--done')
      expect(within(step('결제 완료')).getByText(localized(PAID_AT))).toBeInTheDocument()
    })

    it('취소 시각과 사유를 함께 보여준다', () => {
      renderTimeline(
        trade({
          status: 'CANCELED',
          canceledAt: '2026-09-02T15:00:00',
          cancelReason: '재고 부족으로 발송이 어렵습니다',
        }),
      )

      const notice = screen.getByRole('status')
      expect(notice).toHaveTextContent(localized('2026-09-02T15:00:00'))
      expect(notice).toHaveTextContent('사유: 재고 부족으로 발송이 어렵습니다')
    })

    // 사유 없는 취소가 정상 경로다. "사유 없음" 같은 빈 자리를 만들면 뭔가 누락된
    // 것처럼 읽힌다.
    it('사유가 없으면 사유 줄을 만들지 않는다', () => {
      renderTimeline(trade({ status: 'CANCELED', canceledAt: '2026-09-02T15:00:00' }))

      expect(screen.getByRole('status')).not.toHaveTextContent('사유')
    })

    it('다음 할 일을 안내하지 않는다', () => {
      renderTimeline(trade({ status: 'CANCELED' }), 'SELLER')

      expect(screen.getByRole('status')).toHaveTextContent('취소되었습니다')
      expect(screen.queryByText(/기다리는 중/)).not.toBeInTheDocument()
    })
  })

  describe('다음 할 일', () => {
    it('판매자에게는 발송을, 구매자에게는 기다림을 말한다', () => {
      const paid = trade({ status: 'PAID', paidAt: '2026-09-02T11:30:00' })

      renderTimeline(paid, 'SELLER')
      expect(screen.getByRole('status')).toHaveTextContent('발송 처리를 해주세요')

      cleanup()
      renderTimeline(paid, 'BUYER')
      expect(screen.getByRole('status')).toHaveTextContent('판매자의 발송을 기다리는 중')
    })

    it('수령 확인 단계에서는 소유권 이전을 알린다', () => {
      const shipped = trade({ status: 'SHIPPED', shippedAt: '2026-09-03T09:00:00' })

      renderTimeline(shipped, 'BUYER')
      expect(screen.getByRole('status')).toHaveTextContent('12 kg의 소유권이 이전됩니다')

      cleanup()
      renderTimeline(shipped, 'SELLER')
      expect(screen.getByRole('status')).toHaveTextContent('구매자의 수령 확인을 기다리는 중')
    })

    it('당사자가 아니면 안내하지 않는다', () => {
      renderTimeline(trade({ status: 'PAID', paidAt: '2026-09-02T11:30:00' }))

      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    })
  })

  // 배송지 기능 이전 데이터처럼 상태는 넘어갔는데 시각이 비어 있는 거래가 있다.
  // 시각으로 진행을 판정하면 이런 거래가 영원히 "진행 중" 으로 남는다.
  it('시각이 없어도 상태로 진행을 판정한다', () => {
    renderTimeline(trade({ status: 'SHIPPED', paidAt: null, shippedAt: null }))

    expect(step('결제 완료').className).toContain('--done')
    expect(step('발송').className).toContain('--done')
    expect(step('수령 확인').className).toContain('--current')
    // 값이 없는 것과 0 을 구분한다.
    expect(within(step('결제 완료')).getByText('-')).toBeInTheDocument()
  })
})
