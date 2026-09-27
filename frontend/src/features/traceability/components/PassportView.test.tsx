import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
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
  it('제품 대표 정보와 다섯 단계 Journey를 보여준다', async () => {
    renderView()

    expect(await screen.findByRole('heading', { name: 'RE:BAG' })).toBeInTheDocument()
    expect(screen.getByText('ITEM-0001')).toBeInTheDocument()
    expect(screen.getByText('검증된 업사이클 제품')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '여정 단계' }).querySelectorAll('li')).toHaveLength(5)
    expect(screen.getByRole('button', { name: /원단 등록 완료/ })).toHaveAttribute('aria-current', 'step')
  })

  it('블록체인 검증 전에는 Verified 라고 말하지 않는다', async () => {
    // 예전에는 이 배지가 무조건 "Verified" 였다. 같은 화면 아래 여정 요약은
    // blockchainVerified=false 일 때 "기록 확인 중" 을 띄우므로, 한 페이지가 검증됐다고도
    // 아니라고도 말하는 상태였다 (2026-09-21 확인). 검증된 출처가 이 제품의 값어치라서
    // 여기서 앞서 말하면 안 된다.
    api.getPassport.mockResolvedValue(
      passport({ journey: { ...journey(), blockchainVerified: false } }),
    )
    renderView()

    expect(await screen.findByText('제품 이력을 확인하는 중')).toBeInTheDocument()
    expect(screen.queryByText('검증된 업사이클 제품')).not.toBeInTheDocument()
  })

  it('가공 전에 브랜드 수령 지점을 별도 단계로 보여준다', async () => {
    const user = userEvent.setup()
    const baseJourney = journey()
    api.getPassport.mockResolvedValue(passport({
      journey: {
        ...baseJourney,
        stages: baseJourney.stages.map((stage) => stage.stage === 'COLLECTION'
          ? { ...stage, nodeIds: ['collection-brand'], edgeIds: ['supplier-to-brand'] }
          : stage),
        nodes: [
          ...baseJourney.nodes,
          node({
            nodeId: 'collection-brand',
            stage: 'COLLECTION',
            companyName: 'ReWeave',
            companyRole: 'BRAND',
            description: '브랜드 소재 수령',
            materials: [{ name: '폐데님 A', materialType: 'DENIM', rootOrigin: 'WASTE', batchQuantityGrams: 800 }],
          }),
        ],
        edges: [
          { edgeId: 'supplier-to-brand', fromNodeId: 'source-a', toNodeId: 'collection-brand', stage: 'COLLECTION', materialName: '폐데님 A', materialType: 'DENIM', shippedAt: '2026-09-02T00:00:00', receivedAt: '2026-09-03T00:00:00', status: 'COMPLETED' },
          { edgeId: 'brand-to-process', fromNodeId: 'collection-brand', toNodeId: 'process-a', stage: 'PROCESSING', materialName: '폐데님 A', materialType: 'DENIM', shippedAt: '2026-09-04T00:00:00', receivedAt: '2026-09-05T00:00:00', status: 'COMPLETED' },
        ],
      },
    }))
    const { container } = renderView()

    const collectionStep = await screen.findByRole('button', { name: /브랜드 수령 완료/ })
    await user.click(collectionStep)
    expect(container.querySelector('.journey-detail__body')).toHaveTextContent('ReWeave')
    expect(container.querySelector('.journey-detail__body')).toHaveTextContent('브랜드 소재 수령')
    expect(screen.getByText('1개의 수령·이동 기록')).toBeInTheDocument()
  })

  it('같은 좌표의 브랜드 수령과 제작은 실제 위치의 한 마커에서 각각 선택한다', async () => {
    const user = userEvent.setup()
    const baseJourney = journey()
    const brand = baseJourney.nodes.find((item) => item.nodeId === 'brand')!
    api.getPassport.mockResolvedValue(passport({
      journey: {
        ...baseJourney,
        nodes: [...baseJourney.nodes, node({
          nodeId: 'collection-brand',
          stage: 'COLLECTION',
          companyName: brand.companyName,
          companyRole: 'BRAND',
          locationName: brand.locationName,
          latitude: brand.latitude,
          longitude: brand.longitude,
          description: '브랜드 소재 수령',
        })],
        edges: [...baseJourney.edges, {
          edgeId: 'collection-to-brand',
          fromNodeId: 'collection-brand',
          toNodeId: 'brand',
          stage: 'BRAND',
          materialName: null,
          materialType: null,
          shippedAt: null,
          receivedAt: null,
          status: 'COMPLETED',
        }],
      },
    }))
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    expect(container.querySelectorAll('.journey-canvas--map [data-node-id="brand"], .journey-canvas--map [data-node-id="collection-brand"]')).toHaveLength(1)
    expect(container.querySelector('.journey-canvas--map [data-edge-id="collection-to-brand"]')).not.toBeInTheDocument()
    const sharedPin = screen.getByRole('button', { name: /ReWeave.*2개 기록/ })
    await user.click(sharedPin)
    const choices = screen.getByRole('group', { name: '이 위치와 가까운 지점의 기록' })
    expect(within(choices).getAllByRole('button').map((button) => button.textContent)).toEqual([
      '원단 등록서울 원단 공급처',
      '브랜드 수령ReWeave',
      '브랜드 제작ReWeave',
    ])
    await user.click(within(choices).getByRole('button', { name: /브랜드 수령/ }))
    expect(container.querySelector('.journey-detail__body')).toHaveTextContent('브랜드 소재 수령')
    await user.click(within(choices).getByRole('button', { name: /브랜드 제작/ }))
    expect(container.querySelector('.journey-detail__body')).toHaveTextContent('ReWeave')
    await user.click(within(choices).getByRole('button', { name: /원단 등록/ }))
    expect(container.querySelector('.journey-detail__body')).toHaveTextContent('서울 원단 공급처')
  })

  it('가까운 서로 다른 지점은 좌표를 유지하고 선택 목록에서 구분한다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    const source = container.querySelector('[data-node-id="source-a"]')!
    const brand = container.querySelector('[data-node-id="brand"]')!
    expect(source.getAttribute('transform')).not.toBe(brand.getAttribute('transform'))
    expect(source.querySelector('.journey-node__nearby-badge')).toHaveTextContent('2')
    await user.click(source)
    const choices = screen.getByRole('group', { name: '가까운 지점의 기록' })
    await user.click(within(choices).getByRole('button', { name: /브랜드 제작/ }))
    expect(container.querySelector('.journey-detail__body')).toHaveTextContent('ReWeave')
  })

  it('원단 등록·브랜드 수령 단계에서는 여정 전체 지점을 미리 보여준다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    // 기본 화면(원단 등록)에서부터 이후 단계(가공·브랜드) 지점까지 이미 보인다 — 아직
    // 제품이 만들어지기 전이라 전체 흐름을 먼저 보여준다.
    expect(screen.getByRole('button', { name: /원단 등록 완료/ })).toHaveAttribute('aria-current', 'step')
    expect(container.querySelector('[data-node-id="process-a"]')).toBeInTheDocument()
    expect(container.querySelector('[data-edge-id="brand-a"]')).toBeInTheDocument()

    // 브랜드 수령으로 옮겨도 마찬가지다.
    await user.click(screen.getByRole('button', { name: /브랜드 수령 완료/ }))
    expect(container.querySelector('[data-node-id="process-a"]')).toBeInTheDocument()
    expect(container.querySelector('[data-edge-id="brand-a"]')).toBeInTheDocument()

    // 가공으로 넘어가면 그때부터는 다시 진행된 단계까지만 보인다.
    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    expect(container.querySelector('[data-edge-id="brand-a"]')).not.toBeInTheDocument()
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

    await user.click(screen.getByRole('button', { name: '흐름 보기' }))
    expect(screen.getByRole('button', { name: '흐름 보기' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '에코사이클' })).toHaveClass('is-active')
  })

  it('지도 업체를 선택해도 해당 업체 단계 이후 기록을 미리 노출하지 않는다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    // 가공부터는 원단 등록·운송처럼 전체를 미리 보여주지 않는다 — 그 이후 단계
    // (브랜드) 지점은 진행되기 전까지 지도에도 나타나지 않는다.
    expect(container.querySelector('[data-edge-id="brand-a"]')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '그린텍스 상세 보기' }))

    expect(screen.getByRole('button', { name: /가공 완료/ })).toHaveAttribute('aria-current', 'step')
    // Journey Map과 Flow View는 같은 활성·흐린 상태 계산 결과를 공유한다 —
    // 여기서는 좌표 배치가 없어 클래스 확인이 더 간단한 Flow View로 전환해서 본다.

    await user.click(screen.getByRole('button', { name: '흐름 보기' }))

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
    await user.click(screen.getByRole('button', { name: '그린텍스 상세 보기' }))

    expect(screen.getByRole('button', { name: /브랜드 제작 완료/ })).toHaveAttribute('aria-current', 'step')

    // 좌표 배치가 없어 클래스 확인이 더 간단한 Flow View로 전환해서 본다.
    await user.click(screen.getByRole('button', { name: '흐름 보기' }))
    expect(container.querySelector('[data-node-id="brand"]')).not.toHaveClass('is-dimmed')
    expect(container.querySelector('[data-node-id="process-a"]')).not.toHaveClass('is-dimmed')
    expect(container.querySelector('[data-node-id="process-b"]')).toHaveClass('is-dimmed')
    expect(container.querySelector('[data-edge-id="brand-a"]')).toHaveClass('is-active')
    expect(container.querySelector('[data-edge-id="brand-b"]')).toHaveClass('is-dimmed')
    expect(screen.getAllByRole('heading', { name: '가공' })).not.toHaveLength(0)
  })

  it('다섯 단계를 그리고 선택된 하나만 활성으로 구분한다', async () => {
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    const stepItems = Array.from(screen.getByRole('list', { name: '여정 단계' }).querySelectorAll('li'))
    expect(stepItems).toHaveLength(5)
    expect(stepItems.every((item) => item.querySelector('.journey-stepper__icon svg'))).toBe(true)

    // 선택 여부는 인라인 style이 아니라 상태로 본다. 예전에는 단계마다 다른 hue를
    // 인라인으로 주입해서 "style 다섯 개가 서로 다르다"를 셌는데, 지금은 색을 CSS가
    // 상태(활성/비활성)로 정하므로 li에 인라인 style 자체가 없다.
    const buttons = stepItems.map((item) => item.querySelector('button'))
    expect(buttons.filter((button) => button?.classList.contains('is-active'))).toHaveLength(1)
    expect(buttons.filter((button) => button?.getAttribute('aria-current') === 'step')).toHaveLength(1)
    expect(stepItems.every((item) => item.getAttribute('style') === null)).toBe(true)

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: '흐름 보기' }))
    // Flow 캔버스 안에는 단계 칩을 그리지 않는다 — 캔버스 바깥 위쪽 스테퍼가 같은
    // 다섯 단계를 이미 보여주고 있어 한 화면에 같은 줄이 두 번 나온다. 한 번 지웠다가
    // 되살아난 적이 있어 없다는 쪽으로 고정한다.
    expect(container.querySelectorAll('.journey-flow-backdrop__stage')).toHaveLength(0)
  })

  /**
   * Phosphor 아이콘은 이름을 DOM에 남기지 않는다. 대신 `<svg>`의 자식이 weight별 `<path>`
   * 뿐이라는 점을 쓴다 — IconBase는 `alt`가 있을 때의 `<title>`, `children`, 그리고
   * `weights.get(weight)` 세 가지만 자식으로 넣는데 이 화면은 앞의 둘을 쓰지 않는다.
   * `size`는 `<svg>`의 width/height 속성이라 innerHTML에 들어가지 않으므로, 22px 스테퍼와
   * 24px 노드처럼 크기가 달라도 같은 아이콘·같은 weight면 innerHTML이 정확히 일치한다.
   * 프로덕션 코드에 테스트용 표식을 심지 않으려고 이 방법을 쓴다.
   */
  function iconShape(element: Element | null | undefined): string {
    const svg = element?.querySelector('svg')
    expect(svg?.innerHTML).toContain('path')
    return svg?.innerHTML ?? ''
  }

  it('같은 단계는 스테퍼·지도 마커·Flow 노드·상세 헤더에서 모두 같은 아이콘을 쓴다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /원단 등록 완료/ }))

    const stepper = iconShape(
      screen.getByRole('button', { name: /원단 등록 완료/ }).closest('li')?.querySelector('.journey-stepper__icon'),
    )
    const detailHeader = iconShape(container.querySelector('.journey-detail__icon'))
    const mapNode = iconShape(container.querySelector('[data-node-stage="SOURCE"] .journey-node__icon'))

    await user.click(screen.getByRole('button', { name: '흐름 보기' }))
    const flowNode = iconShape(container.querySelector('[data-node-stage="SOURCE"] .journey-node__icon'))

    expect(detailHeader).toBe(stepper)
    expect(mapNode).toBe(stepper)
    expect(flowNode).toBe(stepper)

    // 위 비교가 실제로 아이콘을 구분하는지 — 다른 단계는 달라야 한다. 이게 없으면
    // 다섯 단계가 전부 같은 아이콘이어도 통과한다.
    const otherStage = iconShape(container.querySelector('[data-node-stage="PROCESSING"] .journey-node__icon'))
    expect(otherStage).not.toBe(stepper)
  })

  /** `.journey-node`의 `transform="translate(x y)"`에서 노드 중심 좌표를 읽는다. */
  function flowNodeCenters(container: HTMLElement): { x: number; y: number }[] {
    return Array.from(container.querySelectorAll('.journey-node')).map((node) => {
      const [x, y] = (node.getAttribute('transform') ?? '')
        .replace(/^translate\(|\)$/g, '')
        .split(/[\s,]+/)
        .map(Number)
      return { x, y }
    })
  }

  it('Flow View는 노드가 있는 단계만 열로 세어 다이어그램을 가운데 둔다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })
    await user.click(screen.getByRole('button', { name: '흐름 보기' }))

    const centers = flowNodeCenters(container)
    expect(centers).not.toHaveLength(0)

    // 운송은 노드가 없으니 열도 차지하지 않는다 — 다섯 단계 중 넷만 열이 된다.
    const columnXs = Array.from(new Set(centers.map((center) => center.x))).sort((a, b) => a - b)
    expect(columnXs).toHaveLength(4)

    // 열 간격이 고르다. 개수만 세면 부족하다 — 운송 자리를 열로 잡아 두더라도 그 열에는
    // 찍힐 노드가 없어서 좌표 개수와 좌우 여백은 그대로 맞고, 간격만 어긋난다
    // (그 경우 90·500·705·910으로 한가운데가 빈다).
    const columnGaps = columnXs.slice(1).map((x, index) => x - columnXs[index])
    columnGaps.forEach((gap) => expect(gap).toBeCloseTo(columnGaps[0], 5))

    // 업체명 카드(반폭 78)까지 포함한 좌우 여백이 같다.
    const labelHalfWidth = 78
    const viewBoxWidth = 1000
    expect(columnXs[0] - labelHalfWidth)
      .toBeCloseTo(viewBoxWidth - (columnXs[columnXs.length - 1] + labelHalfWidth), 5)

    // 세로도 마찬가지로 가운데다. 기준은 **보이는** 잉크다 — 위로는 번호 배지 위 끝
    // 27.75, 아래로는 업체명 카드 아래 끝 86.5. halo(r=34)는 기본 opacity가 0이라
    // 그걸 기준으로 잡으면 다이어그램이 그만큼 아래로 내려앉는다.
    const ys = centers.map((center) => center.y)
    expect((Math.min(...ys) - 27.75 + Math.max(...ys) + 86.5) / 2).toBeCloseTo(310, 5)
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
    expect(screen.queryByRole('button', { name: '흐름 보기' })).not.toBeInTheDocument()

    // 기본 화면(원단 등록)은 전체 여정을 미리 보여주므로 ReWeave(브랜드)도 이미 보인다 —
    // 다만 단계를 눌러야 그 단계가 활성 스텝(aria-current)이 된다.
    await user.click(screen.getByRole('button', { name: /브랜드 제작 완료/ }))
    await user.click(screen.getByRole('button', { name: 'ReWeave 상세 보기' }))
    expect(screen.getByRole('button', { name: /브랜드 제작 완료/ })).toHaveAttribute('aria-current', 'step')

    await user.click(screen.getByRole('button', { name: /가공 완료/ }))
    await user.click(screen.getByRole('button', { name: '에코사이클' }))
    expect(screen.getByRole('button', { name: '에코사이클' })).toHaveClass('is-active')
  })

  it('거래 수령 기록이 없는 기존 운송 단계는 이동 기록만 보여준다', async () => {
    const user = userEvent.setup()
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /브랜드 수령 완료/ }))
    expect(screen.getByText('2개의 수령·이동 기록')).toBeInTheDocument()
    expect(container.querySelector('.journey-detail__selector')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^서울 원단 공급처$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^그린텍스$/ })).not.toBeInTheDocument()

    // 거래가 없는 자가 보유 소재에는 브랜드 수령 노드를 지어내지 않는다. 좌표 없는 노드가
    // 빠지는 Journey Map 대신, 모든 노드를 그리는 Flow View에서 없는 것을 확인한다.
    await user.click(screen.getByRole('button', { name: '흐름 보기' }))
    expect(container.querySelectorAll('[data-node-stage="COLLECTION"]')).toHaveLength(0)
  })

  it('대표 이미지 외 업체·소재 사진을 만들지 않는다', async () => {
    const { container } = renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    // 헤더·푸터의 SAEROI 로고는 본문과 무관한 사이트 공통 요소라 범위에서 뺀다.
    expect(container.querySelectorAll('.passport__content img')).toHaveLength(1)
  })

  it('블록체인 기록을 누르면 상세 모달에 전체 값을 보여준다', async () => {
    const user = userEvent.setup()
    renderView()
    await screen.findByRole('heading', { name: 'RE:BAG' })

    await user.click(screen.getByRole('button', { name: /원단 등록/ }))
    await user.click(screen.getByRole('button', { name: /mintMaterial/ }))

    expect(await screen.findByRole('heading', { name: '기록 상세' })).toBeInTheDocument()
    expect(screen.getByText('소재 발행')).toBeInTheDocument()
    expect(screen.getByText('확정됨')).toBeInTheDocument()
    expect(screen.queryByText('0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66')).not.toBeInTheDocument()
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
