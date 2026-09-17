// 생산 배치 상세 — 투입과 확정.
//
// **확정은 이 서비스에서 가장 무거운 버튼이다.** 한 번의 요청이 소재를 소비하고, 제품
// 개체를 만들고, 자투리를 새 소재로 발행하고, 업사이클 비율을 굳힌다. 되돌릴 수 없다.
//
// 틀리면 안 되는 지점 넷.
//
//   1) **확인 없이 확정되지 않는다.**
//   2) **투입이 없으면 확정 버튼을 내주지 않는다** (서버도 PRODUCTION_NO_INPUT 로 거절).
//   3) **제품 사용량이 전부 0 이면 확정할 수 없다** (PRODUCTION_NO_PRODUCT_MATERIAL).
//   4) **확정 응답의 inputs 가 빈 배열이라 그대로 덮으면 화면에서 투입이 사라진다.**
//      투입이 지워진 것이 아니라 확정 서비스가 목록을 다시 만들지 않는 것이다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductionBatch, ProductionInput } from '../types/production.types.ts'
import { productionKeys } from '../api/productionKeys.ts'
import { ProductionBatchDetail } from './ProductionBatchDetail.tsx'

const api = vi.hoisted(() => ({
  getProductionBatch: vi.fn(),
  confirmProductionBatch: vi.fn(),
  addProductionInputs: vi.fn(),
  removeProductionInput: vi.fn(),
  getMyMaterials: vi.fn(),
  getMyProductModels: vi.fn(),
  assignProductModel: vi.fn(),
}))
vi.mock('../api/productionBatchApi.ts', () => ({
  getProductionBatch: api.getProductionBatch,
  confirmProductionBatch: api.confirmProductionBatch,
  assignProductModel: api.assignProductModel,
}))
// 완성 제품 선택이 이 화면에 있다 — 목록을 못 받으면 오류가 한 줄 더 뜬다.
vi.mock('../../product/api/getMyProductModels.ts', () => ({
  getMyProductModels: api.getMyProductModels,
}))
vi.mock('../api/addProductionInputs.ts', () => ({
  addProductionInputs: api.addProductionInputs,
}))
vi.mock('../api/removeProductionInput.ts', () => ({
  removeProductionInput: api.removeProductionInput,
}))
vi.mock('../../inventory/api/getMyMaterials.ts', () => ({
  getMyMaterials: api.getMyMaterials,
}))

function input(overrides: Partial<ProductionInput> = {}): ProductionInput {
  return {
    productionInputId: 51,
    materialCode: 'MAT-000011',
    chainLockId: null,
    materialId: 11,
    inputQuantity: 10_000,
    productUsedQuantity: 8_000,
    reusableScrapQuantity: 1_500,
    wasteQuantity: 500,
    wasteReason: null,
    materialLockId: 71,
    ...overrides,
  }
}

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3,
    batchCode: 'PB-000003',
    batchName: '2026 가을 토트백 1차',
    productModelId: 31,
    producedQuantity: 10,
    producerId: 5,
    producerWallet: '0x2222222222222222222222222222222222222222',
    // 온체인 서명 전이다 — createBatch 를 알리기 전까지 null 이다.
    chainBatchId: null,
    status: 'DRAFT',
    // 확정 전에는 서버가 전부 0 으로 준다.
    totalUsedQuantity: 0,
    virginUsedQuantity: 0,
    upcycledUsedQuantity: 0,
    upcycleRatio: 0,
    evidenceHash: null,
    manifestUrl: null,
    createdAt: '2026-09-09T10:00:00',
    confirmedAt: null,
    inputs: [input()],
    ...overrides,
  }
}

function renderDetail(seed?: ProductionBatch) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  // 생산 시작이 상세 캐시에 값을 심어 두고 넘어오는 경로를 그대로 재현한다.
  if (seed) queryClient.setQueryData(productionKeys.detail(3), seed)
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductionBatchDetail batchId={3} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getProductionBatch.mockResolvedValue(batch())
  api.getMyMaterials.mockResolvedValue([])
  api.getMyProductModels.mockResolvedValue([
    { productModelId: 31, name: '업사이클 데님 토트백', status: 'DRAFT', stockQuantity: 0 },
  ])
  // 확정 응답은 투입을 담지 않는다. 이게 4번 항목의 근거다.
  api.confirmProductionBatch.mockResolvedValue(
    batch({
      status: 'CONFIRMED',
      confirmedAt: '2026-09-09T12:00:00',
      totalUsedQuantity: 8_000,
      upcycledUsedQuantity: 8_000,
      upcycleRatio: 100,
      inputs: [],
    }),
  )
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ProductionBatchDetail', () => {
  it('배치 요약과 투입 합계를 보여준다', async () => {
    renderDetail()

    expect(await screen.findByRole('heading', { name: '2026 가을 토트백 1차' })).toBeInTheDocument()
    expect(screen.getByText('PB-000003')).toBeInTheDocument()
    // 숫자와 단위를 다른 태그로 나눠 그린다(숫자를 크게, 단위를 작게).
    expect(screen.getByText('10')).toBeInTheDocument()
    // 배치의 totalUsedQuantity 는 0 이지만 투입 합계는 10kg 이다.
    expect(screen.getByText('작성 중')).toBeInTheDocument()
  })

  // 확인 모달을 두지 않으므로 이 줄이 마지막 경고다 — 되돌릴 수 없는 동작이다.
  it('확정이 무엇을 하는지 버튼 위에 적는다', async () => {
    renderDetail()

    expect(await screen.findByText(/8 kg이 소비되고/)).toBeInTheDocument()
    expect(screen.getByText(/제품 10개가/)).toBeInTheDocument()
    expect(screen.getByText('되돌릴 수 없습니다.')).toBeInTheDocument()
  })

  it('누르면 바로 확정한다', async () => {
    const user = renderDetail()

    await user.click(await screen.findByRole('button', { name: '생산 확정' }))

    await waitFor(() => expect(api.confirmProductionBatch).toHaveBeenCalledWith(3))
  })

  it('투입이 없으면 확정할 수 없고 무엇을 해야 하는지 알린다', async () => {
    api.getProductionBatch.mockResolvedValue(batch({ inputs: [] }))
    renderDetail()

    // 안내는 담는 자리(담긴 투입)에 있다. 확정 칸에 또 적으면 같은 말이 화면 위아래로
    // 흩어져 읽는 사람이 오간다.
    expect(
      await screen.findByText(/투입한 소재가 없어 생산을 확정할 수 없습니다/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '생산 확정' })).toBeDisabled()
  })

  // 전량이 자투리·폐기면 제품을 만들 수 없다 (PRODUCTION_NO_PRODUCT_MATERIAL).
  it('제품에 쓰인 수량이 없으면 확정 버튼을 잠근다', async () => {
    api.getProductionBatch.mockResolvedValue(
      batch({
        inputs: [
          input({ productUsedQuantity: 0, reusableScrapQuantity: 9_000, wasteQuantity: 1_000 }),
        ],
      }),
    )
    renderDetail()

    await waitFor(() =>
      expect(screen.getByRole('button', { name: '생산 확정' })).toBeDisabled(),
    )
  })

  // 확정 응답의 inputs 가 빈 배열이라, 그대로 캐시에 넣으면 방금 확정한 화면에서
  // 투입이 사라진다. 투입이 지워진 것이 아니다.
  it('확정 후에도 담긴 투입이 화면에 남는다', async () => {
    // 확정 응답은 투입을 담지 않지만(inputs: []), 상세 조회는 담아 준다.
    // 화면이 응답을 그대로 덮으면 그 사이에 투입이 사라진다 — 캐시 병합이 그것을 막는다.
    const confirmed = batch({
      status: 'CONFIRMED',
      confirmedAt: '2026-09-09T12:00:00',
      totalUsedQuantity: 8_000,
      upcycledUsedQuantity: 8_000,
      upcycleRatio: 100,
    })
    api.getProductionBatch.mockResolvedValueOnce(batch()).mockResolvedValue(confirmed)
    const user = renderDetail()

    await user.click(await screen.findByRole('button', { name: '생산 확정' }))

    expect(await screen.findByText('확정 결과')).toBeInTheDocument()
    // 투입 목록이 비지 않는다.
    expect(screen.queryByText('투입한 소재가 없습니다')).not.toBeInTheDocument()
  })

  it('확정된 배치는 투입을 바꿀 수 없다', async () => {
    api.getProductionBatch.mockResolvedValue(
      batch({
        status: 'CONFIRMED',
        confirmedAt: '2026-09-09T12:00:00',
        totalUsedQuantity: 8_000,
        upcycledUsedQuantity: 8_000,
        upcycleRatio: 100,
      }),
    )
    renderDetail()

    expect(await screen.findByText('확정')).toBeInTheDocument()
    // 선택 UI 와 확정 버튼이 모두 사라진다 — 서버가 거절할 요청을 만들 수 없게 한다.
    expect(screen.queryByLabelText('투입할 소재')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '생산 확정' })).not.toBeInTheDocument()
  })

  it('확정된 배치는 업사이클 비율과 다음 할 일을 보여준다', async () => {
    api.getProductionBatch.mockResolvedValue(
      batch({
        status: 'CONFIRMED',
        confirmedAt: '2026-09-09T12:00:00',
        totalUsedQuantity: 8_000,
        upcycledUsedQuantity: 8_000,
        upcycleRatio: 100,
      }),
    )
    renderDetail()

    expect(await screen.findByText('100.00%')).toBeInTheDocument()
    // 확정의 목적은 판매다. 다음 화면으로 데려간다.
    expect(screen.getByRole('link', { name: '내 제품에서 판매를 시작' })).toHaveAttribute(
      'href',
      '/brand/products',
    )
  })

  it('확정이 실패하면 사유를 보여준다', async () => {
    api.confirmProductionBatch.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'PRODUCTION_INSUFFICIENT_BALANCE',
        message: '보유 수량이 투입량보다 적습니다.',
        status: 422,
      }),
    )
    const user = renderDetail()

    await user.click(await screen.findByRole('button', { name: '생산 확정' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('보유 수량이 투입량보다 적습니다')
  })

  // 브랜드의 순서는 "생산을 끝내고 → 그 결과물에 제품 정보를 붙인다" 다. 서버도 확정에
  // 제품을 요구하지 않는다 — 제품 등록을 기다리느라 생산을 못 닫게 두지 않는다.
  it('제품이 없어도 확정할 수 있다', async () => {
    api.getProductionBatch.mockResolvedValue(batch({ productModelId: null }))
    renderDetail()

    expect(await screen.findByRole('button', { name: '생산 확정' })).toBeEnabled()
  })

  it('배치 조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getProductionBatch.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'PRODUCTION_BATCH_NOT_FOUND',
        message: '생산 배치를 찾을 수 없습니다.',
        status: 404,
      }),
    )
    renderDetail()

    expect(await screen.findByRole('alert')).toHaveTextContent('생산 배치를 찾을 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})

// 생산을 시작하면서 소재를 담고 온 사람이 대부분인데, 빈 선택 칸이 담긴 목록 위에
// 펼쳐져 있으면 "안 담겼나" 로 읽힌다.
it('투입이 있으면 소재 선택 칸을 접어 둔다', async () => {
  api.getProductionBatch.mockResolvedValue(batch({ inputs: [input()] }))
  renderDetail()

  expect(await screen.findByRole('button', { name: '+ 소재 더 담기' })).toBeInTheDocument()
  expect(screen.queryByText('보유한 소재가 없습니다')).not.toBeInTheDocument()
})

// 생산 시작이 빈 배치를 캐시에 심어 두고 오는 경로. 투입이 도착하면 선택 칸은 닫혀야
// 한다 — 방금 소재를 골라 시작한 사람에게 "또 담으라" 고 말하는 꼴이 된다.
it('먼저 그린 값이 비어 있어도 투입이 도착하면 선택 칸을 접는다', async () => {
  api.getProductionBatch.mockResolvedValue(batch({ inputs: [input()] }))
  renderDetail(batch({ inputs: [] }))

  expect(await screen.findByRole('button', { name: '+ 소재 더 담기' })).toBeInTheDocument()
})

// 소재 없이 시작한 생산은 여기서만 담을 수 있다 — 접어 두면 막다른 길이 된다.
it('투입이 없으면 소재 선택 칸을 열어 둔다', async () => {
  api.getProductionBatch.mockResolvedValue(batch({ inputs: [] }))
  renderDetail()

  // 보유 소재가 없는 고정값이라 선택 칸 대신 그 이유가 열린 채로 보인다.
  expect(await screen.findByText('보유한 소재가 없습니다')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '+ 소재 더 담기' })).not.toBeInTheDocument()
})
