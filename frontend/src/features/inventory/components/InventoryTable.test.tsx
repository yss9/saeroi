// 보유 소재 표 (My Materials).
//
// 이 화면에서 가장 위험한 실수는 **totalQuantity(총 발행량)를 보유 수량 자리에
// 넣는 것**이다. "1200kg 을 갖고 있다" 는 거짓이 되고, 그게 판매 등록으로 이어지면
// 초과 판매다. 같은 실수가 MaterialCard 에 실제로 있었다 (MR !111 에서 정정).
//
// 모킹 경계는 API 모듈 하나다. vitest 에 globals 가 없어 cleanup 을 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { InventoryItem } from '../types/inventory.types.ts'
import { InventoryTable } from './InventoryTable.tsx'

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

function renderTable() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <InventoryTable />
        <output aria-label="현재 경로">{<LocationPath />}</output>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function LocationPath() {
  return useLocation().pathname
}

beforeEach(() => {
  api.getMyMaterials.mockReset()
})

afterEach(cleanup)

describe('보유 수량 vs 총 발행량', () => {
  it('보유 수량 자리에 quantity 를 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    renderTable()

    expect(await screen.findByText('20.5 kg')).toBeInTheDocument()
  })

  it('총 발행량을 화면에 내보내지 않는다', async () => {
    // totalQuantity 1,200,000g = 1200kg. 이 값이 어디에도 보이면 안 된다 —
    // 보유 수량 자리에 들어갔다는 뜻이고 그게 초과 판매로 이어진다.
    api.getMyMaterials.mockResolvedValue([item()])
    renderTable()

    await waitFor(() => expect(screen.getByText('20.5 kg')).toBeInTheDocument())
    expect(screen.queryByText('1,200 kg')).not.toBeInTheDocument()
    expect(screen.queryByText('1200 kg')).not.toBeInTheDocument()
  })
})

describe('단위 표시', () => {
  it('gram 을 kg 로 바꿔 보여준다', async () => {
    // 세 값을 다르게 둔다. 같으면 여러 셀에 같은 문자열이 나온다.
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 1_000, lockedQuantity: 250, available: 750 }),
    ])
    renderTable()

    expect(await screen.findByText('1 kg')).toBeInTheDocument()
  })

  it('gram 원값을 노출하지 않는다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    renderTable()

    await waitFor(() => expect(screen.getByText('20.5 kg')).toBeInTheDocument())
    expect(screen.queryByText('20,500')).not.toBeInTheDocument()
  })

  it('판매 가능량과 잠긴 수량을 각각 kg 로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 1_000, lockedQuantity: 300, available: 700 }),
    ])
    renderTable()

    expect(await screen.findByText('0.3 kg')).toBeInTheDocument()
    expect(screen.getByText('0.7 kg')).toBeInTheDocument()
  })

  it('음수 판매 가능량을 0 으로 깎지 않는다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ quantity: 1_000, lockedQuantity: 1_100, available: -100 }),
    ])
    renderTable()

    expect(await screen.findByText('-0.1 kg')).toBeInTheDocument()
  })
})

describe('티켓 표시 항목', () => {
  it('소재 행의 빈 영역을 클릭하면 상세로 이동한다', async () => {
    const user = userEvent.setup()
    api.getMyMaterials.mockResolvedValue([item()])
    renderTable()

    const materialRow = (await screen.findByText('MAT-000042')).closest('tr')
    if (materialRow === null) throw new Error('소재 행을 찾을 수 없습니다.')
    await user.click(materialRow)

    expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/materials/42')
  })

  it('소재 · 보유 수량 · 판매 가능량 · 상태 · 기준 시각을 모두 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    renderTable()

    expect(await screen.findByText('폐데님 원단')).toBeInTheDocument()
    expect(screen.getByText('MAT-000042')).toBeInTheDocument()
    expect(screen.getByText('20.5 kg')).toBeInTheDocument() // 보유 수량
    expect(screen.getByText('20.2 kg')).toBeInTheDocument() // 판매 가능량
    expect(screen.getByText('보유 중')).toBeInTheDocument() // 상태
    expect(document.querySelector('time')).not.toBeNull() // 기준 시각
  })

  it('소진 상태를 구분해 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ status: 'DEPLETED', quantity: 0, lockedQuantity: 0, available: 0 }),
    ])
    renderTable()

    expect(await screen.findByText('소진')).toBeInTheDocument()
  })

  it('소재 상세로 이동하는 링크를 준다', async () => {
    api.getMyMaterials.mockResolvedValue([item()])
    renderTable()

    const link = await screen.findByRole('link', { name: '폐데님 원단' })
    expect(link).toHaveAttribute('href', '/materials/42')
  })
})

describe('세 가지 상태', () => {
  it('조회 중에는 로딩을 보여준다', () => {
    api.getMyMaterials.mockReturnValue(new Promise(() => {}))
    renderTable()

    // variant="skeleton" 은 라벨을 aria-label 로만 준다.
    expect(screen.getByRole('status', { name: '보유 소재를 불러오는 중' })).toBeInTheDocument()
  })

  it('보유가 없으면 EmptyState + 등록 링크다 — 에러가 아니다', async () => {
    api.getMyMaterials.mockResolvedValue([])
    renderTable()

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
    renderTable()

    expect(await screen.findByText('보유 소재를 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.queryByText('보유한 소재가 없습니다')).not.toBeInTheDocument()
  })

  it('여러 건이면 모두 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([
      item({ materialId: 1, materialCode: 'MAT-000001', name: '폐데님' }),
      item({ materialId: 2, materialCode: 'MAT-000002', name: '폐면' }),
    ])
    renderTable()

    await waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(3)) // 헤더 1 + 본문 2
  })
})
