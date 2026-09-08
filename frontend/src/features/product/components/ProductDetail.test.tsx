// 제품 상세.
//
// 이 화면에서 틀리면 안 되는 지점은 셋이다.
//
//   1) **이미지를 `<img src={thumbnailUrl}>` 로 그리지 않는다.** thumbnailUrl 은 저장
//      키이고 실제 이미지는 인증이 필요한 별도 경로에서 온다. 키를 그대로 넣으면
//      제품은 보이는데 이미지만 영원히 깨진다.
//   2) **재고 0 을 "품절" 로 뭉치지 않는다.** 아직 만들지 않은 것(DRAFT)과 다 팔린 것은
//      다르다. 등록만 된 제품은 언제나 재고 0 이다.
//   3) **이미지가 없다고 화면 전체를 에러로 덮지 않는다.** 볼 수 있는 정보까지 사라진다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductModel } from '../types/product.types.ts'
import { ProductDetail } from './ProductDetail.tsx'

const api = vi.hoisted(() => ({ getProductModel: vi.fn(), getProductThumbnail: vi.fn() }))
vi.mock('../api/getProductModel.ts', () => ({ getProductModel: api.getProductModel }))
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
    description: '폐데님을 재단해 만든 토트백입니다.\n안감은 재생 폴리에스터입니다.',
    thumbnailUrl: 'product-images/2026/09/abc123.png',
    status: 'ON_SALE',
    stockQuantity: 12,
    createdAt: '2026-09-08T10:00:00',
    ...overrides,
  }
}

function renderDetail() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductDetail productModelId={31} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getProductModel.mockResolvedValue(product())
  api.getProductThumbnail.mockResolvedValue(new Blob(['fake'], { type: 'image/png' }))
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ProductDetail', () => {
  it('제품 정보를 보여준다', async () => {
    renderDetail()

    expect(await screen.findByRole('heading', { name: '업사이클 데님 토트백' })).toBeInTheDocument()
    expect(screen.getByText('가방')).toBeInTheDocument()
    expect(screen.getByText('129,000원')).toBeInTheDocument()
    expect(screen.getByText('판매 중')).toBeInTheDocument()
    expect(screen.getByText(/폐데님을 재단해 만든/)).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('저장 키를 이미지 주소로 쓰지 않고 인증 경로에서 받아 온다', async () => {
    renderDetail()

    const image = await screen.findByRole('img', { name: /대표 이미지/ })
    await waitFor(() => expect(api.getProductThumbnail).toHaveBeenCalledWith(31))

    // 저장 키가 그대로 들어가면 이미지가 영원히 깨진다.
    expect(image.getAttribute('src')).not.toContain('product-images/')
    // blob URL 이어야 한다 (jsdom 에서는 setup.ts 의 스텁이 만든 값).
    expect(image.getAttribute('src')).toMatch(/^blob:/)
  })

  it('이미지를 못 받아도 제품 정보는 그대로 보여준다', async () => {
    api.getProductThumbnail.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { code: 'PRODUCT_THUMBNAIL_NOT_FOUND', status: 404 }),
    )
    renderDetail()

    expect(await screen.findByText('이미지를 불러오지 못했습니다.')).toBeInTheDocument()
    // 화면 전체가 에러로 덮이지 않는다.
    expect(screen.getByRole('heading', { name: '업사이클 데님 토트백' })).toBeInTheDocument()
    expect(screen.getByText('129,000원')).toBeInTheDocument()
  })

  it('제품 조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getProductModel.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'PRODUCT_MODEL_NOT_FOUND',
        message: '제품을 찾을 수 없습니다.',
        status: 404,
      }),
    )
    renderDetail()

    expect(await screen.findByRole('alert')).toHaveTextContent('제품을 찾을 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  describe('재고 표시', () => {
    it('판매 중이면 남은 수량을 보여준다', async () => {
      renderDetail()

      expect(await screen.findByText('남은 수량 12개')).toBeInTheDocument()
    })

    // 등록만 된 제품은 언제나 재고 0 이다. 숫자만 두면 "품절" 로 읽힌다.
    it('등록만 된 제품은 아직 판매 전이라고 말한다', async () => {
      api.getProductModel.mockResolvedValue(product({ status: 'DRAFT', stockQuantity: 0 }))
      renderDetail()

      expect(await screen.findByText(/아직 판매를 시작하지 않은 제품/)).toBeInTheDocument()
      expect(screen.getByText('판매 준비')).toBeInTheDocument()
      expect(screen.queryByText(/품절/)).not.toBeInTheDocument()
    })

    it('판매 중인데 수량이 0 이면 품절로 말한다', async () => {
      api.getProductModel.mockResolvedValue(product({ status: 'SOLD_OUT', stockQuantity: 0 }))
      renderDetail()

      expect(await screen.findByText('품절되었습니다.')).toBeInTheDocument()
    })

    it('단종된 제품은 수량을 말하지 않는다', async () => {
      api.getProductModel.mockResolvedValue(
        product({ status: 'DISCONTINUED', stockQuantity: 3 }),
      )
      renderDetail()

      expect(await screen.findByText('단종된 제품입니다.')).toBeInTheDocument()
      expect(screen.queryByText(/남은 수량/)).not.toBeInTheDocument()
    })
  })
})
