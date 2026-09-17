// 보유 소재 카드 목록 (My Materials).
//
// 이 화면에서 가장 위험한 실수는 **totalQuantity(총 발행량)를 보유 수량 자리에
// 넣는 것**이다. "1200kg 을 갖고 있다" 는 거짓이 되고, 그게 판매 등록으로 이어지면
// 초과 판매다. 같은 실수가 MaterialCard 에 실제로 있었다 (MR !111 에서 정정).
//
// 모킹 경계는 API 모듈 하나다. vitest 에 globals 가 없어 cleanup 을 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { InventoryItem } from '../types/inventory.types.ts'
import { InventoryList } from './InventoryList.tsx'

const api = vi.hoisted(() => ({ getMyMaterials: vi.fn() }))
vi.mock('../api/getMyMaterials.ts', () => api)

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
    ...overrides,
  }
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
})

afterEach(cleanup)

describe('보유 수량 vs 총 발행량', () => {
  it('총 발행량을 화면에 내보내지 않는다', async () => {
    // totalQuantity 1,200,000g = 1200kg. 이 값이 어디에도 보이면 안 된다 —
    // 판매 가능량 자리에 들어갔다는 뜻이고 그게 초과 판매로 이어진다.
    api.getMyMaterials.mockResolvedValue([item()])
    renderList()

    await waitFor(() => expect(screen.getByText('20.2 kg')).toBeInTheDocument())
    expect(screen.queryByText('1,200 kg')).not.toBeInTheDocument()
    expect(screen.queryByText('1200 kg')).not.toBeInTheDocument()
  })
})

describe('단위 표시', () => {
  it('판매 가능량을 kg 로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([item({ available: 750 })])
    renderList()

    expect(await screen.findByText('0.75 kg')).toBeInTheDocument()
  })

  it('음수 판매 가능량을 0 으로 깎지 않는다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 1_000, lockedQuantity: 1_100, available: -100 }),
    ])
    renderList()

    expect(await screen.findByText('-0.1 kg')).toBeInTheDocument()
  })
})

describe('카드 표시 항목', () => {
  it('카드를 누르면 소재 상세로 이동한다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    renderList()

    const link = await screen.findByRole('link', { name: /폐데님 원단/ })
    expect(link).toHaveAttribute('href', '/materials/42')
  })

  it('소재 이름 · 종류 · 판매 가능량 · 상태만 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    renderList()

    expect(await screen.findByText('폐데님 원단')).toBeInTheDocument()
    // 소재 코드는 적지 않는다. 고르는 기준은 이름이고, 코드는 상세에 있다.
    expect(screen.queryByText('MAT-000042')).not.toBeInTheDocument()
    expect(screen.getByText('데님')).toBeInTheDocument() // 종류
    expect(screen.getByText('20.2 kg')).toBeInTheDocument() // 판매 가능량
    // 상태 배지. 필터 버튼도 같은 문구("보유 중")를 쓰므로 배지(span)로 좁힌다.
    expect(screen.getByText('보유 중', { selector: 'span' })).toBeInTheDocument()

    // 이 화면에서 뺀 값들 — 상세로 옮겨졌으니 카드에는 없어야 한다.
    expect(screen.queryByText('20.5 kg')).not.toBeInTheDocument() // 보유 수량
    expect(screen.queryByText('0.3 kg')).not.toBeInTheDocument() // 잠긴 수량
    expect(document.querySelector('time')).toBeNull() // 기준 시각
  })

  it('소진 상태를 구분해 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ status: 'DEPLETED', quantity: 0, lockedQuantity: 0, available: 0 }),
    ])
    renderList()

    // 필터 버튼도 같은 문구("소진")를 쓰므로 배지(span)로 좁힌다.
    expect(await screen.findByText('소진', { selector: 'span' })).toBeInTheDocument()
  })
})

describe('네 가지 상태', () => {
  it('조회 중에는 로딩을 보여준다', () => {
    api.getMyMaterials.mockReturnValue(new Promise(() => {}))
    renderList()

    // variant="skeleton" 은 라벨을 aria-label 로만 준다.
    expect(screen.getByRole('status', { name: '보유 소재를 불러오는 중' })).toBeInTheDocument()
  })

  it('보유가 없으면 EmptyState + 등록 링크다 — 에러가 아니다', async () => {
    api.getMyMaterials.mockResolvedValue([])
    renderList()

    expect(await screen.findByText('보유한 소재가 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '소재 등록하기' })).toHaveAttribute(
      'href',
      '/supplier/materials/new',
    )
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })

  it('실패하면 ErrorMessage 다 — EmptyState 가 아니다', async () => {
    api.getMyMaterials.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '보유 소재를 불러오지 못했습니다.' }),
    )
    renderList()

    expect(await screen.findByText('보유 소재를 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.queryByText('보유한 소재가 없습니다')).not.toBeInTheDocument()
  })

  it('여러 건이면 모두 카드로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ materialId: 1, materialCode: 'MAT-000001', name: '폐데님' }),
      item({ materialId: 2, materialCode: 'MAT-000002', name: '폐면' }),
    ])
    renderList()

    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(2))
  })
})

/**
 * <b>`status` 와 `available` 은 다른 질문에 답한다.</b>
 *
 * `status` 는 "아직 갖고 있는가" 다 — `DEPLETED` 는 가공·생산 확정으로 실제로 다 써
 * 버렸을 때만 된다. `available` 은 "지금 쓸 수 있는가" 이고, 판매글이나 가공 요청에
 * 걸어 두면 잠기기만 하고 보유량은 그대로라(취소하면 돌아온다) 상태는 `ACTIVE` 로 남는다.
 *
 * 둘을 그대로 나란히 두면 <b>"0.0 kg · 보유 중"</b> 이 된다. 데이터는 맞는데 화면이
 * 모순으로 읽힌다 — 실제 사실은 "갖고는 있는데 전부 묶여 있다" 다.
 */
describe('전량 잠긴 소재', () => {
  it('보유 중이 아니라 전량 잠김으로 말한다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 6_000, lockedQuantity: 6_000, available: 0, status: 'ACTIVE' }),
    ])
    renderList()

    // 필터 버튼에도 "보유 중"·"소진" 이 있으므로 카드 안으로 좁혀서 본다.
    const card = await screen.findByRole('listitem')
    expect(within(card).getByText('전량 잠김')).toBeInTheDocument()
    expect(within(card).queryByText('보유 중')).not.toBeInTheDocument()
  })

  it('쓸 수 있는 양이 남아 있으면 보유 중이다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 6_000, lockedQuantity: 1_000, available: 5_000 }),
    ])
    renderList()

    const card = await screen.findByRole('listitem')
    expect(within(card).getByText('보유 중')).toBeInTheDocument()
  })

  it('실제로 다 쓴 소재는 소진이다', async () => {
    // 잠긴 것과 써 버린 것은 다르다. 잠긴 것은 취소하면 돌아오고, 쓴 것은 안 돌아온다.
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 0, lockedQuantity: 0, available: 0, status: 'DEPLETED' }),
    ])
    renderList()

    const card = await screen.findByRole('listitem')
    expect(within(card).getByText('소진')).toBeInTheDocument()
  })
})

/**
 * 예전에는 `available` 하나만 있어서 그 숫자가 왜 그런지 알 방법이 없었다 — 30kg 을
 * 갖고 있는데 "12kg" 만 보이면 나머지 18kg 이 어디 갔는지 화면 어디에도 없다.
 * 응답에는 보유량과 잠긴 양이 함께 오는데 쓰지 않고 있었다.
 */
describe('수량 내역', () => {
  it('잠긴 양이 있으면 보유와 잠김을 함께 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 30_000, lockedQuantity: 18_000, available: 12_000 }),
    ])
    renderList()

    expect(await screen.findByText(/보유 30 kg/)).toBeInTheDocument()
    expect(screen.getByText(/잠김 18 kg/)).toBeInTheDocument()
  })

  it('잠긴 양이 없으면 쪼개지 않는다', async () => {
    // 보유 = 가능이라 같은 숫자를 두 번 쓰는 셈이다. 읽을 것만 늘어난다.
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 30_000, lockedQuantity: 0, available: 30_000 }),
    ])
    renderList()

    expect(await screen.findByText('폐데님 원단')).toBeInTheDocument()
    expect(screen.queryByText(/잠김/)).not.toBeInTheDocument()
  })
})
