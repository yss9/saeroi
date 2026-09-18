import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { JourneyNode, ProductJourney, ProductPassport } from '../types/traceability.types.ts'
import { PassportView } from './PassportView.tsx'
import { MemoryRouter } from 'react-router-dom'

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
        blockchainProofs: [{
          referenceType: 'MATERIAL',
          chainId: 31221,
          contractAddress: '0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66',
          functionName: 'mintMaterial',
          txHash: '0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcd',
          blockNumber: 11221171,
          status: 'SUCCESS',
          confirmedAt: '2026-09-01T00:05:00',
        }],
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
  // 머리말의 로고가 Link 다 — a 는 basename(/test/)을 무시하고 도메인 루트로 나간다.
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PassportView qrToken={qrToken} />
      </MemoryRouter>
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
  async function selectMapRecord(user: ReturnType<typeof userEvent.setup>, name: string) {
    await user.click(screen.getByRole('button', { name }))
  }
  it('서울과 판교의 원래 좌표를 유지하며 모든 개별 마커를 표시한다', async () => {
    const data = journey()
    Object.assign(data.nodes.find((item) => item.nodeId === 'source-a')!, { latitude: 37.5666, longitude: 126.9784 })
    Object.assign(data.nodes.find((item) => item.nodeId === 'brand')!, { latitude: 37.394776, longitude: 127.11116 })
    api.getPassport.mockResolvedValue(passport({ journey: data }))
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })
    const projected = (latitude: number, longitude: number) =>
      `translate(${500 + (longitude - 127.65) * 94} ${10 + ((38.75 - latitude) / (38.75 - 33.1)) * 590})`
    await user.click(screen.getByRole('button', { name: /브랜드 제작 완료/ }))
    expect(container.querySelector('[data-node-id="brand"]')).toHaveAttribute('transform', projected(37.394776, 127.11116))
    expect(container.querySelector('[data-node-id="source-a"]')).toHaveAttribute('transform', projected(37.5666, 126.9784))
    expect(container.querySelector('.journey-cluster')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'ReWeave 상세 보기' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /업체 위치와 이동 경로/ })).toHaveAttribute('viewBox', '315 0 525 620')
    await user.click(screen.getByRole('button', { name: /최종 수령 완료/ }))
    expect(container.querySelector('[data-node-id="delivery"]')).toHaveAttribute('transform', projected(37.5665, 126.978))
    expect(screen.queryByRole('region', { name: '업체·배송지 보기' })).not.toBeInTheDocument()
    await selectMapRecord(user, '최종 수령 상세 보기')
    expect(screen.getByRole('button', { name: '최종 수령 상세 보기' })).toHaveClass('is-selected')
  })

  it('2~5단계에서도 수거 라벨 전체를 지도 내부에 유지한다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })
    for (const name of [/수거 완료/, /가공 완료/, /브랜드 제작 완료/, /최종 수령 완료/]) {
      await user.click(screen.getByRole('button', { name }))
      const labels = container.querySelectorAll('.journey-canvas__route-label')
      expect(labels.length).toBeLessThanOrEqual(2)
      labels.forEach((label) => {
        expect(label).toHaveTextContent('2. 수거')
        const coordinates = label.getAttribute('transform')!.match(/[\d.]+/g)!.map(Number)
        expect(coordinates[0] - 37).toBeGreaterThanOrEqual(315)
        expect(coordinates[0] + 37).toBeLessThanOrEqual(840)
        expect(coordinates[1] - 12).toBeGreaterThanOrEqual(0)
        expect(coordinates[1] + 12).toBeLessThanOrEqual(620)
      })
    }
  })

  it('같은 서울 지역의 업체가 있어도 배송 마커의 지역 좌표를 밀어내지 않는다', async () => {
    const data = journey()
    const supplier = data.nodes.find((item) => item.nodeId === 'source-a')!
    supplier.latitude = 37.5665
    supplier.longitude = 126.978
    api.getPassport.mockResolvedValue(passport({ journey: data }))
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })
    const original = container.querySelector('[data-node-id="source-a"]')?.getAttribute('transform')
    await userEvent.setup().click(screen.getByRole('button', { name: /최종 수령/ }))
    expect(container.querySelector('[data-node-id="delivery"]')).toHaveAttribute('transform', original)
    expect(container.querySelector('[data-node-id="source-a"]')).toHaveAttribute('transform', original)
    expect(container.querySelector('[data-edge-id="delivery-edge"]')).toBeInTheDocument()
  })

  it('좌표가 같아도 마커를 숨기거나 이동하지 않고 선택할 수 있다', async () => {
    const data = journey()
    for (const id of ['source-a', 'process-a']) {
      Object.assign(data.nodes.find((item) => item.nodeId === id)!, { latitude: 37.5666, longitude: 126.9784 })
    }
    api.getPassport.mockResolvedValue(passport({ journey: data }))
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })
    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    await selectMapRecord(user, '서울 원단 공급처 상세 보기')
    const source = container.querySelector('[data-node-id="source-a"]')!
    const process = container.querySelector('[data-node-id="process-a"]')!
    expect(source.getAttribute('transform')).toBe(process.getAttribute('transform'))
    expect(source).not.toHaveStyle({ display: 'none' })
    expect(process).not.toHaveStyle({ display: 'none' })
    expect(container.querySelector('.journey-cluster')).not.toBeInTheDocument()
  })

  it('짧은 배송 지역명을 지도 좌표로 표시하고 업체 좌표는 유지한다', async () => {
    const data = journey()
    data.nodes.find((item) => item.nodeId === 'delivery')!.locationName = '경남'
    api.getPassport.mockResolvedValue(passport({ journey: data }))
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })
    const supplierPosition = container.querySelector('[data-node-id="source-a"]')?.getAttribute('transform')
    await userEvent.setup().click(screen.getByRole('button', { name: /최종 수령/ }))
    expect(container.querySelector('[data-node-id="delivery"]')).toBeInTheDocument()
    expect(container.querySelector('[data-node-id="delivery"]')).not.toHaveAttribute('transform', 'translate(830 150)')
    expect(container.querySelector('[data-edge-id="delivery-edge"]')).toBeInTheDocument()
    expect(container.querySelector('[data-node-id="source-a"]')).toHaveAttribute('transform', supplierPosition)
    expect(screen.getAllByText('경남').length).toBeGreaterThan(0)
  })

  it.each(['알 수 없는 지역', null])('배송 좌표가 없는 %s는 지도에서만 제외하고 Flow View는 유지한다', async (locationName) => {
    const data = journey()
    data.nodes.find((item) => item.nodeId === 'delivery')!.locationName = locationName
    api.getPassport.mockResolvedValue(passport({ journey: data }))
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /최종 수령/ }))
    expect(screen.getByRole('button', { name: /최종 수령/ })).toHaveAttribute('aria-current', 'step')
    expect(container.querySelector('[data-node-id="delivery"]')).not.toBeInTheDocument()
    expect(container.querySelector('[data-edge-id="delivery-edge"]')).not.toBeInTheDocument()
    expect(container.querySelector('[data-node-id="brand"]')).toBeInTheDocument()
    if (locationName) expect(screen.getAllByText(locationName).length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Flow View' }))
    expect(container.querySelector('[data-node-id="delivery"]')).toBeInTheDocument()
    expect(container.querySelector('[data-edge-id="delivery-edge"]')).toBeInTheDocument()
  })

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

  it('지도 업체를 선택해도 해당 업체 단계 이후 기록을 미리 노출하지 않는다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await selectMapRecord(user, '서울 원단 공급처 상세 보기')

    expect(screen.getByRole('button', { name: /원단 등록 완료/ })).toHaveAttribute('aria-current', 'step')
    expect(container.querySelector('[data-edge-id="collect-a"]')).not.toBeInTheDocument()
    expect(container.querySelector('[data-node-id="process-a"]')).not.toBeInTheDocument()
    expect(container.querySelector('[data-edge-id="brand-a"]')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    await selectMapRecord(user, '그린텍스 상세 보기')

    expect(screen.getByRole('button', { name: /가공 완료/ })).toHaveAttribute('aria-current', 'step')
    expect(container.querySelector('[data-edge-id="collect-a"]')).toHaveClass('is-active')
    expect(container.querySelector('[data-edge-id="collect-b"]')).toHaveClass('is-dimmed')
    expect(container.querySelector('[data-edge-id="collect-a"] .journey-canvas__route-label')).toHaveTextContent('2. 수거')
    expect(container.querySelector('.journey-route-list')).toHaveTextContent('서울 원단 공급처 → 그린텍스 · 수거')
    expect(container.querySelector('[data-edge-id="collect-a"] [data-traveler-stage="COLLECTION"] svg')).toBeInTheDocument()
    expect(container.querySelector('[data-edge-id="brand-a"]')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Flow View' }))

    expect(container.querySelector('[data-edge-id="collect-a"]')).toHaveClass('is-active')
    expect(container.querySelector('[data-edge-id="collect-b"]')).toHaveClass('is-dimmed')
    expect(container.querySelector('[data-edge-id="brand-a"]')).toHaveClass('is-dimmed')
    expect(container.querySelector('[data-edge-id="brand-b"]')).toHaveClass('is-dimmed')
    expect(container.querySelector('[data-node-id="process-a"]')).not.toHaveClass('is-dimmed')
    expect(container.querySelector('[data-node-id="process-b"]')).toHaveClass('is-dimmed')
  })

  it('브랜드 단계에서 가공업체를 선택해도 누적된 브랜드 노드와 연결 경로를 유지한다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /브랜드 제작 완료/ }))
    await selectMapRecord(user, '그린텍스 상세 보기')

    expect(screen.getByRole('button', { name: /브랜드 제작 완료/ })).toHaveAttribute('aria-current', 'step')
    expect(container.querySelector('[data-node-id="brand"]')).not.toHaveClass('is-dimmed')
    expect(container.querySelector('[data-node-id="process-a"]')).not.toHaveClass('is-dimmed')
    expect(container.querySelector('[data-node-id="process-b"]')).toHaveClass('is-dimmed')
    expect(container.querySelector('[data-edge-id="brand-a"]')).toHaveClass('is-active')
    expect(container.querySelector('[data-edge-id="brand-b"]')).toHaveClass('is-dimmed')
    expect(screen.getAllByRole('heading', { name: '가공' })).not.toHaveLength(0)
  })

  it('다섯 단계에 서로 다른 색상과 단계 아이콘을 동일하게 적용한다', async () => {
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    const stepItems = Array.from(screen.getByRole('list', { name: '여정 단계' }).querySelectorAll('li'))
    expect(new Set(stepItems.map((item) => item.getAttribute('style'))).size).toBe(5)
    expect(stepItems.every((item) => item.querySelector('.journey-stepper__icon svg'))).toBe(true)
    expect(container.querySelector('[data-node-stage="SOURCE"] .journey-node__icon svg')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Flow View' }))
    expect(container.querySelectorAll('.journey-flow-backdrop__stage')).toHaveLength(5)
    expect(container.querySelectorAll('.journey-flow-backdrop__stage .journey-flow-backdrop__icon svg')).toHaveLength(5)
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

    expect(screen.queryByRole('button', { name: 'ReWeave 상세 보기' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /브랜드 제작 완료/ }))
    await selectMapRecord(user, 'ReWeave 상세 보기')
    expect(screen.getByRole('button', { name: /브랜드 제작 완료/ })).toHaveAttribute('aria-current', 'step')

    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    await user.click(screen.getByRole('button', { name: '에코사이클' }))
    expect(screen.getByRole('button', { name: '에코사이클' })).toHaveClass('is-active')
  })

  it('수거업체를 만들지 않고 업체 사이 이동 기록만 보여준다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /수거 완료/ }))
    expect(screen.getByText('2개의 이동 기록')).toBeInTheDocument()
    expect(container.querySelector('.journey-detail__selector')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^서울 원단 공급처$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^그린텍스$/ })).not.toBeInTheDocument()
    expect(screen.queryByText('수거업체')).not.toBeInTheDocument()
  })

  it('대표 이미지 외 업체·소재 사진을 만들지 않는다', async () => {
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    expect(container.querySelectorAll('img')).toHaveLength(1)
  })

  it('블록체인 기록을 누르면 상세 모달에 전체 값을 보여준다', async () => {
    const user = userEvent.setup()
    renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /원단 등록/ }))
    await user.click(screen.getByRole('button', { name: /mintMaterial/ }))

    expect(await screen.findByRole('heading', { name: '블록체인 기록 상세' })).toBeInTheDocument()
    expect(screen.getByText('소재 발행')).toBeInTheDocument()
    expect(screen.getByText('확정됨')).toBeInTheDocument()
    expect(screen.getByText('0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66')).toBeInTheDocument()
    expect(screen.getByText('11,221,171')).toBeInTheDocument()
    // 해시는 공통 TransactionHash 컴포넌트가 그리므로 값 자체만 확인한다 —
    // 링크 여부는 VITE_BESU_EXPLORER_URL 설정에 달려 있어 이 테스트의 관심사가 아니다.
    expect(screen.getByTitle(
      '0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcd',
    )).toBeInTheDocument()
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
