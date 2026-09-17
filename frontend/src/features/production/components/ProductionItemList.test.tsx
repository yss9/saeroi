// 제품 개체와 QR.
//
// 브랜드가 제품에 인쇄할 QR 주소를 얻는 화면이다. 틀리면 안 되는 지점 넷.
//
//   1) **확정 전에는 조회하지 않는다.** 개체가 없어 언제나 빈 배열이고, 부르면 화면마다
//      의미 없는 요청이 늘어난다.
//   2) **qrToken 전체를 화면에 늘어놓지 않는다.** 그 값 하나가 비회원 공개 경로의 열쇠라
//      화면 캡처 한 장으로 다 새어 나간다.
//   3) **주소는 서버가 준 것을 그대로 쓴다.** 화면이 토큰에 접두어를 붙여 만들면 그 규칙이
//      두 곳이 되고, 서버 설정이 바뀔 때 한쪽만 바뀐다 — 그러면 화면에서 확인한 주소와
//      실제로 인쇄된 QR 이 다른 곳을 가리킨다. 인쇄된 QR 은 고칠 수 없다.
//   4) **복사 실패를 삼키지 않는다.** 클립보드는 권한·보안 컨텍스트에 따라 실패한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductionItem } from '../types/production.types.ts'
import { ProductionItemList } from './ProductionItemList.tsx'

const api = vi.hoisted(() => ({
  getProductionItems: vi.fn(),
  getProductionItemQr: vi.fn(),
}))
vi.mock('../api/productionBatchApi.ts', () => ({
  getProductionItems: api.getProductionItems,
  getProductionItemQr: api.getProductionItemQr,
}))

const TOKEN = 'a1b2c3d4e5f60718293a4b5c6d7e8f90'
// 서버가 조립해 내려주는 절대 주소. **일부러 테스트가 도는 origin 과 다르게 둔다** —
// 화면이 이 값을 그대로 쓰는지, 아니면 토큰으로 다시 만들고 있는지가 그래야 드러난다.
const QR_URL = `https://saeroi.example.com/qr/${TOKEN}`

function item(overrides: Partial<ProductionItem> = {}): ProductionItem {
  return {
    productItemId: 91,
    itemCode: 'PI-000031-01',
    serialNumber: 1,
    qrToken: TOKEN,
    qrUrl: QR_URL,
    status: 'IN_STOCK',
    createdAt: '2026-09-10T10:00:00',
    ...overrides,
  }
}

function renderList({ confirmed = true } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductionItemList batchId={3} confirmed={confirmed} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getProductionItems.mockResolvedValue([item()])
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductionItemList', () => {
  it('확정 전에는 조회하지 않고 무엇을 기다리는지 알린다', () => {
    renderList({ confirmed: false })

    expect(screen.getByText(/생산을 확정하면 QR 이 만들어집니다/)).toBeInTheDocument()
    expect(api.getProductionItems).not.toHaveBeenCalled()
  })

  it('만들어진 개체와 상태를 보여준다', async () => {
    api.getProductionItems.mockResolvedValue([
      item({ productItemId: 91, serialNumber: 1, status: 'IN_STOCK' }),
      item({
        productItemId: 92,
        itemCode: 'PI-000031-02',
        serialNumber: 2,
        status: 'SOLD',
        qrToken: 'ffffffff11112222333344445555aaaa',
        qrUrl: 'https://saeroi.example.com/qr/ffffffff11112222333344445555aaaa',
      }),
    ])
    renderList()

    expect(await screen.findByText('PI-000031-01')).toBeInTheDocument()
    expect(screen.getByText('재고')).toBeInTheDocument()
    expect(screen.getByText('판매 완료')).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('QR 토큰 전체를 화면에 노출하지 않는다', async () => {
    renderList()
    await screen.findByText('PI-000031-01')

    // 앞자리만 확인용으로 보인다.
    expect(screen.getByText(`${TOKEN.slice(0, 6)}…`)).toBeInTheDocument()
    expect(screen.queryByText(TOKEN)).not.toBeInTheDocument()
    // 링크의 href 에는 들어가지만(열어보기) 텍스트로는 노출되지 않는다.
    expect(screen.getByRole('link', { name: '열어보기' })).toHaveAttribute('href', QR_URL)
  })

  // navigator 를 통째로 바꾸지 않는다 — userEvent.setup() 이 자체 클립보드 스텁을
  // 설치하므로 그것을 그대로 쓰고, 실패 경로만 spy 로 갈아 끼운다.
  it('복사는 서버가 준 주소를 그대로 쓴다', async () => {
    const user = renderList()
    await screen.findByText('PI-000031-01')

    await user.click(screen.getByRole('button', { name: '주소 복사' }))

    // 화면이 토큰으로 주소를 다시 만들면 여기서 origin 이 어긋난다.
    await waitFor(async () => expect(await navigator.clipboard.readText()).toBe(QR_URL))
    expect(await screen.findByRole('button', { name: '복사됨' })).toBeInTheDocument()
  })

  // 실패를 삼키면 사용자는 복사된 줄 알고 빈 값을 붙여 넣는다.
  it('복사가 실패하면 복사됨으로 바꾸지 않는다', async () => {
    const user = renderList()
    await screen.findByText('PI-000031-01')
    // 권한 거부·비보안 컨텍스트에서 실제로 이렇게 던진다.
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'))

    await user.click(screen.getByRole('button', { name: '주소 복사' }))

    expect(screen.getByRole('button', { name: '주소 복사' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '복사됨' })).not.toBeInTheDocument()
  })

  it('새 탭으로 연다', async () => {
    renderList()
    await screen.findByText('PI-000031-01')

    // 라벨을 만들다가 이 화면을 잃으면 다시 찾아와야 한다.
    const link = screen.getByRole('link', { name: '열어보기' })
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noreferrer')
  })

  it('확정됐는데 개체가 없으면 이상 상황임을 알린다', async () => {
    api.getProductionItems.mockResolvedValue([])
    renderList()

    expect(await screen.findByText('만들어진 제품 개체가 없습니다')).toBeInTheDocument()
  })

  // 남의 배치는 403 이다. 빈 배열과 구분되어야 한다.
  it('조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getProductionItems.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'PRODUCTION_FORBIDDEN',
        message: '이 배치를 조회할 권한이 없습니다.',
        status: 403,
      }),
    )
    renderList()

    expect(await screen.findByRole('alert')).toHaveTextContent('조회할 권한이 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('개체마다 번호와 코드를 짝지어 보여준다', async () => {
    api.getProductionItems.mockResolvedValue([
      item({ productItemId: 92, itemCode: 'PI-000031-02', serialNumber: 2 }),
    ])
    renderList()

    const row = (await screen.findByText('PI-000031-02')).closest('tr')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getByText('2')).toBeInTheDocument()
  })
})
