// 브랜드 제품 관리.
//
// 이 화면이 메우는 구멍은 하나다 — **등록한 제품의 판매를 시작할 곳이 없었다.** 등록은
// 언제나 DRAFT 로 저장되고 판매 시작은 별도 요청인데, 그 버튼을 놓을 자리가 없었다.
//
// 틀리면 안 되는 지점 넷.
//
//   1) **DRAFT 를 보여준다.** 마켓 목록과 반대다. 준비 중인 제품이 안 보이면 판매 시작
//      버튼을 놓을 자리가 없다.
//   2) **재고 0 을 품절로 뭉치지 않는다.** 등록만 된 제품의 0 은 "아직 만들지 않았다" 다.
//   3) **재고 0 이면 판매 시작을 내주지 않는다.** 서버가 PRODUCT_MODEL_HAS_NO_STOCK 로
//      거절하고, 개체 없는 제품이 마켓에 걸리면 들어오자마자 살 수 없다.
//   4) **단종은 확인을 거친다.** 되돌릴 수 없고 다시 팔려면 새로 등록해야 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductModel } from '../types/product.types.ts'
import { BrandProductList } from './BrandProductList.tsx'

const api = vi.hoisted(() => ({
  getMyProductModels: vi.fn(),
  changeProductModelStatus: vi.fn(),
}))
vi.mock('../api/getMyProductModels.ts', () => ({
  getMyProductModels: api.getMyProductModels,
}))
vi.mock('../api/changeProductModelStatus.ts', () => ({
  changeProductModelStatus: api.changeProductModelStatus,
}))

function product(overrides: Partial<ProductModel> = {}): ProductModel {
  return {
    productModelId: 31,
    name: '업사이클 데님 토트백',
    category: 'BAG',
    brandId: 5,
    price: 129_000,
    description: '폐데님으로 만든 토트백',
    thumbnailUrl: 'product-images/2026/09/abc.png',
    status: 'DRAFT',
    stockQuantity: 0,
    createdAt: '2026-09-08T10:00:00',
    ...overrides,
  }
}

function renderList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <BrandProductList />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 제품 한 줄. 이름으로 찾는다. */
function row(name: string): HTMLElement {
  const item = screen.getByText(name).closest('li')
  if (item === null) throw new Error(`제품 줄을 찾을 수 없습니다: ${name}`)
  return item
}

beforeEach(() => {
  api.getMyProductModels.mockResolvedValue([product()])
  api.changeProductModelStatus.mockResolvedValue(product({ status: 'ON_SALE', stockQuantity: 10 }))
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('BrandProductList', () => {
  it('등록만 된 제품도 보여준다', async () => {
    renderList()

    // 마켓 목록은 ON_SALE 만 담지만 이쪽은 DRAFT 를 포함한다.
    expect(await screen.findByText('업사이클 데님 토트백')).toBeInTheDocument()
    expect(screen.getByText('판매 준비')).toBeInTheDocument()
    expect(screen.getByText(/가방 · 129,000원/)).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('재고가 없으면 판매 시작을 내주지 않고 무엇을 해야 하는지 알린다', async () => {
    renderList()
    await screen.findByText('업사이클 데님 토트백')

    const item = row('업사이클 데님 토트백')
    expect(within(item).queryByRole('button', { name: '판매 시작' })).not.toBeInTheDocument()
    // 비활성 버튼은 이유를 말하지 않는다. 생산으로 데려간다.
    expect(within(item).getByRole('link', { name: '생산해야 판매 시작 가능' })).toHaveAttribute(
      'href',
      '/brand/production',
    )
    expect(api.changeProductModelStatus).not.toHaveBeenCalled()
  })

  // 등록만 된 제품의 0 은 "다 팔렸다" 가 아니다.
  it('생산 전 재고 0 과 품절을 구분한다', async () => {
    api.getMyProductModels.mockResolvedValue([
      product({ productModelId: 31, name: '준비 중 가방', status: 'DRAFT', stockQuantity: 0 }),
      product({ productModelId: 32, name: '다 팔린 가방', status: 'SOLD_OUT', stockQuantity: 0 }),
    ])
    renderList()

    // DRAFT: "아직 만들지 않았다". SOLD_OUT: 배지가 품절을 말하고 재고 줄은 수량만 말한다.
    expect(await screen.findByText(/생산 전 · 판매 가능 수량 0개/)).toBeInTheDocument()
    const soldOut = row('다 팔린 가방')
    expect(within(soldOut).getByText('품절')).toBeInTheDocument()
    expect(within(soldOut).getByText('판매 가능 수량 0개')).toBeInTheDocument()
  })

  it('재고가 있으면 판매를 시작한다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ stockQuantity: 10 })])
    const user = renderList()

    await user.click(await screen.findByRole('button', { name: '판매 시작' }))

    await waitFor(() =>
      expect(api.changeProductModelStatus).toHaveBeenCalledWith(31, 'ON_SALE'),
    )
  })

  it('판매 가능 수량을 보여준다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ status: 'ON_SALE', stockQuantity: 7 })])
    renderList()

    expect(await screen.findByText('판매 가능 7개')).toBeInTheDocument()
  })

  describe('판매 중단', () => {
    it('확인을 거친 뒤에만 단종 처리한다', async () => {
      api.getMyProductModels.mockResolvedValue([product({ status: 'ON_SALE', stockQuantity: 10 })])
      const user = renderList()

      await user.click(await screen.findByRole('button', { name: '판매 중단' }))
      // 되돌릴 수 없으므로 확인 없이 요청이 나가면 안 된다.
      expect(api.changeProductModelStatus).not.toHaveBeenCalled()
      expect(screen.getByText(/되돌릴 수 없고, 다시 팔려면 제품을 새로 등록/)).toBeInTheDocument()

      const [, confirm] = screen.getAllByRole('button', { name: '판매 중단' })
      await user.click(confirm)

      await waitFor(() =>
        expect(api.changeProductModelStatus).toHaveBeenCalledWith(31, 'DISCONTINUE'),
      )
    })

    it('확인 모달을 닫으면 단종하지 않는다', async () => {
      api.getMyProductModels.mockResolvedValue([product({ status: 'ON_SALE', stockQuantity: 10 })])
      const user = renderList()

      await user.click(await screen.findByRole('button', { name: '판매 중단' }))
      await user.click(screen.getByRole('button', { name: '취소' }))

      expect(api.changeProductModelStatus).not.toHaveBeenCalled()
    })

    it('이미 단종된 제품은 되돌릴 수 없다고 알린다', async () => {
      api.getMyProductModels.mockResolvedValue([product({ status: 'DISCONTINUED' })])
      renderList()

      expect(await screen.findByText('되돌릴 수 없습니다')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '판매 중단' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '판매 시작' })).not.toBeInTheDocument()
    })
  })

  it('상태 변경이 실패하면 사유를 보여준다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ stockQuantity: 10 })])
    api.changeProductModelStatus.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'PRODUCT_MODEL_HAS_NO_STOCK',
        message: '판매할 수 있는 개체가 없습니다.',
        status: 409,
      }),
    )
    const user = renderList()

    await user.click(await screen.findByRole('button', { name: '판매 시작' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('판매할 수 있는 개체가 없습니다')
  })

  it('등록한 제품이 없으면 등록으로 데려간다', async () => {
    api.getMyProductModels.mockResolvedValue([])
    renderList()

    expect(await screen.findByText('등록한 제품이 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '제품 등록하기' })).toHaveAttribute(
      'href',
      '/brand/products/new',
    )
  })

  it('조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getMyProductModels.mockRejectedValue(
      createAppError(ERROR_KIND.NETWORK_ERROR, { message: '서버에 연결할 수 없습니다.' }),
    )
    renderList()

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
