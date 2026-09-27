// 생산 온체인 기록 — 진행 상황과 버튼 하나.
//
// 예전에는 단계마다 "진행" 버튼이 있어 생산 하나에 다섯 번을 눌러야 했다. 이제는 버튼 하나가
// 남은 단계를 차례로 진행한다. 여기서 보는 것은 둘이다.
//
//   1) **어디까지 끝났는지는 체인이 정한다.** 서명은 됐는데 서버 보고가 실패하면 서버 기준으로는
//      영원히 "아직" 이다 — 체인 상태로 완료를 표시한다.
//   2) **누르는 곳은 하나다.** 단계별 버튼이 없고, 남은 게 있을 때만 이어서 진행 버튼이 있다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductionBatch } from '../types/production.types.ts'
import { ProductionChainSteps } from './ProductionChainSteps.tsx'

// 체인 읽기. 단계 판정의 근거라 테스트마다 갈아 끼운다.
const chain = vi.hoisted(() => ({
  status: 'DRAFT' as string,
  linkedItemCount: 0,
}))
vi.mock('../hooks/useProductionChainStatus.ts', () => ({
  useProductionChainStatus: () => ({
    data: { status: chain.status, linkedItemCount: chain.linkedItemCount },
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
}))

const flowRun = vi.hoisted(() => vi.fn())
function flow(overrides: Record<string, unknown> = {}) {
  return { run: flowRun, running: false, currentStep: null, error: null, ...overrides }
}

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3,
    chainBatchId: 7,
    batchCode: 'PB-000003',
    batchName: '2026 가을 1차',
    productModelId: 31,
    producedQuantity: 2,
    producerId: 5,
    producerWallet: '0x2222222222222222222222222222222222222222',
    status: 'CONFIRMED',
    totalUsedQuantity: 8_000,
    virginUsedQuantity: 0,
    upcycledUsedQuantity: 8_000,
    upcycleRatio: 100,
    evidenceHash: null,
    manifestUrl: null,
    createdAt: '2026-09-09T10:00:00',
    confirmedAt: '2026-09-09T12:00:00',
    inputs: [],
    ...overrides,
  }
}

function renderSteps(value: ProductionBatch = batch(), extra: { blockedReason?: string; flow?: Record<string, unknown> } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <ProductionChainSteps batch={value} flow={flow(extra.flow) as never} blockedReason={extra.blockedReason} />
    </QueryClientProvider>,
  )
  return { user: userEvent.setup(), ...rendered }
}

function stepRow(label: string): HTMLElement {
  const row = screen.getByText(label).closest('li')
  if (row === null) throw new Error(`단계를 찾지 못했다: ${label}`)
  return row
}

beforeEach(() => {
  chain.status = 'DRAFT'
  chain.linkedItemCount = 0
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductionChainSteps', () => {
  it('단계마다 버튼을 두지 않고, 남은 기록을 한 번에 이어서 진행한다', async () => {
    chain.status = 'DRAFT'
    const { user } = renderSteps()

    expect(stepRow('생산 확정')).toHaveClass('chain-steps__done')
    expect(stepRow('생산 이력 기록')).toHaveClass('chain-steps__done')
    expect(stepRow('생산에 사용할 소재 확정').querySelector('button')).toBeNull()
    expect(screen.getAllByRole('button')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: '남은 기록 이어서 진행' }))
    expect(flowRun).toHaveBeenCalledOnce()
  })

  it('체인 상태로 끝난 단계를 표시한다', () => {
    chain.status = 'CONFIRMED'
    renderSteps()

    expect(stepRow('생산에 사용할 소재 확정')).toHaveClass('chain-steps__done')
    expect(stepRow('사용 소재 차감·자투리 등록')).toHaveClass('chain-steps__done')
    expect(stepRow('생산 제품 등록')).not.toHaveClass('chain-steps__done')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('모두 끝났으면 버튼이 없다', () => {
    chain.status = 'CONFIRMED'
    chain.linkedItemCount = 2
    renderSteps()

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('생산 이력 기록이 완료되었습니다.')
  })

  it('진행 중에는 지금 단계를 표시한다', () => {
    chain.status = 'DRAFT'
    renderSteps(batch(), { flow: { running: true, currentStep: 'CONSUME' } })

    expect(stepRow('사용 소재 차감·자투리 등록')).toHaveTextContent('진행 중')
    expect(stepRow('생산에 사용할 소재 확정')).toHaveClass('chain-steps__done')
  })

  it('서명할 수 없으면 막고 이유를 적는다', () => {
    renderSteps(batch(), { blockedReason: '지갑을 먼저 연결해 주세요.' })

    expect(screen.getByRole('button', { name: '남은 기록 이어서 진행' })).toBeDisabled()
    expect(screen.getByText('지갑을 먼저 연결해 주세요.')).toBeInTheDocument()
  })

  it('온체인 배치가 없으면 이력 기록부터 시작한다', () => {
    renderSteps(batch({ chainBatchId: null }))

    expect(screen.getByRole('button', { name: '생산 이력 기록' })).toBeInTheDocument()
  })

  it('확정 전에는 아무것도 그리지 않는다', () => {
    const { container } = renderSteps(batch({ status: 'DRAFT', chainBatchId: null }))

    expect(container).toBeEmptyDOMElement()
  })

  it('멈춘 이유를 보여준다', () => {
    renderSteps(batch(), { flow: { error: '서명을 취소했어요. 다시 누르면 남은 단계부터 이어서 진행해요.' } })

    expect(screen.getByText('서명을 취소했어요. 다시 누르면 남은 단계부터 이어서 진행해요.')).toBeInTheDocument()
  })
})
