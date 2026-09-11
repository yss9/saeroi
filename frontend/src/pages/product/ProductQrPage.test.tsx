// 제품의 QR 라벨 화면.
//
// **제품 관리 화면에서 오는 입구다.** QR 은 개체마다 붙고 개체는 생산 배치가 만들지만,
// 브랜드의 일은 "이 제품 라벨을 뽑는다" 로 시작한다. 이 화면이 그 사이를 잇는다.
//
// 고정하는 것 넷.
//
//   1) **그 제품의 확정된 배치만 고른다.** 다른 제품의 배치나 작성 중인 배치를 집으면
//      물건과 다른 이력을 가리키는 라벨을 인쇄한다.
//   2) **가장 최근 확정분을 먼저 본다.** 라벨을 뽑는 대상은 거의 항상 방금 생산한 배치다.
//   3) **라벨이 없는 것과 실패를 구분한다** (ARCHITECTURE §8). 생산 전이면 할 수 있는 일
//      (생산 관리)로 보낸다.
//   4) **내 배치만 본다.** 남의 제품 번호를 주소에 넣어도 개체나 토큰이 새지 않는다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductionBatch } from '../../features/production/types/production.types.ts'
import type { ProductModel } from '../../features/product/types/product.types.ts'
import { ProductQrPage } from './ProductQrPage.tsx'

const api = vi.hoisted(() => ({
  getProductModel: vi.fn(),
  getMyProductionBatches: vi.fn(),
  getProductionItems: vi.fn(),
  getProductionItemQr: vi.fn(),
}))
vi.mock('../../features/product/api/getProductModel.ts', () => ({
  getProductModel: api.getProductModel,
}))
vi.mock('../../features/production/api/productionBatchApi.ts', () => ({
  getMyProductionBatches: api.getMyProductionBatches,
  getProductionItems: api.getProductionItems,
  getProductionItemQr: api.getProductionItemQr,
}))

const PRODUCT_ID = 31

function product(): ProductModel {
  return {
    productModelId: PRODUCT_ID,
    name: 'RE:BAG',
    category: 'BAG',
    brandId: 5,
    price: 129_000,
    description: '폐데님으로 만든 가방입니다.',
    thumbnailUrl: 'key.jpg',
    status: 'ON_SALE',
    stockQuantity: 10,
    createdAt: '2026-09-01T10:00:00',
  }
}

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 7,
    batchCode: 'PB-000007',
    batchName: '2026 가을 1차',
    productModelId: PRODUCT_ID,
    producedQuantity: 2,
    producerId: 5,
    producerWallet: '0x2222222222222222222222222222222222222222',
    chainBatchId: null,
    status: 'CONFIRMED',
    totalUsedQuantity: 8_000,
    virginUsedQuantity: 0,
    upcycledUsedQuantity: 8_000,
    upcycleRatio: 100,
    evidenceHash: null,
    manifestUrl: null,
    createdAt: '2026-09-05T10:00:00',
    confirmedAt: '2026-09-05T12:00:00',
    inputs: [],
    ...overrides,
  }
}

function renderPage(path = `/brand/products/${PRODUCT_ID}/qr`) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [{ path: '/brand/products/:productModelId/qr', element: <ProductQrPage /> }],
    { initialEntries: [path] },
  )
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getProductModel.mockResolvedValue(product())
  api.getMyProductionBatches.mockResolvedValue([batch()])
  api.getProductionItems.mockResolvedValue([])
  api.getProductionItemQr.mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductQrPage', () => {
  it('제품 이름을 제목에 넣는다', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: /RE:BAG/ })).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('그 제품의 확정된 배치만 고른다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      // 다른 제품의 배치.
      batch({ productionBatchId: 5, productModelId: 99 }),
      // 같은 제품이지만 작성 중 — 개체가 아직 없다.
      batch({ productionBatchId: 6, status: 'DRAFT', confirmedAt: null }),
      batch({ productionBatchId: 7 }),
    ])
    renderPage()

    await waitFor(() => expect(api.getProductionItems).toHaveBeenCalledWith(7))
    expect(api.getProductionItems).toHaveBeenCalledTimes(1)
  })

  // 라벨을 뽑는 대상은 거의 항상 방금 생산한 배치다.
  it('확정이 가장 최근인 배치를 먼저 보여준다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ productionBatchId: 7, confirmedAt: '2026-09-05T12:00:00' }),
      batch({ productionBatchId: 8, batchCode: 'PB-000008', confirmedAt: '2026-09-09T12:00:00' }),
    ])
    renderPage()

    await waitFor(() => expect(api.getProductionItems).toHaveBeenCalledWith(8))
  })

  it('배치가 여럿이면 골라 볼 수 있다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ productionBatchId: 7, confirmedAt: '2026-09-05T12:00:00' }),
      batch({ productionBatchId: 8, batchCode: 'PB-000008', confirmedAt: '2026-09-09T12:00:00' }),
    ])
    const user = renderPage()
    await waitFor(() => expect(api.getProductionItems).toHaveBeenCalledWith(8))

    await user.selectOptions(await screen.findByLabelText('생산 배치'), '7')

    await waitFor(() => expect(api.getProductionItems).toHaveBeenCalledWith(7))
  })

  // 배치가 하나면 고를 것이 없지만 어느 배치인지는 알아야 한다 — 같은 제품을 여러 번
  // 생산하면 라벨은 배치마다 다른 개체를 가리킨다.
  it('배치가 하나면 고르게 하지 않고 어느 배치인지 적는다', async () => {
    renderPage()

    expect(await screen.findByText(/PB-000007/)).toBeInTheDocument()
    expect(screen.queryByLabelText('생산 배치')).not.toBeInTheDocument()
  })

  it('확정된 배치가 없으면 생산으로 보낸다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ status: 'DRAFT', confirmedAt: null }),
    ])
    renderPage()

    expect(await screen.findByText('아직 만들어진 QR 이 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '생산 관리로 이동' })).toHaveAttribute(
      'href',
      '/brand/production',
    )
    expect(api.getProductionItems).not.toHaveBeenCalled()
  })

  // 서버가 남의 배치를 주지 않으므로 목록이 비고, 개체나 토큰은 새지 않는다.
  it('남의 제품 번호를 넣으면 라벨이 없다고 답한다', async () => {
    renderPage('/brand/products/99/qr')

    expect(await screen.findByText('아직 만들어진 QR 이 없습니다')).toBeInTheDocument()
    expect(api.getProductionItems).not.toHaveBeenCalled()
  })

  it('배치 조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getMyProductionBatches.mockRejectedValue(new Error('network'))
    renderPage()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('주소의 제품 번호가 숫자가 아니면 조회하지 않는다', async () => {
    renderPage('/brand/products/abc/qr')

    expect(await screen.findByRole('alert')).toHaveTextContent('잘못된 주소입니다')
    expect(api.getProductModel).not.toHaveBeenCalled()
    expect(api.getProductionItems).not.toHaveBeenCalled()
  })

  it('내 제품 목록으로 돌아갈 길을 둔다', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /내 제품/ })).toHaveAttribute(
      'href',
      '/brand/products',
    )
  })
})
