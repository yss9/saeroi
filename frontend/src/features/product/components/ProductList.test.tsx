// 제품 마켓 목록.
//
// 틀리면 안 되는 지점 넷.
//
//   1) **준비 중인 제품(DRAFT)이 마켓에 뜨지 않는다.** status 를 보내지 않으면 서버가
//      ON_SALE 로 접는다 — 화면이 그 기본값을 깨뜨리지 않아야 한다.
//   2) **필터를 바꾸면 첫 페이지로 돌아간다.** 3페이지를 보다 필터를 바꾸면 결과가 3페이지도
//      안 될 수 있고, 빈 화면이 뜨면 "그 카테고리에 제품이 없다" 고 읽힌다.
//   3) **재고 0 을 품절로 표시한다.** SOLD_OUT 전환은 주문 처리의 몫이고 그 코드가 아직
//      없어서, status 가 ON_SALE 인데 재고가 0 인 제품이 목록에 온다.
//   4) **결과 없음과 조회 실패를 구분한다.** 전자는 필터를 바꾸면 되고 후자는 재시도다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PageResponse } from '../../../types/api.types.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductModel } from '../types/product.types.ts'
import { ProductList } from './ProductList.tsx'

const api = vi.hoisted(() => ({ getProductModels: vi.fn(), getProductThumbnail: vi.fn() }))
vi.mock('../api/getProductModels.ts', () => ({ getProductModels: api.getProductModels }))
vi.mock('../api/getProductThumbnail.ts', () => ({
  getProductThumbnail: api.getProductThumbnail,
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
    status: 'ON_SALE',
    stockQuantity: 12,
    createdAt: '2026-09-08T10:00:00',
    ...overrides,
  }
}

function page(
  content: ProductModel[],
  overrides: Partial<PageResponse<ProductModel>> = {},
): PageResponse<ProductModel> {
  return {
    content,
    page: 0,
    size: 12,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
    ...overrides,
  }
}

function renderList() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductList />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 마지막 조회에 넘긴 필터. */
function lastFilters() {
  return api.getProductModels.mock.calls.at(-1)?.[0]
}

beforeEach(() => {
  api.getProductModels.mockResolvedValue(page([product()]))
  api.getProductThumbnail.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ProductList', () => {
  it('제품 카드를 보여준다', async () => {
    renderList()

    expect(await screen.findByText('업사이클 데님 토트백')).toBeInTheDocument()
    // '가방' 은 필터 버튼에도 있다. 카드 안에서 찾는다.
    const card = within(screen.getByRole('listitem'))
    expect(card.getByText('129,000원')).toBeInTheDocument()
    expect(card.getByText('가방')).toBeInTheDocument()
    expect(card.getByRole('link')).toHaveAttribute('href', '/business/products/31')
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('status 를 보내지 않아 서버 기본값(판매 중)을 그대로 쓴다', async () => {
    renderList()

    await waitFor(() => expect(api.getProductModels).toHaveBeenCalled())
    // status 를 실어 보내면 DRAFT 를 섞어 볼 길이 열린다. 기본값을 서버에 맡긴다.
    expect(lastFilters()).not.toHaveProperty('status')
  })

  it('카테고리를 고르면 그 값만 실어 보낸다', async () => {
    const user = renderList()
    await screen.findByText('업사이클 데님 토트백')

    await user.click(screen.getByRole('button', { name: '지갑' }))

    await waitFor(() => expect(lastFilters()).toMatchObject({ category: 'WALLET', page: 0 }))
  })

  it('전체를 고르면 카테고리를 보내지 않는다', async () => {
    const user = renderList()
    await screen.findByText('업사이클 데님 토트백')

    await user.click(screen.getByRole('button', { name: '지갑' }))
    await waitFor(() => expect(lastFilters()?.category).toBe('WALLET'))
    await user.click(screen.getByRole('button', { name: '전체' }))

    // undefined 는 쿼리에서 빠진다 — "전체" 를 뜻하는 특수값을 만들지 않는다.
    await waitFor(() => expect(lastFilters()?.category).toBeUndefined())
  })

  it('페이지를 넘긴 뒤 필터를 바꾸면 첫 페이지로 돌아간다', async () => {
    api.getProductModels.mockResolvedValue(
      page([product()], { totalPages: 3, totalElements: 30, hasNext: true }),
    )
    const user = renderList()
    await screen.findByText('업사이클 데님 토트백')

    await user.click(screen.getByRole('button', { name: '다음' }))
    await waitFor(() => expect(lastFilters()?.page).toBe(1))

    await user.click(screen.getByRole('button', { name: '가방' }))

    // 필터를 바꾸면서 page 를 그대로 두면 결과가 없는 페이지가 열린다.
    await waitFor(() => expect(lastFilters()).toMatchObject({ category: 'BAG', page: 0 }))
  })

  // SOLD_OUT 전환은 주문 처리의 몫이고 그 코드가 아직 없다.
  it('상태가 판매 중이어도 재고가 0 이면 품절로 표시한다', async () => {
    api.getProductModels.mockResolvedValue(
      page([product({ status: 'ON_SALE', stockQuantity: 0 })]),
    )
    renderList()

    expect(await screen.findByText('품절')).toBeInTheDocument()
  })

  it('결과가 없으면 필터를 풀 수 있게 안내한다', async () => {
    api.getProductModels.mockResolvedValue(page([]))
    const user = renderList()

    await user.click(await screen.findByRole('button', { name: '지갑' }))

    expect(await screen.findByText(/지갑 카테고리에 판매 중인 제품이 없습니다/)).toBeInTheDocument()
    // 결과 없음은 에러가 아니다 — 재시도 대신 필터를 푸는 길을 준다.
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전체 보기' })).toBeInTheDocument()
  })

  it('조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getProductModels.mockRejectedValue(
      createAppError(ERROR_KIND.NETWORK_ERROR, { message: '서버에 연결할 수 없습니다.' }),
    )
    renderList()

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('이미지를 저장 키가 아니라 인증 경로에서 받아 온다', async () => {
    renderList()

    const image = await screen.findByRole('img', { name: /대표 이미지/ })
    await waitFor(() => expect(api.getProductThumbnail).toHaveBeenCalledWith(31))
    // 저장 키를 src 에 넣으면 이미지가 영원히 깨진다.
    expect(image.getAttribute('src')).not.toContain('product-images/')
    expect(image.getAttribute('src')).toMatch(/^blob:/)
  })

  it('한 페이지에 다 들어가면 페이지 이동을 보여주지 않는다', async () => {
    renderList()
    await screen.findByText('업사이클 데님 토트백')

    expect(screen.queryByRole('navigation', { name: '페이지 이동' })).not.toBeInTheDocument()
  })

  it('여러 페이지면 현재 위치를 사람 기준(1부터)으로 보여준다', async () => {
    api.getProductModels.mockResolvedValue(
      page([product()], { totalPages: 3, totalElements: 30, hasNext: true }),
    )
    renderList()

    const pager = await screen.findByRole('navigation', { name: '페이지 이동' })
    expect(within(pager).getByText(/1 \/ 3/)).toBeInTheDocument()
  })
})
