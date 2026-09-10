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

// 컨트랙트 설정은 env 에서 온다. 테스트마다 갈아 끼울 수 있게 훅 경계에서 자른다.
const config = vi.hoisted(() => ({ configured: true }))
vi.mock('../../../config/contracts.ts', () => ({
  contracts: { productionRegistry: '0x65B4D067E929a7Ce64E8d5896bD8D4e0efE6DD3a' },
  isContractConfigured: () => config.configured,
}))

// 지갑 연결 상태.
const wallet = vi.hoisted(() => ({ address: null as string | null }))
vi.mock('../../wallet/hooks/useWallet.ts', () => ({
  useWallet: () => ({ address: wallet.address }),
}))

// 서명 자체는 MetaMask 의 일이다. 여기서 보는 것은 "서명을 시켰는가" 다.
const chain = vi.hoisted(() => ({ create: vi.fn() }))
vi.mock('../hooks/useCreateChainBatch.ts', () => ({
  useCreateChainBatch: () => ({
    ...chain,
    status: 'IDLE',
    hash: null,
    blockNumber: null,
    error: null,
    isBusy: false,
    isRejected: false,
  }),
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

    await user.click(screen.getByRole('button', { name: '온체인 배치 생성' }))

    expect(chain.create).toHaveBeenCalled()
  })

  it('확정 전에는 서명시키지 않고 이유를 적는다', () => {
    renderPanel(batch({ status: 'DRAFT', confirmedAt: null, totalUsedQuantity: 0 }))

    expect(screen.getByText(/배치를 확정하면 온체인에 기록할 수 있습니다/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '온체인 배치 생성' })).toBeDisabled()
  })

  // 이 테스트가 이 파일의 존재 이유다 — 배치가 서명자 주소를 스냅샷으로 갖고 있다.
  it('배치를 연 지갑이 아니면 막고 어느 계정인지 알려준다', () => {
    wallet.address = '0x9999999999999999999999999999999999999999'
    renderPanel()

    expect(screen.getByText(/0x2222…2222 지갑으로 열렸습니다/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '온체인 배치 생성' })).toBeDisabled()
  })

  it('지갑을 연결하지 않으면 막는다', () => {
    wallet.address = null
    renderPanel()

    expect(screen.getByText('지갑을 먼저 연결해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '온체인 배치 생성' })).toBeDisabled()
  })

  it('컨트랙트 주소가 없으면 막고 운영자에게 알리라고 한다', () => {
    config.configured = false
    renderPanel()

    expect(screen.getByText(/생산 컨트랙트 주소가 설정되지 않아/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '온체인 배치 생성' })).toBeDisabled()
  })

  it('이미 기록된 배치는 온체인 번호를 보여준다', () => {
    renderPanel(batch({ chainBatchId: 7 }))

    expect(screen.getByText(/7/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '온체인 배치 생성' })).not.toBeInTheDocument()
  })

  // 1단계만 보여주면 브랜드는 온체인 기록이 끝난 줄로 안다.
  it('남은 세 단계가 아직 준비 중이라는 것을 적는다', () => {
    renderPanel()

    expect(screen.getByText('투입 잠금·계획 기록', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('소재 소각', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('제품 개체 연결', { exact: false })).toBeInTheDocument()
    expect(screen.getAllByText('준비 중')).toHaveLength(3)
  })

  it('생산 개수를 문구에 넣어 무엇을 기록하는지 말한다', () => {
    renderPanel(batch({ producedQuantity: 25 }))

    expect(screen.getByText(/제품 25개의 생산 배치를 체인에 만듭니다/)).toBeInTheDocument()
  })
})
