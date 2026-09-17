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
import { MemoryRouter, useLocation } from 'react-router-dom'
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
        <output aria-label="현재 경로">{<LocationPath />}</output>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

function LocationPath() {
  return useLocation().pathname
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
  // 브랜드가 확인하려는 것은 자기 제품이 쇼핑몰에서 어떻게 보이는가다.
  it('제품 행을 클릭하면 소비자가 보는 상품 페이지로 간다', async () => {
    const user = renderList()

    const productRow = (await screen.findByText('업사이클 데님 토트백')).closest('li')
    if (productRow === null) throw new Error('제품 행을 찾을 수 없습니다.')
    await user.click(productRow)

    expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/products/31')
  })

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
    expect(await screen.findByText(/생산 전 · 판매 가능 0개/)).toBeInTheDocument()
    const soldOut = row('다 팔린 가방')
    expect(within(soldOut).getByText('품절')).toBeInTheDocument()
    expect(within(soldOut).getByText('판매 가능 0개')).toBeInTheDocument()
  })

  it('재고가 있으면 판매를 시작한다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ stockQuantity: 10 })])
    const user = renderList()

    await user.click(await screen.findByRole('button', { name: '판매 시작' }))

    await waitFor(() =>
      expect(api.changeProductModelStatus).toHaveBeenCalledWith(31, 'ON_SALE'),
    )
    expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/')
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

  // QR 은 개체마다 붙고 개체는 생산 배치가 만든다. 그래서 여태 라벨에 닿으려면 그 제품이
  // 어느 배치에서 생산됐는지 기억해 제품 생산에서 찾아 들어가야 했다.
  it('생산된 제품은 QR 라벨로 가는 길을 준다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ status: 'ON_SALE', stockQuantity: 4 })])
    renderList()

    expect(await screen.findByRole('link', { name: 'QR 라벨 보기' })).toHaveAttribute(
      'href',
      '/brand/products/31/qr',
    )
  })

  // 눌러 보고 빈 화면을 만나게 하지 않는다 — 개체가 없으면 라벨도 없다.
  it('생산 전 제품에는 QR 라벨 링크를 두지 않는다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ status: 'DRAFT', stockQuantity: 0 })])
    renderList()

    await screen.findByText('업사이클 데님 토트백')
    expect(screen.queryByRole('link', { name: 'QR 라벨 보기' })).not.toBeInTheDocument()
  })

  // 등록만 한 제품은 재고가 0 이라 판매를 시작할 수 없는데, 그 0 을 채우는 일이 다른
  // 화면에 있어 "등록했는데 왜 못 파는가" 로 읽혔다.
  // 같은 제품을 또 만드는 일이라 지난 생산을 베껴 채우도록 제품 번호를 들고 간다.
  it('추가 생산은 그 제품을 지목해 생산 화면으로 보낸다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ status: 'DRAFT', stockQuantity: 0 })])
    renderList()

    expect(await screen.findByRole('link', { name: '추가 생산' })).toHaveAttribute(
      'href',
      '/brand/production?product=31',
    )
  })

  // 만들어도 팔 수 없다. 서버가 단종된 제품의 판매 재개를 거절한다.
  it('단종된 제품에는 생산 링크를 두지 않는다', async () => {
    api.getMyProductModels.mockResolvedValue([
      product({ status: 'DISCONTINUED', stockQuantity: 0 }),
    ])
    renderList()

    await screen.findByText('업사이클 데님 토트백')
    expect(screen.queryByRole('link', { name: /생산/ })).not.toBeInTheDocument()
  })

  // 이미 만들어진 개체의 라벨은 여전히 유효하다 — 긁힌 라벨을 다시 인쇄해야 할 수 있다.
  it('단종된 제품의 라벨은 계속 볼 수 있다', async () => {
    api.getMyProductModels.mockResolvedValue([
      product({ status: 'DISCONTINUED', stockQuantity: 0 }),
    ])
    renderList()

    expect(await screen.findByRole('link', { name: 'QR 라벨 보기' })).toBeInTheDocument()
  })

  // 이 화면에서 손대는 대상은 지금 팔리는 제품이다 — 준비·단종을 지나쳐 내려가게 두지 않는다.
  it('판매 중인 제품을 목록 위로 올린다', async () => {
    api.getMyProductModels.mockResolvedValue([
      product({ productModelId: 1, name: '단종된 가방', status: 'DISCONTINUED' }),
      product({ productModelId: 2, name: '준비 중 가방', status: 'DRAFT', stockQuantity: 0 }),
      product({ productModelId: 3, name: '판매 중 가방', status: 'ON_SALE', stockQuantity: 5 }),
      product({ productModelId: 4, name: '품절 가방', status: 'ON_SALE', stockQuantity: 0 }),
    ])
    renderList()

    await screen.findByText('판매 중 가방')
    const names = screen.getAllByRole('listitem').map((row) => row.querySelector('a')?.textContent)
    expect(names).toEqual(['판매 중 가방', '품절 가방', '준비 중 가방', '단종된 가방'])
  })

  // 오타 하나를 고칠 방법이 없었다. 가격을 잘못 넣으면 결제가 통째로 실패한다.
  // 목록을 떠나지 않고 고친다 — 화면을 옮기면 어디까지 훑었는지 잃는다.
  it('제품 수정은 목록 위에 팝업으로 연다', async () => {
    api.getMyProductModels.mockResolvedValue([product({ status: 'ON_SALE', stockQuantity: 4 })])
    renderList()

    await userEvent.click(await screen.findByRole('button', { name: '제품 수정' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText(/제품명/)).toHaveValue('업사이클 데님 토트백')
    // 목록은 그대로 있다. 주소도 바뀌지 않는다.
    expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/')
  })

  // 서버가 PRODUCT_MODEL_DISCONTINUED 로 거절한다. 눌러 보고 알게 하지 않는다.
  it('단종된 제품에는 수정 버튼을 두지 않는다', async () => {
    api.getMyProductModels.mockResolvedValue([
      product({ status: 'DISCONTINUED', stockQuantity: 0 }),
    ])
    renderList()

    await screen.findByText('업사이클 데님 토트백')
    expect(screen.queryByRole('button', { name: '제품 수정' })).not.toBeInTheDocument()
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

// 서버도 다 팔리면 SOLD_OUT 으로 넘기지만 그 표시는 개체를 세는 쪽의 사본일 뿐이다.
// 둘이 어긋났을 때 "판매 중" 이라고 적으면 살 수 없는 제품을 팔리는 것처럼 보여준다.
it('판매 중인데 재고가 0 이면 품절로 적는다', async () => {
  api.getMyProductModels.mockResolvedValue([product({ status: 'ON_SALE', stockQuantity: 0 })])
  renderList()

  expect(await screen.findByText('품절')).toBeInTheDocument()
  expect(screen.queryByText('판매 중')).not.toBeInTheDocument()
})

// 만든 적이 없어 0 인 것을 품절이라 부르면 "팔린 적 없는데 품절" 이 된다.
it('판매 준비 중인 제품은 재고가 0 이어도 품절이 아니다', async () => {
  api.getMyProductModels.mockResolvedValue([product({ status: 'DRAFT', stockQuantity: 0 })])
  renderList()

  expect(await screen.findByText('판매 준비')).toBeInTheDocument()
  expect(screen.queryByText('품절')).not.toBeInTheDocument()
})
