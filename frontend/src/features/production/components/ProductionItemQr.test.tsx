// 개체 QR 이미지 생성·저장.
//
// **브랜드가 제품에 붙일 라벨을 얻는 지점이다.** 여기서 틀리면 잘못된 이미지가 물건에
// 인쇄되어 나가고, 그건 되돌릴 수 없다. 고정하는 것 넷.
//
//   1) **누르기 전에는 받지 않는다.** 배치 하나에 개체가 수십 개다 — 목록을 열자마자 전부
//      받으면 보지도 않을 PNG 수십 장이 내려온다.
//   2) **저장 파일 이름이 개체 코드다.** 라벨 수십 장을 한 폴더에 받는데 이름이 같으면
//      어느 것이 어느 개체인지 알 수 없고, 잘못된 라벨이 잘못된 제품에 붙는다.
//   3) **실패해도 저장 버튼을 두지 않는다.** 빈 파일을 받아 인쇄하는 것이 최악이다.
//   4) **실패 이유를 적는다.** blob 응답이라 서버 문구를 읽을 수 없어 화면이 만들어야 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductionItem } from '../types/production.types.ts'
import { ProductionItemQr } from './ProductionItemQr.tsx'

const api = vi.hoisted(() => ({ getProductionItemQr: vi.fn() }))
vi.mock('../api/productionBatchApi.ts', () => ({
  getProductionItemQr: api.getProductionItemQr,
}))

const TOKEN = 'a1b2c3d4e5f60718293a4b5c6d7e8f90'

const ITEM: ProductionItem = {
  productItemId: 91,
  itemCode: 'PI-000031-01',
  serialNumber: 1,
  qrToken: TOKEN,
  qrUrl: `https://saeroi.example.com/qr/${TOKEN}`,
  status: 'IN_STOCK',
  createdAt: '2026-09-10T10:00:00',
}

function renderQr(item: ProductionItem = ITEM) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductionItemQr batchId={3} item={item} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getProductionItemQr.mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductionItemQr', () => {
  // 이 테스트가 이 파일의 존재 이유다.
  it('누르기 전에는 이미지를 받지 않는다', () => {
    renderQr()

    expect(screen.getByRole('button', { name: 'QR 생성' })).toBeInTheDocument()
    expect(api.getProductionItemQr).not.toHaveBeenCalled()
  })

  it('누르면 그 개체의 QR 을 받아 보여준다', async () => {
    const user = renderQr()

    await user.click(screen.getByRole('button', { name: 'QR 생성' }))

    expect(await screen.findByAltText('PI-000031-01 개체의 QR 코드')).toBeInTheDocument()
    expect(api.getProductionItemQr.mock.calls[0]).toEqual([3, 91])
  })

  // 이름이 같으면 잘못된 라벨이 잘못된 제품에 붙는다.
  it('개체 코드로 된 파일 이름으로 저장하게 한다', async () => {
    const user = renderQr()

    await user.click(screen.getByRole('button', { name: 'QR 생성' }))

    const save = await screen.findByRole('link', { name: '이미지 저장' })
    expect(save).toHaveAttribute('download', 'PI-000031-01-qr.png')
  })

  // 토큰은 공개 경로의 열쇠다. 파일 이름은 목록·메신저·인쇄 의뢰서에 그대로 드러난다.
  it('파일 이름에 QR 토큰을 넣지 않는다', async () => {
    const user = renderQr()

    await user.click(screen.getByRole('button', { name: 'QR 생성' }))

    const save = await screen.findByRole('link', { name: '이미지 저장' })
    expect(save.getAttribute('download')).not.toContain(TOKEN)
  })

  it('무엇이 인쇄되는지, 스캔하면 무엇이 열리는지 적는다', async () => {
    const user = renderQr()

    await user.click(screen.getByRole('button', { name: 'QR 생성' }))

    expect(await screen.findByText(/누구나 열 수 있는 공개 주소/)).toBeInTheDocument()
  })

  // 빈 파일을 받아 인쇄하는 것이 최악이다.
  it('실패하면 저장 버튼을 두지 않고 이유를 적는다', async () => {
    api.getProductionItemQr.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { status: 403 }),
    )
    const user = renderQr()

    await user.click(screen.getByRole('button', { name: 'QR 생성' }))

    // blob 응답이라 서버 문구(code·message)를 읽을 수 없다 — 상태 코드로 만든다.
    expect(await screen.findByRole('alert')).toHaveTextContent(/생산을 시작한 계정으로 로그인/)
    expect(screen.queryByRole('link', { name: '이미지 저장' })).not.toBeInTheDocument()
  })

  it('실패한 뒤 다시 시도할 수 있다', async () => {
    api.getProductionItemQr.mockRejectedValueOnce(
      createAppError(ERROR_KIND.API_ERROR, { status: 500 }),
    )
    const user = renderQr()

    await user.click(screen.getByRole('button', { name: 'QR 생성' }))
    await user.click(await screen.findByRole('button', { name: '다시 시도' }))

    expect(await screen.findByAltText('PI-000031-01 개체의 QR 코드')).toBeInTheDocument()
  })

  it('닫으면 이미지가 사라진다', async () => {
    const user = renderQr()
    await user.click(screen.getByRole('button', { name: 'QR 생성' }))
    await screen.findByAltText('PI-000031-01 개체의 QR 코드')

    // 헤더의 ×(aria-label="닫기")와 이름이 같다 — 푸터 쪽이 DOM 에서 뒤에 온다.
    const [, footerClose] = screen.getAllByRole('button', { name: '닫기' })
    await user.click(footerClose)

    await waitFor(() =>
      expect(screen.queryByAltText('PI-000031-01 개체의 QR 코드')).not.toBeInTheDocument(),
    )
  })
})
