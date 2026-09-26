// 판매 관리 화면.
//
// **목록이 먼저다.** 공급업체가 이 화면에 오는 이유는 대부분 "내 판매글이 어떻게 됐나"
// 이고 등록은 가끔 하는 일이다. 그래서 등록은 접어 두는데, 그러면 두 가지가 깨지기 쉽다 —
// 소재 상세에서 "판매 등록" 을 눌러 온 사람에게도 닫힌 채로 보이는 것, 그리고 등록을 마친
// 뒤에도 폼이 열려 있어 방금 올린 글을 가리는 것.
//
// 이력 미기록 표시도 여기서 본다. 판매 등록 때 서명이 끝나지 않은 글은 서버에만 있고
// 체인에는 없는데, 카드가 ON_SALE 배지를 정상처럼 띄워서 판매자가 알 방법이 없었다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/authStore.ts'
import type { InventoryItem } from '../../features/inventory/types/inventory.types.ts'
import type { Listing } from '../../features/marketplace/types/marketplace.types.ts'
import { SupplierMarketPage } from './SupplierMarketPage.tsx'

const api = vi.hoisted(() => ({
  getMyMaterials: vi.fn(),
  getListings: vi.fn(),
  getMaterial: vi.fn(),
}))

vi.mock('../../features/inventory/api/getMyMaterials.ts', () => ({
  getMyMaterials: api.getMyMaterials,
}))
vi.mock('../../features/marketplace/api/getListings.ts', () => ({
  getListings: api.getListings,
}))
vi.mock('../../features/material/api/getMaterial.ts', () => ({
  getMaterial: api.getMaterial,
}))

const DENIM: InventoryItem = {
  materialId: 1,
  materialCode: 'MAT-000001',
  materialType: 'DENIM',
  rootOrigin: 'WASTE',
  generationType: 'INITIAL',
  name: '세척된 폐데님',
  grade: 'A',
  status: 'ACTIVE',
  totalQuantity: 30_000,
  quantity: 30_000,
  lockedQuantity: 0,
  available: 30_000,
  syncedAt: '2026-09-07T00:00:00.000Z',
  completedProcessTypes: [],
}

const COTTON: InventoryItem = { ...DENIM, materialId: 2, materialCode: 'MAT-000002', name: '재생 면 원사' }

/** 온체인까지 기록이 끝난 정상 판매글. */
const LISTING: Listing = {
  listingId: 10,
  chainListingId: 29,
  materialId: 1,
  materialCode: 'MAT-000001',
  materialName: '세척된 폐데님',
  materialDescription: null,
  materialType: 'DENIM',
  rootOrigin: 'WASTE',
  generationType: 'INITIAL',
  grade: 'A',
  sellerId: 9,
  sellerName: '공급자',
  totalQuantity: 30_000,
  availableQuantity: 12_000,
  pricePerKg: 4_200,
  status: 'ON_SALE',
  createdAt: '2026-09-10T00:00:00.000Z',
}

/** 판매 등록 때 서명이 끝나지 않아 체인에는 없는 글. */
const UNRECORDED: Listing = { ...LISTING, listingId: 11, chainListingId: null }

function listingPage(content: Listing[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function renderPage(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <SupplierMarketPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/**
 * 상태별로 다른 결과를 돌려준다.
 *
 * 요약 지표(useSupplierListingSummary)가 ON_SALE·SOLD_OUT·CANCELED 를 <b>따로</b> 묻는다 —
 * 서버가 기본으로 ON_SALE 만 주기 때문이다. 한 응답을 모든 호출에 돌려주면 세 건수가
 * 전부 같아져 집계 검사가 의미를 잃는다.
 */
function mockByStatus(rows: Partial<Record<'ON_SALE' | 'SOLD_OUT' | 'CANCELED', Listing[]>>) {
  api.getListings.mockImplementation(async (params: { status?: string } = {}) => {
    const content = params.status ? (rows[params.status as keyof typeof rows] ?? []) : Object.values(rows).flat()
    return listingPage(content)
  })
}

beforeEach(() => {
  api.getMyMaterials.mockReset()
  api.getListings.mockReset()
  api.getMaterial.mockReset()
  mockByStatus({})
  api.getMaterial.mockResolvedValue({ tokenId: null })
  useAuthStore.setState({
    user: { id: 9, email: 'brand@test.com', name: '공급자', role: 'SUPPLIER', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
})

afterEach(cleanup)

describe('소재 상세에서 넘어온 판매 등록', () => {
  it('materialId 쿼리와 일치하는 소재를 미리 선택해 폼을 바로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM, COTTON])
    renderPage('/supplier/listings?materialId=2')

    expect(await screen.findByText('재생 면 원사')).toBeInTheDocument()
    expect(screen.queryByText('판매할 소재를 먼저 선택하세요')).not.toBeInTheDocument()
  })

  it('보유하지 않은 소재 id 면 열어 두되 선택은 비워 둔다', async () => {
    // 소재를 들고 왔다는 의도는 유효하므로 패널은 연다. 다만 그 소재가 내 것이 아니면
    // 고를 수 없으니, 다음 할 일을 말해 주고 사용자가 직접 고르게 둔다.
    api.getMyMaterials.mockResolvedValue([DENIM])
    renderPage('/supplier/listings?materialId=999')

    await waitFor(() => expect(api.getMyMaterials).toHaveBeenCalled())
    expect(await screen.findByText(/소재를 고르면 수량과 가격을/)).toBeInTheDocument()
  })
})

/**
 * <b>목록이 먼저다.</b>
 *
 * 예전에는 등록 폼이 통째로 위에 펼쳐져 있었고, 소재를 고르기 전에는 안내 박스가 그 자리를
 * 차지했다. 매번 하는 일(판매글 확인)이 가끔 하는 일(등록) 뒤로 밀렸다.
 */
describe('등록 패널 접기', () => {
  it('들어오면 등록은 접혀 있고 목록이 먼저 보인다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM])
    mockByStatus({ ON_SALE: [LISTING] })
    renderPage('/supplier/listings')

    // 목록이 먼저 보인다.
    expect(await screen.findByText('세척된 폐데님')).toBeInTheDocument()
    // 접혀 있으므로 소재 선택도 안내도 그려지지 않는다.
    expect(screen.queryByLabelText('판매할 소재')).not.toBeInTheDocument()
    expect(screen.queryByText(/소재를 고르면 수량과 가격을/)).not.toBeInTheDocument()
  })

  it('판매 등록을 누르면 열린다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM])
    renderPage('/supplier/listings')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: '판매 등록' }))

    expect(await screen.findByText(/소재를 고르면 수량과 가격을/)).toBeInTheDocument()
  })

  it('소재를 들고 오면 열린 채로 시작한다', async () => {
    // 소재 상세에서 "판매 등록" 을 눌러 온 사람에게 버튼을 한 번 더 누르게 하면 안 된다.
    api.getMyMaterials.mockResolvedValue([DENIM, COTTON])
    renderPage('/supplier/listings?materialId=2')

    expect(await screen.findByText('재생 면 원사')).toBeInTheDocument()
  })
})

/**
 * 요약 지표.
 *
 * 목록을 세어 보지 않아도 "지금 몇 건을 팔고 있고 손볼 것이 있는가" 가 바로 읽혀야 한다.
 * 상태별 건수는 서버가 세어 주지만(totalElements), <b>이력 미기록은 세어 주지 않는다</b> —
 * 그런 필터가 없어서 행을 받아 직접 센다.
 */
describe('요약 지표', () => {
  it('상태별 건수를 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM])
    mockByStatus({ ON_SALE: [LISTING, UNRECORDED], SOLD_OUT: [LISTING], CANCELED: [] })
    renderPage('/supplier/listings')

    // 지표는 role=group + aria-label 이다. 같은 낱말이 탭·배지에도 있어 글자만으로는
    // 어느 것을 보는지 정할 수 없다.
    await waitFor(() =>
      expect(screen.getByRole('group', { name: '판매 중' })).toHaveTextContent('2건'),
    )
    expect(screen.getByRole('group', { name: '품절' })).toHaveTextContent('1건')
  })

  it('이력이 남지 않은 건수를 따로 센다', async () => {
    // 한 페이지만 세면 뒷페이지의 미기록 건을 놓쳐 숫자가 조용히 틀린다.
    api.getMyMaterials.mockResolvedValue([DENIM])
    mockByStatus({ ON_SALE: [LISTING, UNRECORDED] })
    renderPage('/supplier/listings')

    await waitFor(() =>
      expect(screen.getByRole('group', { name: '기록 완료 필요' })).toHaveTextContent('1건'),
    )
  })
})

/**
 * <b>이력이 안 남은 판매글을 드러낸다.</b>
 *
 * {@code chainListingId} 가 null 이면 판매 등록 때 서명이 끝나지 않은 글이다. 서버에는
 * 있고 체인에는 없으므로 구매자가 사도 발송·수령이 기록되지 않는다 — 발송 단계에서 서명
 * 창이 아예 뜨지 않는 증상의 출처가 여기다. 그런데 카드는 ON_SALE 배지를 정상처럼 띄워서
 * 판매자도 구매자도 알 방법이 없었다.
 */
describe('이력 미기록 표시', () => {
  it('체인에 없는 판매글에 안내와 복구 경로를 준다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM])
    mockByStatus({ ON_SALE: [UNRECORDED] })
    renderPage('/supplier/listings')

    expect(await screen.findByText('이력 기록이 남지 않았습니다')).toBeInTheDocument()
    // 알리기만 하고 고칠 길이 없으면 사용자가 할 수 있는 일이 없다.
    expect(screen.getByRole('link', { name: /지금 완료하기/ })).toHaveAttribute(
      'href',
      '/marketplace/11',
    )
  })

  it('정상 판매글에는 표시하지 않는다', async () => {
    // 전부에 붙으면 경고가 배경이 되어 아무도 보지 않는다.
    api.getMyMaterials.mockResolvedValue([DENIM])
    mockByStatus({ ON_SALE: [LISTING] })
    renderPage('/supplier/listings')

    expect(await screen.findByText('세척된 폐데님')).toBeInTheDocument()
    expect(screen.queryByText('이력 기록이 남지 않았습니다')).not.toBeInTheDocument()
  })
})
