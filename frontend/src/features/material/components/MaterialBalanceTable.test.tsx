// 보유자별 잔량 표.
//
// **여기서 검증하는 것은 계산이 아니라 표시다.** 잔량 계산은 서버 소관이고
// MR !106·!114 가 26건으로 덮었다. 클라이언트는 재계산하지 않는다.
// 이 도메인에서 화면이 거짓을 말하는 경로는 계산 오류가 아니라 **단위 혼동**이다.
//
// 모킹 경계는 API 모듈 하나다 (WalletLoginButton.test.tsx 와 같은 방식).
// 그래야 훅 → 쿼리 → 렌더까지 실제로 통과한다.
//
// vitest 설정에 globals 가 없어 testing-library 자동 cleanup 이 등록되지 않는다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { MaterialBalance } from '../types/material.types.ts'
import { MaterialBalanceTable } from './MaterialBalanceTable.tsx'

const api = vi.hoisted(() => ({ getMaterialBalances: vi.fn() }))
vi.mock('../api/getMaterialBalances.ts', () => api)

const ADDRESS = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'

function balance(overrides: Partial<MaterialBalance> = {}): MaterialBalance {
  return {
    ownerId: 1,
    walletAddress: ADDRESS,
    quantity: 1_000,
    lockedQuantity: 0,
    available: 1_000,
    syncedAt: '2026-09-07T09:00:00',
    ...overrides,
  }
}

function renderTable(materialId: number | null = 7) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MaterialBalanceTable materialId={materialId} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getMaterialBalances.mockReset()
})

afterEach(cleanup)

describe('단위 표시', () => {
  it('gram 을 kg 로 바꿔 보여준다', async () => {
    // 서버는 gram 정수를 준다. 그대로 보여주면 20,500 이 20.5kg 인지
    // 20500kg 인지 알 수 없다 — 1000배 오해가 나는 지점이다.
    // 세 값을 다르게 둔다. 같으면 여러 셀에 같은 문자열이 나와 어느 칸을
    // 검증한 것인지 알 수 없다.
    api.getMaterialBalances.mockResolvedValue([
      balance({ quantity: 20_500, lockedQuantity: 500, available: 20_000 }),
    ])
    renderTable()

    expect(await screen.findByText('20.5 kg')).toBeInTheDocument()
    expect(screen.getByText('0.5 kg')).toBeInTheDocument()
    expect(screen.getByText('20 kg')).toBeInTheDocument()
    expect(screen.queryByText('20,500')).not.toBeInTheDocument()
  })

  it('딱 나누어떨어지는 값에 소수점을 붙이지 않는다', async () => {
    api.getMaterialBalances.mockResolvedValue([
      balance({ quantity: 1_000, lockedQuantity: 250, available: 750 }),
    ])
    renderTable()

    expect(await screen.findByText('1 kg')).toBeInTheDocument()
  })

  it('잠긴 수량과 판매 가능량을 각각 kg 로 보여준다', async () => {
    api.getMaterialBalances.mockResolvedValue([
      balance({ quantity: 1_000, lockedQuantity: 300, available: 700 }),
    ])
    renderTable()

    expect(await screen.findByText('1 kg')).toBeInTheDocument()
    expect(screen.getByText('0.3 kg')).toBeInTheDocument()
    expect(screen.getByText('0.7 kg')).toBeInTheDocument()
  })
})

describe('음수 available', () => {
  it('0 으로 깎지 않고 그대로 보여준다', async () => {
    // 잠금 합계가 보유량을 넘었다는 데이터 파손 신호다. Math.max(0, ...) 로
    // 감추면 화면은 정상으로 보이고 원인을 못 찾는다.
    api.getMaterialBalances.mockResolvedValue([
      balance({ quantity: 1_000, lockedQuantity: 1_100, available: -100 }),
    ])
    renderTable()

    expect(await screen.findByText('-0.1 kg')).toBeInTheDocument()
    expect(screen.queryByText('0 kg')).not.toBeInTheDocument()
  })
})

describe('세 가지 상태', () => {
  it('조회 중에는 로딩을 보여준다', () => {
    api.getMaterialBalances.mockReturnValue(new Promise(() => {}))
    renderTable()

    // variant="skeleton" 은 라벨을 aria-label 로만 준다 — 텍스트로는 찾을 수 없다.
    expect(screen.getByRole('status', { name: '보유 현황을 불러오는 중' })).toBeInTheDocument()
  })

  it('보유자가 없으면 EmptyState 다 — 에러가 아니다', async () => {
    // 사용자가 재시도를 반복하게 만들지 않는다.
    api.getMaterialBalances.mockResolvedValue([])
    renderTable()

    expect(await screen.findByText('보유자가 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })

  it('빈 목록 안내가 동기화 미반영 가능성을 알려준다', async () => {
    // material_balance 를 채우는 -376 이 막혀 있어 지금은 이 경우가 대부분이다.
    api.getMaterialBalances.mockResolvedValue([])
    renderTable()

    expect(await screen.findByText(/동기화가 아직 반영되지 않았을 수 있습니다/)).toBeInTheDocument()
  })

  it('실패하면 ErrorMessage 다 — EmptyState 가 아니다', async () => {
    api.getMaterialBalances.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '잔량을 불러오지 못했습니다.' }),
    )
    renderTable()

    expect(await screen.findByText('잔량을 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.queryByText('보유자가 없습니다')).not.toBeInTheDocument()
  })
})

describe('기준 시각', () => {
  it('syncedAt 을 표시한다', async () => {
    // 이 값이 없으면 "보유자 없음" 과 "동기화 전" 을 구분할 수 없다.
    api.getMaterialBalances.mockResolvedValue([balance()])
    renderTable()

    // 호출이 아니라 렌더를 기다려야 한다 — 호출 직후에는 아직 표가 없다.
    await screen.findByRole('table')
    const time = document.querySelector('time')
    expect(time).not.toBeNull()
    expect(time?.getAttribute('datetime')).toBe('2026-09-07T09:00:00')
  })
})

describe('요청 제어', () => {
  it('materialId 가 null 이면 요청하지 않는다', () => {
    // 라우트 파라미터 파싱 실패 시 /materials/undefined/balance 를 부르면 안 된다.
    renderTable(null)

    expect(api.getMaterialBalances).not.toHaveBeenCalled()
  })

  it('보유자가 여러 명이면 모두 보여준다', async () => {
    api.getMaterialBalances.mockResolvedValue([
      balance({ ownerId: 1, quantity: 700, available: 700 }),
      balance({ ownerId: 2, quantity: 300, available: 300 }),
    ])
    renderTable()

    await waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(3)) // 헤더 1 + 본문 2
  })
})
