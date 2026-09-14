import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { JourneyNode, ProductJourney, ProductPassport } from '../types/traceability.types.ts'
import { PassportView } from './PassportView.tsx'

const api = vi.hoisted(() => ({ getPassport: vi.fn() }))
vi.mock('../api/getPassport.ts', () => ({
  getPassport: api.getPassport,
  getPassportThumbnailUrl: (qrToken: string) => `/api/v1/qr/${qrToken}/thumbnail`,
}))

function node(overrides: Partial<JourneyNode> & Pick<JourneyNode, 'nodeId' | 'stage'>): JourneyNode {
  return {
    companyName: null,
    companyRole: null,
    locationName: null,
    latitude: null,
    longitude: null,
    startedAt: '2026-09-01T00:00:00',
    completedAt: '2026-09-02T00:00:00',
    status: 'COMPLETED',
    description: null,
    processTypes: [],
    materials: [],
    blockchainProofs: [],
    evidenceSummary: [],
    ...overrides,
  }
}

function journey(): ProductJourney {
  return {
    stages: [
      { stage: 'SOURCE', sequence: 1, status: 'COMPLETED', nodeIds: ['source-a', 'source-b'], edgeIds: [] },
      { stage: 'COLLECTION', sequence: 2, status: 'COMPLETED', nodeIds: [], edgeIds: ['collect-a', 'collect-b'] },
      { stage: 'PROCESSING', sequence: 3, status: 'COMPLETED', nodeIds: ['process-a', 'process-b'], edgeIds: ['process-a-edge', 'process-b-edge'] },
      { stage: 'BRAND', sequence: 4, status: 'COMPLETED', nodeIds: ['brand'], edgeIds: ['brand-a', 'brand-b'] },
      { stage: 'DELIVERY', sequence: 5, status: 'COMPLETED', nodeIds: ['delivery'], edgeIds: ['delivery-edge'] },
    ],
    nodes: [
      node({
        nodeId: 'source-a',
        stage: 'SOURCE',
        companyName: '서울 원단 공급처',
        companyRole: 'SUPPLIER',
        locationName: '서울특별시 성동구 성수이로 10',
        latitude: 37.544,
        longitude: 127.056,
        materials: [{ name: '폐데님 A', materialType: 'DENIM', rootOrigin: 'WASTE', batchQuantityGrams: 800 }],
      }),
      node({
        nodeId: 'source-b',
        stage: 'SOURCE',
        companyName: '인천 원단 공급처',
        companyRole: 'SUPPLIER',
        locationName: '인천광역시 서구 가좌로 20',
        latitude: 37.49,
        longitude: 126.67,
      }),
      node({
        nodeId: 'process-a',
        stage: 'PROCESSING',
        companyName: '그린텍스',
        companyRole: 'PROCESSOR',
        locationName: '경기도 화성시 봉담읍 산업로 30',
        latitude: 37.2,
        longitude: 126.95,
        processTypes: ['SORTING', 'CLEANING'],
        evidenceSummary: [{ evidenceType: 'PROCESS_PHOTO', count: 2 }],
      }),
      node({
        nodeId: 'process-b',
        stage: 'PROCESSING',
        companyName: '에코사이클',
        companyRole: 'PROCESSOR',
        locationName: '충청남도 아산시 음봉면 산업로 40',
        latitude: 36.79,
        longitude: 127.0,
        processTypes: ['CUTTING'],
      }),
      node({ nodeId: 'brand', stage: 'BRAND', companyName: 'ReWeave', companyRole: 'BRAND', locationName: '서울특별시 성동구 연무장길 50', latitude: 37.54, longitude: 127.05 }),
      node({ nodeId: 'delivery', stage: 'DELIVERY', companyName: null, companyRole: 'CONSUMER', locationName: '서울특별시', latitude: null, longitude: null }),
    ],
    edges: [
      { edgeId: 'collect-a', fromNodeId: 'source-a', toNodeId: 'process-a', stage: 'COLLECTION', materialName: '폐데님 A', materialType: 'DENIM', shippedAt: '2026-09-02T00:00:00', receivedAt: '2026-09-03T00:00:00', status: 'COMPLETED' },
      { edgeId: 'collect-b', fromNodeId: 'source-b', toNodeId: 'process-b', stage: 'COLLECTION', materialName: '폐데님 B', materialType: 'DENIM', shippedAt: '2026-09-02T00:00:00', receivedAt: '2026-09-03T00:00:00', status: 'COMPLETED' },
      { edgeId: 'process-a-edge', fromNodeId: 'source-a', toNodeId: 'process-a', stage: 'PROCESSING', materialName: '폐데님 A', materialType: 'DENIM', shippedAt: null, receivedAt: null, status: 'COMPLETED' },
      { edgeId: 'process-b-edge', fromNodeId: 'source-b', toNodeId: 'process-b', stage: 'PROCESSING', materialName: '폐데님 B', materialType: 'DENIM', shippedAt: null, receivedAt: null, status: 'COMPLETED' },
      { edgeId: 'brand-a', fromNodeId: 'process-a', toNodeId: 'brand', stage: 'BRAND', materialName: null, materialType: null, shippedAt: null, receivedAt: null, status: 'COMPLETED' },
      { edgeId: 'brand-b', fromNodeId: 'process-b', toNodeId: 'brand', stage: 'BRAND', materialName: null, materialType: null, shippedAt: null, receivedAt: null, status: 'COMPLETED' },
      { edgeId: 'delivery-edge', fromNodeId: 'brand', toNodeId: 'delivery', stage: 'DELIVERY', materialName: null, materialType: null, shippedAt: null, receivedAt: '2026-09-10T00:00:00', status: 'COMPLETED' },
    ],
    materialComposition: [{ fiberType: '데님', percentage: 78 }, { fiberType: '면', percentage: 22 }],
    upcycleRatio: 100,
    blockchainVerified: true,
    truncated: false,
    message: null,
  }
}

function passport(overrides: Partial<ProductPassport> = {}): ProductPassport {
  return {
    itemCode: 'ITEM-0001',
    serialNumber: 1,
    itemStatus: 'SOLD',
    issuedAt: '2026-09-08T10:00:00',
    product: {
      name: 'RE:BAG',
      brandName: 'ReWeave',
      category: 'BAG',
      description: '폐데님으로 만든 가방입니다.',
      imageUrl: '/api/v1/qr/token/thumbnail',
    },
    traceabilityAvailable: true,
    journey: journey(),
    ...overrides,
  }
}

function renderView(qrToken = 'a'.repeat(32)) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <PassportView qrToken={qrToken} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getPassport.mockResolvedValue(passport())
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  vi.unstubAllGlobals()
})

describe('PassportView', () => {
  it('제품 대표 정보와 다섯 단계 Journey를 보여준다', async () => {
    renderView()

    expect(await screen.findByRole('heading', { name: 'RE:BAG' })).toBeInTheDocument()
    expect(screen.getByText('ITEM-0001')).toBeInTheDocument()
    expect(screen.getByText('폐데님으로 만든 가방입니다.')).toBeInTheDocument()
    expect(screen.getByText('Verified Upcycled Product')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '여정 단계' }).querySelectorAll('li')).toHaveLength(5)
    expect(screen.getByRole('button', { name: /원단 등록/ })).toHaveAttribute('aria-current', 'step')
  })

  it('qrToken으로 공개 대표 이미지를 조회한다', async () => {
    renderView('deadbeef00000000000000000000000')

    const image = await screen.findByRole('img', { name: /대표 이미지/ })
    expect(image).toHaveAttribute('src', '/api/v1/qr/deadbeef00000000000000000000000/thumbnail')
  })

  it('여러 가공업체를 선택하고 지도와 Flow View에서 같은 상세 상태를 유지한다', async () => {
    const user = userEvent.setup()
    renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    await user.click(screen.getByRole('button', { name: '에코사이클' }))
    expect(screen.getAllByText('에코사이클')).not.toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Flow View' }))
    expect(screen.getByRole('button', { name: 'Flow View' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '에코사이클' })).toHaveClass('is-active')
  })

  it('모바일에서는 지도와 단계별 상세만 간결하게 보여준다', async () => {
    vi.stubGlobal('matchMedia', vi.fn((query: string) => ({
      matches: query === '(max-width: 760px)',
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(() => false),
    })))
    const user = userEvent.setup()
    renderView()

    expect(await screen.findByRole('region', { name: '모바일 제품 여정' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '여정 확인' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /업체 위치와 이동 경로/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Flow View' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'ReWeave 상세 보기' }))
    expect(screen.getByRole('button', { name: /브랜드 제작 완료/ })).toHaveAttribute('aria-current', 'step')

    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    await user.click(screen.getByRole('button', { name: '에코사이클' }))
    expect(screen.getByRole('button', { name: '에코사이클' })).toHaveClass('is-active')
  })

  it('수거업체를 만들지 않고 업체 사이 이동 기록만 보여준다', async () => {
    const user = userEvent.setup()
    renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /수거 완료/ }))
    expect(screen.getByText('2개의 이동 기록')).toBeInTheDocument()
    expect(screen.queryByText('수거업체')).not.toBeInTheDocument()
  })

  it('대표 이미지 외 업체·소재 사진을 만들지 않는다', async () => {
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    expect(container.querySelectorAll('img')).toHaveLength(1)
  })

  it('연결된 Journey가 없으면 확인된 것처럼 표시하지 않는다', async () => {
    api.getPassport.mockResolvedValue(passport({ traceabilityAvailable: false, journey: null }))
    renderView()

    expect(await screen.findByRole('heading', { name: '제품 여정을 준비하고 있습니다.' })).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '여정 단계' })).not.toBeInTheDocument()
  })

  it('모르는 토큰이면 다시 시도할 수 있게 한다', async () => {
    api.getPassport.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'QR_TOKEN_NOT_FOUND',
        message: '일치하는 제품을 찾을 수 없습니다.',
        status: 404,
      }),
    )
    renderView()

    expect(await screen.findByRole('alert')).toHaveTextContent('일치하는 제품을 찾을 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
