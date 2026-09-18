// 소재 선택 검증.
//
// 이 컴포넌트에서 사고가 나는 지점은 하나다 — **`quantity`(보유량)를 기준으로 다루는 것**.
// 보유량에는 이미 다른 판매글·가공 요청에 잠긴 몫이 섞여 있어서, 그걸 최대값으로 넘기면
// 사용자가 팔 수 없는 수량을 입력하고 서버가 409 로 거절한다.
// 실제로 그 일이 있었다 — 고정값 18kg 을 표시했지만 실제 가용량은 10kg 이었다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import { MaterialSelect } from './MaterialSelect.tsx'

const { getMyMaterials } = vi.hoisted(() => ({ getMyMaterials: vi.fn() }))

vi.mock('../../inventory/api/getMyMaterials.ts', () => ({ getMyMaterials }))

/** 30kg 보유, 20kg 이 이미 잠겨 있어 실제로 팔 수 있는 것은 10kg. */
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
  lockedQuantity: 20_000,
  available: 10_000,
  syncedAt: '2026-09-07T00:00:00.000Z',
  completedProcessTypes: [],
}

/** 전량이 잠겨 팔 수 없는 소재. */
const LOCKED: InventoryItem = {
  ...DENIM,
  materialId: 2,
  materialCode: 'MAT-000002',
  name: '폐면 자투리',
  quantity: 15_000,
  lockedQuantity: 15_000,
  available: 0,
}

function renderSelect(onChange = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MaterialSelect value={null} onChange={onChange} />
    </QueryClientProvider>,
  )
  return onChange
}

beforeEach(() => getMyMaterials.mockReset())
afterEach(cleanup)

describe('MaterialSelect', () => {
  it('보유 소재를 판매 가능 수량과 함께 보여준다', async () => {
    getMyMaterials.mockResolvedValue([DENIM])
    renderSelect()

    // 30kg 을 보유하지만 10kg 만 팔 수 있다. 30 이 보이면 보유량을 쓴 것이다.
    // formatKg 은 "10 kg" 처럼 공백을 넣으므로 정규식으로 맞춘다.
    await waitFor(() => expect(screen.getByRole('option', { name: /폐데님/ })).toBeInTheDocument())
    const option = screen.getByRole('option', { name: /폐데님/ })
    expect(option).toHaveTextContent(/10\s*kg/)
    expect(option).not.toHaveTextContent(/30\s*kg/)
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('선택 시 보유량이 아니라 가용 수량을 올려보낸다', async () => {
    getMyMaterials.mockResolvedValue([DENIM])
    const user = userEvent.setup()
    const onChange = renderSelect()

    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument())
    await user.selectOptions(screen.getByRole('combobox'), '1')

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ materialId: 1, available: 10_000 }),
    )
  })

  it('가용 수량이 0 인 소재는 고를 수 없다', async () => {
    getMyMaterials.mockResolvedValue([LOCKED])
    renderSelect()

    // 목록에서 빼지 않는다 — 안 보이면 "내 소재가 사라졌다" 로 읽힌다.
    await waitFor(() => expect(screen.getByRole('option', { name: /폐면/ })).toBeInTheDocument())
    expect(screen.getByRole('option', { name: /폐면/ })).toBeDisabled()
  })

  it('전부 잠겨 있으면 이유를 알려준다', async () => {
    getMyMaterials.mockResolvedValue([LOCKED])
    renderSelect()

    await waitFor(() => expect(screen.getByText(/잠겨 있어/)).toBeInTheDocument())
  })

  it('보유 소재가 없으면 등록을 안내한다', async () => {
    getMyMaterials.mockResolvedValue([])
    renderSelect()

    await waitFor(() => expect(screen.getByText('보유한 소재가 없습니다')).toBeInTheDocument())
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })
})
