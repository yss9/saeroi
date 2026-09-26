// 보유 소재 표 (My Materials).
//
// 이 화면에서 가장 위험한 실수는 **totalQuantity(총 발행량)를 보유 수량 자리에
// 넣는 것**이다. "1200kg 을 갖고 있다" 는 거짓이 되고, 그게 판매 등록으로 이어지면
// 초과 판매다. 같은 실수가 MaterialCard 에 실제로 있었다 (MR !111 에서 정정).
//
// 다음으로 위험한 것은 **잠긴 곳을 잘못 적는 것**이다. 그 칸을 믿고 엉뚱한 판매글을
// 취소하면 되돌릴 수 없다. 그래서 "모르면 비운다" 를 못박는다.
//
// 모킹 경계는 API 모듈이다. 표가 판매글을 이어 붙이므로 그쪽도 함께 자른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { UserRole } from '../../../types/role.types.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { InventoryItem } from '../types/inventory.types.ts'
import { InventoryList } from './InventoryList.tsx'

const api = vi.hoisted(() => ({ getMyMaterials: vi.fn() }))
vi.mock('../api/getMyMaterials.ts', () => api)

// 잠긴 곳 칸이 내 판매글을 이어 붙인다. 목록 자체를 보는 테스트가 아니므로 잘라 둔다.
const market = vi.hoisted(() => ({ getListings: vi.fn() }))
vi.mock('../../marketplace/api/getListings.ts', () => market)

const ME = 7

function item(overrides: Partial<InventoryItem> = {}): InventoryItem {
  return {
    materialId: 42,
    materialCode: 'MAT-000042',
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    name: '폐데님 원단',
    grade: 'A',
    status: 'ACTIVE',
    totalQuantity: 1_200_000,
    quantity: 20_500,
    lockedQuantity: 300,
    available: 20_200,
    syncedAt: '2026-09-07T09:00:00',
    completedProcessTypes: [],
    ...overrides,
  }
}

/** 판매글 한 건. 잠긴 곳 칸에 필요한 값만 채운다. */
function listing(overrides: Record<string, unknown> = {}) {
  return {
    listingId: 12,
    chainListingId: null,
    materialId: 42,
    materialCode: 'MAT-000042',
    materialName: '폐데님 원단',
    materialDescription: null,
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    grade: 'A',
    sellerId: ME,
    sellerName: '수거업체 A',
    totalQuantity: 20_500,
    availableQuantity: 300,
    pricePerKg: 4_200,
    status: 'ON_SALE',
    createdAt: '2026-09-01T00:00:00.000Z',
    ...overrides,
  }
}

function signInAs(role: UserRole, id: number = ME) {
  useAuthStore.setState({
    user: { id, name: '테스트 계정', email: 'user@saeroi.test', role, walletAddress: null },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
}

function renderList() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <InventoryList />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getMyMaterials.mockReset()
  market.getListings.mockReset()
  market.getListings.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 })
  signInAs('SUPPLIER')
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

describe('보유 수량 vs 총 발행량', () => {
  it('총 발행량을 화면에 내보내지 않는다', async () => {
    // totalQuantity 1,200,000g = 1200kg. 이 값이 어디에도 보이면 안 된다 —
    // 쓸 수 있는 양 자리에 들어갔다는 뜻이고 그게 초과 판매로 이어진다.
    api.getMyMaterials.mockResolvedValue([item()])
    renderList()

    // 맨 위 합계 줄도 같은 숫자를 쓰므로 행 안으로 좁힌다.
    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).getByText('20.2 kg')).toBeInTheDocument()
    expect(screen.queryByText('1,200 kg')).not.toBeInTheDocument()
    expect(screen.queryByText('1200 kg')).not.toBeInTheDocument()
  })
})

describe('단위 표시', () => {
  it('쓸 수 있는 양을 kg 로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([item({ available: 750 })])
    renderList()

    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).getByText('0.75 kg')).toBeInTheDocument()
  })

  it('음수 가용량을 0 으로 깎지 않는다', async () => {
    // 잠금 합계가 보유량을 넘었다는 데이터 파손 신호다. 숨기면 원인을 못 찾는다.
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 1_000, lockedQuantity: 1_100, available: -100 }),
    ])
    renderList()

    expect(await screen.findByText('-0.1 kg')).toBeInTheDocument()
  })
})

describe('표에 적는 것', () => {
  it('소재 이름에서 상세로 간다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    renderList()

    // 행 전체도 눌리지만 링크가 있어야 키보드로 갈 수 있다.
    const link = await screen.findByRole('link', { name: '폐데님 원단' })
    expect(link).toHaveAttribute('href', '/materials/42')
  })

  it('소재 코드를 함께 적는다', async () => {
    // 같은 이름의 소재가 여러 건일 수 있다. 예전에는 코드가 없어 구분할 수 없었다.
    api.getMyMaterials.mockResolvedValue([item()])
    renderList()

    expect(await screen.findByText('MAT-000042')).toBeInTheDocument()
  })

  it('보유량과 잠긴 양을 함께 적는다', async () => {
    // 쓸 수 있는 양 하나만 있으면 그 숫자가 왜 그런지 알 수 없다.
    api.getMyMaterials.mockResolvedValue([item()])
    renderList()

    expect(await screen.findByText('20.5 kg')).toBeInTheDocument()
    expect(screen.getByText('0.3 kg 잠김')).toBeInTheDocument()
  })

  it('등급이 없으면 칩을 만들지 않는다', async () => {
    // 빈 칩을 두면 없는 정보가 있는 것처럼 보인다.
    api.getMyMaterials.mockResolvedValue([item({ grade: null })])
    renderList()

    // 표 머리글이 "종류 · 등급" 이라 화면 전체로 물으면 그것이 걸린다.
    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).getByText('데님')).toBeInTheDocument()
    expect(within(row).queryByText(/등급/)).not.toBeInTheDocument()
  })

  it('이 숫자가 언제 기준인지 적는다', async () => {
    // 온체인 잔액 동기화가 없어 값이 방금 것인지 어제 것인지 알 수 없었다.
    // 서버는 syncedAt 을 내려주고 있었는데 화면이 버렸다.
    api.getMyMaterials.mockResolvedValue([item({ syncedAt: '2026-09-07T09:05:00' })])
    renderList()

    expect(await screen.findByText('09/07 09:05')).toBeInTheDocument()
  })
})

/**
 * 세 가지가 서로 다른 사실이다 — 소진(다 썼다) / 전량 잠김(묶여 있다) / 보유 중.
 *
 * 순서가 중요하다. 다 쓴 소재는 available 도 0 이라, 잠김을 먼저 물으면 되찾을 수 없는
 * 소재를 "잠김" 이라고 적는다.
 */
describe('세 가지 상태', () => {
  it('보유 중이 아니라 전량 잠김으로 말한다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 6_000, lockedQuantity: 6_000, available: 0, status: 'ACTIVE' }),
    ])
    renderList()

    // 필터 버튼에도 같은 문구가 있으므로 행 안으로 좁혀서 본다.
    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).getByText('전량 잠김')).toBeInTheDocument()
    expect(within(row).queryByText('보유 중')).not.toBeInTheDocument()
  })

  it('쓸 수 있는 양이 남아 있으면 보유 중이다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 6_000, lockedQuantity: 1_000, available: 5_000 }),
    ])
    renderList()

    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).getByText('보유 중')).toBeInTheDocument()
  })

  it('보유량이 0 이면 소진이다 — 서버가 ACTIVE 로 줘도', async () => {
    // 서버에 DEPLETED 전환 메서드가 없어 0g 도 ACTIVE 로 온다.
    api.getMyMaterials.mockResolvedValue([
      item({ status: 'ACTIVE', quantity: 0, lockedQuantity: 0, available: 0 }),
    ])
    renderList()

    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).getByText('소진')).toBeInTheDocument()
    expect(within(row).queryByText('보유 중')).not.toBeInTheDocument()
  })
})

/**
 * 잠긴 곳.
 *
 * <b>모르면 비운다.</b> 판매글 잠금만 되짚을 수 있고 가공·생산 잠금은 조회 API 가 없다.
 * 지어내면 그 말을 믿고 엉뚱한 것을 취소하게 된다.
 */
describe('잠긴 곳', () => {
  it('판매글에 잠긴 소재는 그 판매글을 가리킨다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    market.getListings.mockResolvedValue({
      content: [listing({ listingId: 12, materialId: 42 })],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 200,
    })
    renderList()

    const link = await screen.findByRole('link', { name: '판매글 #12' })
    expect(link).toHaveAttribute('href', '/marketplace/12')
  })

  it('되짚을 수 없으면 비운다', async () => {
    // 가공·생산에 잠긴 소재. 잠긴 양은 있지만 출처를 말할 수 없다.
    api.getMyMaterials.mockResolvedValue([item({ lockedQuantity: 5_000 })])
    renderList()

    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).queryByRole('link', { name: /판매글/ })).not.toBeInTheDocument()
  })

  it('다른 소재의 판매글을 끌어다 붙이지 않는다', async () => {
    // materialId 로 묶지 않으면 아무 판매글이나 아무 소재에 붙는다.
    api.getMyMaterials.mockResolvedValue([item({ materialId: 42 })])
    market.getListings.mockResolvedValue({
      content: [listing({ listingId: 99, materialId: 999 })],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 200,
    })
    renderList()

    const row = await screen.findByRole('row', { name: /폐데님 원단/ })
    expect(within(row).queryByRole('link', { name: /판매글/ })).not.toBeInTheDocument()
  })
})

describe('네 가지 상태', () => {
  it('조회 중에는 로딩을 보여준다', () => {
    api.getMyMaterials.mockReturnValue(new Promise(() => {}))
    renderList()

    expect(screen.getByLabelText('보유 소재를 불러오는 중')).toBeInTheDocument()
  })

  it('실패하면 ErrorMessage 다 — EmptyState 가 아니다', async () => {
    api.getMyMaterials.mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { message: '서버 오류' }))
    renderList()

    expect(await screen.findByText('서버 오류')).toBeInTheDocument()
  })

  it('공급업체에게는 빈 화면에서 등록으로 보낸다', async () => {
    api.getMyMaterials.mockResolvedValue([])
    renderList()

    const link = await screen.findByRole('link', { name: '소재 등록하기' })
    expect(link).toHaveAttribute('href', '/supplier/materials/new')
  })

  it('브랜드에게는 등록 링크를 내지 않는다', async () => {
    // /supplier/materials/new 는 SUPPLIER 전용이라 누르면 /403 으로 튕긴다 —
    // 빈 화면에서 유일한 출구가 막다른 길이 되는 셈이었다.
    signInAs('BRAND')
    api.getMyMaterials.mockResolvedValue([])
    renderList()

    expect(await screen.findByText('보유한 소재가 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /소재 등록/ })).not.toBeInTheDocument()
  })

  it('여러 건이면 모두 행으로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ materialId: 1, name: '폐데님 원단' }),
      item({ materialId: 2, name: '폐면 원단' }),
    ])
    renderList()

    expect(await screen.findByText('폐데님 원단')).toBeInTheDocument()
    expect(screen.getByText('폐면 원단')).toBeInTheDocument()
  })
})
