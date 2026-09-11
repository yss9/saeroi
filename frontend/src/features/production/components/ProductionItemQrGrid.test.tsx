// QR 라벨 미리보기 격자.
//
// **인쇄될 이미지를 나란히 놓고 보는 화면이다.** 고정하는 것 넷.
//
//   1) **한 번에 전부 그리지 않는다.** 개체 하나당 이미지 요청 하나라, 50개짜리 배치를 다
//      그리면 화면을 열자마자 요청 50개가 나간다.
//   2) **남은 장수를 적는다.** "더 보기" 만 있으면 몇 장이 더 있는지 알 수 없다.
//   3) **한 장이 실패해도 나머지는 보인다.** 라벨 한 장 때문에 화면 전체가 죽으면 브랜드는
//      아무 것도 인쇄하지 못한다.
//   4) **저장 파일 이름이 개체 코드다.** 이름이 같으면 잘못된 라벨이 잘못된 제품에 붙는다.
//   5) **다 받기 전에는 인쇄시키지 않는다.** 아직 오지 않은 라벨은 종이에 빈 칸으로 나가고,
//      빈 라벨이 붙은 제품은 스캔되지 않는다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductionItem } from '../types/production.types.ts'
import { ProductionItemQrGrid } from './ProductionItemQrGrid.tsx'

const api = vi.hoisted(() => ({
  getProductionItems: vi.fn(),
  getProductionItemQr: vi.fn(),
}))
vi.mock('../api/productionBatchApi.ts', () => ({
  getProductionItems: api.getProductionItems,
  getProductionItemQr: api.getProductionItemQr,
}))

function item(serialNumber: number): ProductionItem {
  const id = 90 + serialNumber
  const token = String(serialNumber).padStart(32, '0')
  return {
    productItemId: id,
    itemCode: `PI-000031-${String(serialNumber).padStart(2, '0')}`,
    serialNumber,
    qrToken: token,
    qrUrl: `https://saeroi.example.com/qr/${token}`,
    status: 'IN_STOCK',
    createdAt: '2026-09-10T10:00:00',
  }
}

function items(count: number): ProductionItem[] {
  return Array.from({ length: count }, (_, index) => item(index + 1))
}

function renderGrid() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductionItemQrGrid batchId={3} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 그려진 QR 이미지 수. alt 로 센다 — 자리표시자와 섞이지 않는다. */
function drawnLabels(): HTMLElement[] {
  return screen.queryAllByAltText(/개체의 QR 코드$/)
}

beforeEach(() => {
  api.getProductionItems.mockResolvedValue(items(3))
  api.getProductionItemQr.mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductionItemQrGrid', () => {
  describe('인쇄', () => {
    it('그려진 장수를 적고 브라우저 인쇄를 연다', async () => {
      const print = vi.spyOn(window, 'print').mockImplementation(() => {})
      const user = renderGrid()
      await waitFor(() => expect(drawnLabels()).toHaveLength(3))

      await user.click(screen.getByRole('button', { name: '라벨 3장 인쇄' }))

      expect(print).toHaveBeenCalled()
    })

    // 이 테스트가 이 파일의 존재 이유다 — 빈 라벨이 붙은 제품은 스캔되지 않는다.
    it('라벨을 아직 받는 중이면 인쇄를 막는다', async () => {
      // 영원히 도착하지 않는 이미지. "받는 중" 상태를 붙잡아 둔다.
      api.getProductionItemQr.mockReturnValue(new Promise(() => {}))
      renderGrid()

      expect(await screen.findByRole('button', { name: '라벨을 불러오는 중' })).toBeDisabled()
    })

    // 20장짜리 배치에서 12장만 인쇄된 것을 인쇄물을 세어 보고 알게 하지 않는다.
    it('아직 불러오지 않은 장수를 인쇄 전에 알린다', async () => {
      api.getProductionItems.mockResolvedValue(items(20))
      renderGrid()
      await waitFor(() => expect(drawnLabels()).toHaveLength(12))

      expect(screen.getByRole('button', { name: '라벨 12장 인쇄' })).toBeInTheDocument()
      expect(screen.getByText(/8장은 아직 불러오지 않았습니다/)).toBeInTheDocument()
    })

    it('더 보기로 전부 불러오면 그 장수로 인쇄한다', async () => {
      api.getProductionItems.mockResolvedValue(items(20))
      const user = renderGrid()
      await waitFor(() => expect(drawnLabels()).toHaveLength(12))

      await user.click(screen.getByRole('button', { name: '8장 더 보기' }))

      expect(await screen.findByRole('button', { name: '라벨 20장 인쇄' })).toBeInTheDocument()
      expect(screen.queryByText(/아직 불러오지 않았습니다/)).not.toBeInTheDocument()
    })
  })

  it('개체마다 라벨을 그린다', async () => {
    renderGrid()

    await waitFor(() => expect(drawnLabels()).toHaveLength(3))
    expect(screen.getByText(/라벨 3장 중 3장을 미리 보고 있습니다/)).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다 — 개체 하나당 요청 하나다.
  it('한 번에 12장까지만 그리고 남은 장수를 알린다', async () => {
    api.getProductionItems.mockResolvedValue(items(20))
    renderGrid()

    await waitFor(() => expect(drawnLabels()).toHaveLength(12))
    // 보이지 않는 8장은 받지도 않았다.
    expect(api.getProductionItemQr).toHaveBeenCalledTimes(12)
    expect(screen.getByRole('button', { name: '8장 더 보기' })).toBeInTheDocument()
  })

  it('더 보기를 누르면 남은 라벨을 그린다', async () => {
    api.getProductionItems.mockResolvedValue(items(20))
    const user = renderGrid()
    await waitFor(() => expect(drawnLabels()).toHaveLength(12))

    await user.click(screen.getByRole('button', { name: '8장 더 보기' }))

    await waitFor(() => expect(drawnLabels()).toHaveLength(20))
    expect(screen.queryByRole('button', { name: /더 보기/ })).not.toBeInTheDocument()
  })

  // 라벨 한 장 때문에 화면 전체가 죽으면 브랜드는 아무 것도 인쇄하지 못한다.
  it('한 장이 실패해도 나머지 라벨은 보여준다', async () => {
    api.getProductionItemQr.mockImplementation((_batchId: number, productItemId: number) =>
      productItemId === 92
        ? Promise.reject(createAppError(ERROR_KIND.API_ERROR, { status: 500 }))
        : Promise.resolve(new Blob(['png'], { type: 'image/png' })),
    )
    renderGrid()

    await waitFor(() => expect(drawnLabels()).toHaveLength(2))
    expect(await screen.findByRole('alert')).toHaveTextContent(/QR 이미지를 만들지 못했습니다/)
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('개체 코드로 된 파일 이름으로 저장하게 한다', async () => {
    api.getProductionItems.mockResolvedValue([item(1)])
    renderGrid()

    const save = await screen.findByRole('link', { name: '저장' })
    expect(save).toHaveAttribute('download', 'PI-000031-01-qr.png')
  })

  it('공개 경로라는 사실을 격자 위에 적어 둔다', async () => {
    renderGrid()

    expect(await screen.findByText(/누구나 열 수 있는 공개 경로/)).toBeInTheDocument()
  })

  it('확정됐는데 개체가 없으면 이상 상황임을 알린다', async () => {
    api.getProductionItems.mockResolvedValue([])
    renderGrid()

    expect(await screen.findByText('만들어진 제품 개체가 없습니다')).toBeInTheDocument()
  })

  // 남의 배치는 403 이다. 빈 배열과 구분되어야 한다.
  it('개체 조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getProductionItems.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'PRODUCTION_FORBIDDEN',
        message: '이 배치를 조회할 권한이 없습니다.',
        status: 403,
      }),
    )
    renderGrid()

    expect(await screen.findByRole('alert')).toHaveTextContent('조회할 권한이 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
