// 생산 온체인 기록 (1단계: createBatch).
//
// 서명은 지갑이 하는 일이라 여기서 검증하는 것은 **"서명을 시킬 수 있는 상태인지"** 다.
// 틀리면 안 되는 지점 넷.
//
//   1) **확정 전에는 서명시키지 않는다.** 투입이 더 바뀔 수 있고, 바뀐 뒤에도 체인에는 옛
//      내용이 남는다. 서버도 확정된 배치만 받는다.
//   2) **연결된 지갑이 배치의 producerWallet 과 같아야 한다.** 다른 계정으로 서명하면
//      배치에 적힌 주소와 어긋난 기록이 남는다.
//   3) **컨트랙트 주소가 없으면 막는다** — 서버가 BLOCKCHAIN_NOT_CONFIGURED 로 거절한다.
//   4) **못 누르는 이유를 적는다.** 버튼만 잠그면 사용자는 자기가 뭘 잘못했다고 생각한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductionBatch } from '../types/production.types.ts'
import { ProductionChainPanel } from './ProductionChainPanel.tsx'

// 진행 목록과 버튼 하나는 ProductionChainSteps 가 그린다(그쪽 동작은 ProductionChainSteps.test).
// 이 파일은 패널이 "못 누르는 이유" 를 제대로 계산해 넘기는지 본다.
vi.mock('./ProductionChainSteps.tsx', () => ({
  ProductionChainSteps: ({ blockedReason, flow }: { blockedReason?: string; flow: { run: () => void } }) => (
    <div>
      {blockedReason && <p>{blockedReason}</p>}
      <button disabled={Boolean(blockedReason)} onClick={flow.run}>남은 기록 이어서 진행</button>
    </div>
  ),
}))

// 컨트랙트 설정은 env 에서 온다. 테스트마다 갈아 끼울 수 있게 훅 경계에서 자른다.
const config = vi.hoisted(() => ({ configured: true }))
vi.mock('../../../config/contracts.ts', () => ({
  contracts: { productionRegistry: '0x65B4D067E929a7Ce64E8d5896bD8D4e0efE6DD3a' },
  isContractConfigured: () => config.configured,
}))

// 지갑 연결 상태.
const wallet = vi.hoisted(() => ({ address: null as string | null }))
vi.mock('../../wallet/hooks/useWallet.ts', () => ({
  useSwitchChainNotice: () => ({ message: null, report: () => {} }),
  useWallet: () => ({ address: wallet.address }),
}))

// 서명 자체는 MetaMask 의 일이다. 여기서 보는 것은 "진행을 시켰는가" 다.
const chain = vi.hoisted(() => ({ run: vi.fn() }))
vi.mock('../hooks/useProductionFlow.ts', () => ({
  useProductionFlow: () => ({ run: chain.run, running: false, currentStep: null, error: null }),
}))

const PRODUCER = '0x2222222222222222222222222222222222222222'

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3,
    chainBatchId: null,
    batchCode: 'PB-000003',
    batchName: '2026 가을 토트백 1차',
    productModelId: 31,
    producedQuantity: 10,
    producerId: 5,
    producerWallet: PRODUCER,
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

function renderPanel(value: ProductionBatch = batch()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductionChainPanel batch={value} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  config.configured = true
  wallet.address = PRODUCER
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ProductionChainPanel', () => {
  it('확정된 배치에서 서명할 수 있다', async () => {
    const user = renderPanel()

    await user.click(screen.getByRole('button', { name: '남은 기록 이어서 진행' }))

    expect(chain.run).toHaveBeenCalled()
  })

  it('확정 전에는 서명시키지 않고 이유를 적는다', () => {
    renderPanel(batch({ status: 'DRAFT', confirmedAt: null, totalUsedQuantity: 0 }))

    expect(screen.getByText(/생산을 확정하면 이력을 기록할 수 있습니다/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다 — 배치가 서명자 주소를 스냅샷으로 갖고 있다.
  it('배치를 연 지갑이 아니면 막고 어느 계정인지 알려준다', () => {
    wallet.address = '0x9999999999999999999999999999999999999999'
    renderPanel()

    expect(screen.getByText(/이 생산을 시작한 0x2222…2222 지갑/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '남은 기록 이어서 진행' })).toBeDisabled()
  })

  it('지갑을 연결하지 않으면 막는다', () => {
    wallet.address = null
    renderPanel()

    expect(screen.getByText('지갑을 먼저 연결해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '남은 기록 이어서 진행' })).toBeDisabled()
  })

  it('컨트랙트 주소가 없으면 막고 운영자에게 알리라고 한다', () => {
    config.configured = false
    renderPanel()

    expect(screen.getByText(/생산 이력 기록 기능을 사용할 수 없습니다/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '남은 기록 이어서 진행' })).toBeDisabled()
  })

  it('이미 기록된 배치는 온체인 번호를 보여준다', () => {
    renderPanel(batch({ chainBatchId: 7 }))

    expect(screen.getByText('기록 완료')).toBeInTheDocument()
  })

  // 남은 단계는 ProductionChainSteps 가 그린다. 이 패널은 1단계만 책임진다.
  it('남은 단계를 그릴 자리를 내준다', () => {
    renderPanel(batch({ chainBatchId: 7 }))

    // 1단계가 끝났으면 번호를 보여주고 서명 버튼을 거둔다.
    expect(screen.getByText('기록 완료')).toBeInTheDocument()
  })

  it('생산 개수를 문구에 넣어 무엇을 기록하는지 말한다', () => {
    renderPanel(batch({ producedQuantity: 25 }))

    expect(screen.getByText(/제품 25개의 생산 정보를 안전하게 저장합니다/)).toBeInTheDocument()
  })
})
