// 부분 구매 폼 검증.
//
// 이 도메인에서 사고가 나면 안 되는 지점은 하나다 — **판매 가능 수량 초과 구매**.
// 넘겨서 요청하면 서버가 거절하지만, 그 전에 화면이 막아야 사용자가 결제까지 갔다가
// 실패하는 일이 없다.
//
// 금액 계산도 함께 본다. 화면에 보인 금액과 실제 결제 금액이 다르면 신뢰가 깨진다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import type { ListingDetail } from '../../marketplace/types/marketplace.types.ts'
import { PurchaseForm } from './PurchaseForm.tsx'

const LISTING: ListingDetail = {
  id: 1,
  materialId: 1,
  materialCode: 'MAT-000001',
  materialName: '세척된 폐데님',
  materialType: 'DENIM',
  rootOrigin: 'WASTE',
  grade: 'A',
  totalQuantity: 30_000,
  availableQuantity: 12_000, // 12kg 남음
  pricePerKg: 4_200,
  status: 'ON_SALE',
  sellerId: 2,
  sellerName: '수거업체 A',
  createdAt: '2026-09-01T00:00:00.000Z',
  description: null,
  materialLockId: 9001,
  canceledAt: null,
  cancelReason: null,
}

function renderForm(listing: ListingDetail = LISTING) {
  // 테스트마다 새 QueryClient 를 쓴다. 캐시가 테스트 사이에 새면 순서에 따라 결과가 달라진다.
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PurchaseForm listing={listing} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(cleanup)

describe('PurchaseForm', () => {
  it('수량을 입력하기 전에는 구매할 수 없다', () => {
    renderForm()
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeDisabled()
  })

  it('입력한 수량으로 결제 금액을 계산한다', async () => {
    const user = userEvent.setup()
    renderForm()

    // 10kg × 4,200원/kg = 42,000원
    await user.type(screen.getByLabelText('구매 수량'), '10')

    expect(screen.getByText('42,000원')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeEnabled()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('판매 가능 수량을 넘으면 막고 이유를 알린다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('구매 수량'), '13') // 12kg 만 남았다

    expect(screen.getByRole('alert')).toHaveTextContent('판매 가능 수량')
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeDisabled()
  })

  it('남은 수량과 정확히 같으면 구매할 수 있다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('구매 수량'), '12')

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeEnabled()
  })

  // 소수점 수량은 gram 변환에서 부동소수점 오차가 날 수 있는 지점이다.
  it('소수점 수량도 정확히 계산한다', async () => {
    const user = userEvent.setup()
    renderForm()

    // 2.3kg → 2300g × 4,200원/kg = 9,660원
    await user.type(screen.getByLabelText('구매 수량'), '2.3')

    expect(screen.getByText('9,660원')).toBeInTheDocument()
  })

  it('판매가 끝난 소재는 구매 버튼이 잠긴다', () => {
    renderForm({ ...LISTING, status: 'SOLD_OUT', availableQuantity: 0 })
    expect(screen.getByRole('button', { name: '판매가 종료된 소재입니다' })).toBeDisabled()
  })
})
