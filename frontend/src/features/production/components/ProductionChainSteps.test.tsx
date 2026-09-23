// 생산 온체인 2~4단계.
//
// 서명은 지갑의 일이라 여기서 보는 것은 **"지금 어느 단계를 시킬 수 있는가"** 다.
// 틀리면 안 되는 지점 넷.
//
//   1) **다음에 할 수 있는 일은 체인이 정한다.** 서버 응답으로는 3·4단계가 끝났는지 알 수
//      없고, 서명은 됐는데 서버 보고가 실패하면 서버 기준으로는 영원히 "아직" 이다. 그
//      상태에서 다시 서명하면 컨트랙트가 거절해 화면이 막다른 길이 된다.
//   2) **순서를 건너뛸 수 없다.** 컨트랙트가 revert 하고 서버도 보고 순서를 검사한다.
//   3) **개체 수가 맞아야 4단계를 연다.** 개수가 어긋나면 revert 하므로, 서명까지 시키고
//      실패하게 두지 않는다.
//   4) **못 누르는 이유를 적는다.** 잠긴 버튼은 이유를 말하지 않는다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductionBatch, ProductionItem } from '../types/production.types.ts'
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

// 서명과 서버 보고는 훅 경계에서 자른다. 여기서 볼 것은 "서명을 시켰는가" 다.
const steps = vi.hoisted(() => ({ plan: vi.fn(), confirm: vi.fn(), link: vi.fn() }))
const idle = {
  status: 'IDLE',
  hash: null,
  error: null,
  isBusy: false,
  isRejected: false,
}
vi.mock('../hooks/useProductionChainSteps.ts', () => ({
  useSubmitProductionPlan: () => ({ ...idle, run: steps.plan }),
  useConfirmProductionOnChain: () => ({ ...idle, run: steps.confirm }),
  useLinkProductionItems: () => ({ ...idle, run: steps.link }),
}))

const items = vi.hoisted(() => ({ data: [] as ProductionItem[] }))
vi.mock('../hooks/useProductionItems.ts', () => ({
  useProductionItems: () => ({ data: items.data }),
}))

function item(serialNumber: number): ProductionItem {
  return {
    productItemId: 90 + serialNumber,
    itemCode: `PI-${serialNumber}`,
    serialNumber,
    qrToken: 'a'.repeat(32),
    qrUrl: `https://saeroi.example.com/qr/${'a'.repeat(32)}`,
    status: 'IN_STOCK',
    createdAt: '2026-09-10T10:00:00',
  }
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

function renderSteps(value: ProductionBatch = batch()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductionChainSteps batch={value} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 단계 이름으로 그 줄의 서명 버튼을 찾는다. */
function signButton(label: string): HTMLElement {
  const row = screen.getByText(label).closest('li')
  if (row === null) throw new Error(`단계를 찾지 못했다: ${label}`)
  const button = row.querySelector('button')
  if (button === null) throw new Error(`서명 버튼이 없다: ${label}`)
  return button
}

beforeEach(() => {
  chain.status = 'DRAFT'
  chain.linkedItemCount = 0
  items.data = [item(1), item(2)]
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductionChainSteps', () => {
  // 이 테스트가 이 파일의 존재 이유다 — 순서를 건너뛰면 컨트랙트가 revert 한다.
  it('체인이 DRAFT 면 2단계만 열어 준다', async () => {
    const user = renderSteps()

    expect(signButton('생산에 사용할 소재 확정')).toBeEnabled()
    expect(signButton('사용 소재 차감·자투리 등록')).toBeDisabled()
    expect(signButton('생산 제품 등록')).toBeDisabled()

    await user.click(signButton('생산에 사용할 소재 확정'))
    expect(steps.plan).toHaveBeenCalled()
  })

  it('체인이 SIGNING 이면 3단계를 열고 2단계는 완료로 둔다', async () => {
    chain.status = 'SIGNING'
    const user = renderSteps()

    expect(screen.getAllByText('완료').length).toBeGreaterThan(0)
    await user.click(signButton('사용 소재 차감·자투리 등록'))

    expect(steps.confirm).toHaveBeenCalled()
  })

  it('체인이 CONFIRMED 면 4단계를 연다', async () => {
    chain.status = 'CONFIRMED'
    const user = renderSteps()

    await user.click(signButton('생산 제품 등록'))

    expect(steps.link).toHaveBeenCalled()
  })

  // 개수가 어긋나면 컨트랙트가 revert 한다. 서명까지 시키고 실패하게 두지 않는다.
  it('개체 수가 생산 수량과 다르면 4단계를 막고 이유를 적는다', () => {
    chain.status = 'CONFIRMED'
    items.data = [item(1)] // 생산 수량은 2개다
    renderSteps()

    expect(signButton('생산 제품 등록')).toBeDisabled()
    expect(screen.getByText(/등록할 생산 제품 수가 맞지 않습니다 \(1 \/ 2개\)/)).toBeInTheDocument()
  })

  it('소각 전에는 개체 연결을 막고 앞 단계를 가리킨다', () => {
    chain.status = 'SIGNING'
    renderSteps()

    expect(signButton('생산 제품 등록')).toBeDisabled()
    expect(screen.getByText('사용 소재 차감과 자투리 등록을 먼저 마쳐 주세요.')).toBeInTheDocument()
  })

  it('개체가 연결됐으면 마지막 단계를 완료로 둔다', () => {
    chain.status = 'CONFIRMED'
    chain.linkedItemCount = 2
    renderSteps()

    const row = screen.getByText('생산 제품 등록').closest('li') as HTMLElement
    expect(row.querySelector('button')).toBeNull()
  })

  // 1단계를 마치기 전에는 뒤의 단계가 존재하지 않는다.
  it('온체인 배치가 없으면 아무것도 그리지 않는다', () => {
    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <ProductionChainSteps batch={batch({ chainBatchId: null })} />
      </QueryClientProvider>,
    )

    expect(container).toBeEmptyDOMElement()
  })
})
