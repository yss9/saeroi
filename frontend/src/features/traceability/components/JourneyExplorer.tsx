import {
  ArrowRight,
  CalendarBlank,
  CheckCircle,
  FileText,
  GearSix,
  Leaf,
  MapPin,
  MapTrifold,
  Package,
  ShareNetwork,
  ShieldCheck,
  Storefront,
  Truck,
} from '@phosphor-icons/react'
import { useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import type {
  JourneyEdge,
  JourneyNode,
  JourneyStage,
  JourneyStageStatus,
  JourneyStageType,
  ProductJourney,
} from '../types/traceability.types.ts'

type ViewMode = 'MAP' | 'FLOW'
type Point = { x: number; y: number }

const STAGE_ORDER: JourneyStageType[] = [
  'SOURCE',
  'COLLECTION',
  'PROCESSING',
  'BRAND',
  'DELIVERY',
]

const STAGE_LABEL: Record<JourneyStageType, string> = {
  SOURCE: '원단 등록',
  COLLECTION: '수거',
  PROCESSING: '가공',
  BRAND: '브랜드 제작',
  DELIVERY: '최종 수령',
}

const STAGE_META = {
  SOURCE: { icon: FileText, color: '#2f7d59' },
  COLLECTION: { icon: Truck, color: '#216a9f' },
  PROCESSING: { icon: GearSix, color: '#a9855d' },
  BRAND: { icon: Storefront, color: '#f07d45' },
  DELIVERY: { icon: Package, color: '#e95b2b' },
} satisfies Record<JourneyStageType, { icon: typeof FileText; color: string }>

const STATUS_LABEL: Record<JourneyStageStatus, string> = {
  COMPLETED: '완료',
  CURRENT: '진행 중',
  PENDING: '대기',
}

const PROCESS_LABEL: Record<string, string> = {
  CLEANING: '세척',
  COLOR_PROCESSING: '염색',
  CUTTING: '재단',
  DISASSEMBLY: '해체',
  REMANUFACTURING: '재제조',
  SHREDDING: '파쇄',
  SORTING: '선별',
  SURFACE_FINISHING: '표면 가공',
}

const MATERIAL_LABEL: Record<string, string> = {
  COTTON: '면',
  DENIM: '데님',
  ETC: '기타',
  LEATHER: '가죽',
  LINING: '안감',
  METAL: '금속',
  MIXED: '혼합 소재',
}

function formatDate(value: string | null): string {
  if (!value) return '기록 없음'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}

function formatQuantity(grams: number): string {
  if (grams >= 1000) return `${new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(grams / 1000)} kg`
  return `${new Intl.NumberFormat('ko-KR').format(grams)} g`
}

function shortHash(hash: string): string {
  return `${hash.slice(0, 8)}…${hash.slice(-6)}`
}

function stageIndex(stage: JourneyStageType): number {
  return STAGE_ORDER.indexOf(stage)
}

function stageIcon(stage: JourneyStageType, size = 20) {
  const Icon = STAGE_META[stage].icon
  return <Icon size={size} weight="duotone" aria-hidden="true" />
}

function useReducedMotion() {
  const [reduced, setReduced] = useState(false)

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const update = () => setReduced(media.matches)
    update()
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])

  return reduced
}

export function JourneyExplorer({ journey }: { journey: ProductJourney }) {
  const [activeStage, setActiveStage] = useState<JourneyStageType>('SOURCE')
  const [activeNodeId, setActiveNodeId] = useState<string | null>(() => (
    journey.stages.find((item) => item.stage === 'SOURCE')?.nodeIds[0]
    ?? journey.edges.find((edge) => edge.stage === 'SOURCE')?.toNodeId
    ?? null
  ))
  const [viewMode, setViewMode] = useState<ViewMode>('MAP')

  const handleStageChange = (nextStage: JourneyStageType) => {
    const nextStageInfo = journey.stages.find((item) => item.stage === nextStage)
    const nextEdge = journey.edges.find((edge) => edge.stage === nextStage)
    const firstNode = nextStageInfo?.nodeIds[0]
      ?? nextEdge?.toNodeId
      ?? nextEdge?.fromNodeId
      ?? null
    setActiveStage(nextStage)
    setActiveNodeId(firstNode)
  }

  return (
    <section className="journey-explorer" aria-label="제품 여정">
      <div className="journey-explorer__toolbar">
        <div className="journey-view-toggle" role="group" aria-label="여정 보기 방식">
          <button
            type="button"
            className={viewMode === 'MAP' ? 'is-active' : ''}
            aria-pressed={viewMode === 'MAP'}
            onClick={() => setViewMode('MAP')}
          >
            <MapTrifold size={18} aria-hidden="true" />
            Journey Map
          </button>
          <button
            type="button"
            className={viewMode === 'FLOW' ? 'is-active' : ''}
            aria-pressed={viewMode === 'FLOW'}
            onClick={() => setViewMode('FLOW')}
          >
            <ShareNetwork size={18} aria-hidden="true" />
            Flow View
          </button>
        </div>
        <p>업체를 선택하면 상세 기록을 확인할 수 있습니다.</p>
      </div>

      <JourneyStepper
        stages={journey.stages}
        activeStage={activeStage}
        onChange={handleStageChange}
      />

      <div className="journey-explorer__workspace">
        <JourneyCanvas
          journey={journey}
          activeStage={activeStage}
          activeNodeId={activeNodeId}
          viewMode={viewMode}
          onSelectNode={setActiveNodeId}
        />
        <JourneyDetailPanel
          journey={journey}
          activeStage={activeStage}
          activeNodeId={activeNodeId}
          onSelectNode={setActiveNodeId}
        />
      </div>

      <JourneyMetrics journey={journey} />
    </section>
  )
}

function JourneyStepper({
  stages,
  activeStage,
  onChange,
}: {
  stages: JourneyStage[]
  activeStage: JourneyStageType
  onChange: (stage: JourneyStageType) => void
}) {
  return (
    <ol className="journey-stepper" aria-label="여정 단계">
      {STAGE_ORDER.map((stageType, index) => {
        const stage = stages.find((item) => item.stage === stageType)
        const active = stageType === activeStage
        return (
          <li key={stageType} style={{ '--stage-color': STAGE_META[stageType].color } as CSSProperties}>
            <button
              type="button"
              className={active ? 'is-active' : ''}
              aria-current={active ? 'step' : undefined}
              onClick={() => onChange(stageType)}
            >
              <span className="journey-stepper__number">{index + 1}</span>
              <span className="journey-stepper__icon">{stageIcon(stageType, 22)}</span>
              <span className="journey-stepper__label">{STAGE_LABEL[stageType]}</span>
              <span className="journey-stepper__status">
                {stage ? STATUS_LABEL[stage.status] : '대기'}
              </span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}

function JourneyCanvas({
  journey,
  activeStage,
  activeNodeId,
  viewMode,
  onSelectNode,
}: {
  journey: ProductJourney
  activeStage: JourneyStageType
  activeNodeId: string | null
  viewMode: ViewMode
  onSelectNode: (nodeId: string) => void
}) {
  const reducedMotion = useReducedMotion()
  const positions = useMemo(
    () => viewMode === 'MAP' ? mapPositions(journey.nodes) : flowPositions(journey.nodes),
    [journey.nodes, viewMode],
  )
  const activeIndex = stageIndex(activeStage)
  const visibleEdges = journey.edges.filter((edge) =>
    viewMode === 'FLOW' || stageIndex(edge.stage) <= activeIndex,
  )
  const connectedIds = new Set(
    journey.edges
      .filter((edge) => edge.stage === activeStage)
      .flatMap((edge) => [edge.fromNodeId, edge.toNodeId]),
  )
  const visibleNodes = journey.nodes.filter((node) =>
    viewMode === 'FLOW'
      || stageIndex(node.stage) <= activeIndex
      || connectedIds.has(node.nodeId),
  )

  return (
    <div className={`journey-canvas journey-canvas--${viewMode.toLowerCase()}`}>
      <div className="journey-canvas__heading">
        <span>{viewMode === 'MAP' ? 'Journey Map' : 'Flow View'}</span>
        <strong>{STAGE_LABEL[activeStage]}</strong>
      </div>
      <svg
        viewBox="0 0 1000 620"
        role="img"
        aria-label={`${STAGE_LABEL[activeStage]} 업체 위치와 이동 경로`}
      >
        {viewMode === 'MAP' ? <KoreaMapBackdrop /> : <FlowBackdrop />}

        <defs>
          <marker id="journey-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
          </marker>
        </defs>

        <g className="journey-canvas__edges">
          {visibleEdges.map((edge, index) => {
            const from = positions[edge.fromNodeId]
            const to = positions[edge.toNodeId]
            if (!from || !to) return null
            const active = edge.stage === activeStage
            const selected = activeNodeId === edge.fromNodeId || activeNodeId === edge.toNodeId
            const path = curvedPath(from, to, index)
            return (
              <g
                key={`${viewMode}-${activeStage}-${edge.edgeId}`}
                className={`journey-canvas__edge${active ? ' is-active' : ''}${selected ? ' is-selected' : ''}`}
                style={{ '--edge-color': STAGE_META[edge.stage].color } as CSSProperties}
              >
                <path className="journey-canvas__edge-shadow" d={path} />
                <path className="journey-canvas__edge-line" d={path} markerEnd="url(#journey-arrow)" />
                {active && !reducedMotion ? (
                  <g className="journey-canvas__traveler">
                    <circle r="15" />
                    <text textAnchor="middle" dy="5">{travelerGlyph(activeStage)}</text>
                    <animateMotion
                      path={path}
                      dur="900ms"
                      begin={`${index * 0.14}s`}
                      fill="freeze"
                      calcMode="spline"
                      keySplines="0.42 0 0.58 1"
                    />
                  </g>
                ) : null}
              </g>
            )
          })}
        </g>

        <g className="journey-canvas__nodes">
          {visibleNodes.map((node) => {
            const point = positions[node.nodeId]
            if (!point) return null
            const selected = node.nodeId === activeNodeId
            const current = node.stage === activeStage || connectedIds.has(node.nodeId)
            return (
              <g
                key={node.nodeId}
                role="button"
                tabIndex={0}
                aria-label={`${node.companyName ?? STAGE_LABEL[node.stage]} 상세 보기`}
                className={`journey-node${selected ? ' is-selected' : ''}${current ? ' is-current' : ''}`}
                style={{ '--node-color': STAGE_META[node.stage].color } as CSSProperties}
                transform={`translate(${point.x} ${point.y})`}
                onClick={() => onSelectNode(node.nodeId)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') onSelectNode(node.nodeId)
                }}
              >
                <circle className="journey-node__halo" r="34" />
                <circle className="journey-node__body" r="23" />
                <text className="journey-node__icon" textAnchor="middle" dy="6">
                  {stageIndex(node.stage) + 1}
                </text>
                {viewMode === 'FLOW' ? (
                  <g className="journey-node__flow-label">
                    <rect x="-68" y="32" width="136" height="47" rx="10" />
                    <text textAnchor="middle" y="51">{node.companyName ?? STAGE_LABEL[node.stage]}</text>
                    <text className="journey-node__location" textAnchor="middle" y="68">
                      {compactLocation(node.locationName)}
                    </text>
                  </g>
                ) : null}
              </g>
            )
          })}
        </g>
      </svg>
      <div className="journey-canvas__legend" aria-hidden="true">
        <span><i className="is-active" />현재 단계</span>
        <span><i />이전 경로</span>
      </div>
    </div>
  )
}

function JourneyDetailPanel({
  journey,
  activeStage,
  activeNodeId,
  onSelectNode,
}: {
  journey: ProductJourney
  activeStage: JourneyStageType
  activeNodeId: string | null
  onSelectNode: (nodeId: string) => void
}) {
  const stage = journey.stages.find((item) => item.stage === activeStage)
  const edges = journey.edges.filter((edge) => edge.stage === activeStage)
  const candidateIds = Array.from(new Set([
    ...(stage?.nodeIds ?? []),
    ...edges.flatMap((edge) => [edge.fromNodeId, edge.toNodeId]),
  ]))
  const candidates = candidateIds
    .map((id) => journey.nodes.find((node) => node.nodeId === id))
    .filter((node): node is JourneyNode => Boolean(node))
  const node = journey.nodes.find((item) => item.nodeId === activeNodeId) ?? candidates[0]

  return (
    <aside
      className="journey-detail"
      style={{ '--stage-color': STAGE_META[activeStage].color } as CSSProperties}
    >
      <header className="journey-detail__header">
        <span className="journey-detail__icon">{stageIcon(activeStage, 24)}</span>
        <div>
          <p>STEP {stageIndex(activeStage) + 1}</p>
          <h2>{STAGE_LABEL[activeStage]}</h2>
        </div>
        <span className={`journey-detail__status is-${stage?.status.toLowerCase() ?? 'pending'}`}>
          {stage ? STATUS_LABEL[stage.status] : '대기'}
        </span>
      </header>

      {candidates.length > 1 ? (
        <div className="journey-detail__selector" aria-label="업체 선택">
          {candidates.map((candidate) => (
            <button
              key={candidate.nodeId}
              type="button"
              className={candidate.nodeId === node?.nodeId ? 'is-active' : ''}
              onClick={() => onSelectNode(candidate.nodeId)}
            >
              {candidate.companyName ?? compactLocation(candidate.locationName)}
            </button>
          ))}
        </div>
      ) : null}

      {node ? <NodeDetail node={node} /> : <CollectionDetail edges={edges} journey={journey} />}

      {activeStage === 'COLLECTION' && edges.length > 0 ? (
        <CollectionDetail edges={edges} journey={journey} compact />
      ) : null}
    </aside>
  )
}

function NodeDetail({ node }: { node: JourneyNode }) {
  return (
    <div className="journey-detail__body">
      <div className="journey-detail__company">
        <strong>{node.companyName ?? (node.stage === 'DELIVERY' ? '수령 지역' : '업체 정보')}</strong>
        {node.locationName ? <span><MapPin size={17} />{node.locationName}</span> : null}
      </div>

      <dl className="journey-detail__facts">
        <div>
          <dt><CalendarBlank size={17} />시작</dt>
          <dd>{formatDate(node.startedAt)}</dd>
        </div>
        <div>
          <dt><CheckCircle size={17} />완료</dt>
          <dd>{formatDate(node.completedAt)}</dd>
        </div>
      </dl>

      {node.description ? <p className="journey-detail__description">{node.description}</p> : null}

      {node.processTypes.length > 0 ? (
        <section className="journey-detail__section">
          <h3>가공 방식</h3>
          <div className="journey-detail__chips">
            {node.processTypes.map((type) => <span key={type}>{PROCESS_LABEL[type] ?? type}</span>)}
          </div>
        </section>
      ) : null}

      {node.materials.length > 0 ? (
        <section className="journey-detail__section">
          <h3>소재 정보</h3>
          <ul className="journey-detail__materials">
            {node.materials.map((material, index) => (
              <li key={`${material.name}-${index}`}>
                <span><Leaf size={17} />{material.name}</span>
                <strong>{formatQuantity(material.batchQuantityGrams)}</strong>
                <small>{MATERIAL_LABEL[material.materialType] ?? material.materialType}</small>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {node.blockchainProofs.length > 0 ? (
        <section className="journey-detail__section journey-detail__proofs">
          <h3>블록체인 기록</h3>
          {node.blockchainProofs.map((proof) => (
            <div key={proof.txHash}>
              <ShieldCheck size={18} weight="duotone" />
              <span>{proof.functionName}</span>
              <code title={proof.txHash}>{shortHash(proof.txHash)}</code>
            </div>
          ))}
        </section>
      ) : null}

      {node.evidenceSummary.length > 0 ? (
        <section className="journey-detail__section journey-detail__evidence">
          <h3>등록 증빙</h3>
          <p>
            <FileText size={18} weight="duotone" />
            {node.evidenceSummary.reduce((total, evidence) => total + evidence.count, 0)}개의 증빙 기록
          </p>
        </section>
      ) : null}
    </div>
  )
}

function CollectionDetail({
  edges,
  journey,
  compact = false,
}: {
  edges: JourneyEdge[]
  journey: ProductJourney
  compact?: boolean
}) {
  if (edges.length === 0) return <p className="journey-detail__empty">등록된 이동 기록이 없습니다.</p>
  return (
    <section className={`journey-detail__movements${compact ? ' is-compact' : ''}`}>
      <h3>{edges.length}개의 이동 기록</h3>
      <ul>
        {edges.map((edge) => {
          const from = journey.nodes.find((node) => node.nodeId === edge.fromNodeId)
          const to = journey.nodes.find((node) => node.nodeId === edge.toNodeId)
          return (
            <li key={edge.edgeId}>
              <div>
                <span>{from?.companyName ?? '출발지'}</span>
                <ArrowRight size={15} aria-hidden="true" />
                <strong>{to?.companyName ?? '도착지'}</strong>
              </div>
              <small>{edge.materialName ?? '소재'} · {formatDate(edge.receivedAt ?? edge.shippedAt)}</small>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function JourneyMetrics({ journey }: { journey: ProductJourney }) {
  return (
    <div className="journey-metrics">
      <section className="journey-metrics__composition">
        <div
          className="journey-donut"
          style={{ background: compositionGradient(journey.materialComposition) }}
          aria-label="소재 구성 비율"
        >
          <span>{Math.round(journey.upcycleRatio)}<small>%</small></span>
        </div>
        <div>
          <p className="journey-metrics__eyebrow">MATERIAL COMPOSITION</p>
          <h2>소재 구성</h2>
          <ul>
            {journey.materialComposition.map((item) => (
              <li key={item.fiberType}>
                <span>{item.fiberType}</span>
                <strong>{item.percentage}%</strong>
              </li>
            ))}
          </ul>
        </div>
      </section>
      <section className="journey-metrics__verification">
        <ShieldCheck size={34} weight="duotone" />
        <div>
          <p className="journey-metrics__eyebrow">TRACEABILITY</p>
          <h2>{journey.blockchainVerified ? '블록체인 검증 완료' : '기록 확인 중'}</h2>
          <p>각 업체의 이력과 제품 연결 기록을 확인할 수 있습니다.</p>
        </div>
      </section>
    </div>
  )
}

function mapPositions(nodes: JourneyNode[]): Record<string, Point> {
  const positions: Record<string, Point> = {}
  const stageCounts = new Map<JourneyStageType, number>()
  nodes.forEach((node) => {
    let latitude = node.latitude
    let longitude = node.longitude
    if ((latitude === null || longitude === null) && node.locationName) {
      const region = Object.entries(REGION_COORDINATES).find(([name]) => node.locationName?.startsWith(name))
      latitude = region?.[1].latitude ?? null
      longitude = region?.[1].longitude ?? null
    }
    const count = stageCounts.get(node.stage) ?? 0
    stageCounts.set(node.stage, count + 1)
    if (latitude === null || longitude === null) {
      positions[node.nodeId] = { x: 150 + stageIndex(node.stage) * 170, y: 150 + count * 92 }
      return
    }
    const jitter = count * 7
    positions[node.nodeId] = {
      x: clamp(90 + ((longitude - 124.5) / 5.5) * 790 + jitter, 70, 930),
      y: clamp(55 + ((39.2 - latitude) / 6.2) * 520 + jitter, 55, 575),
    }
  })
  return positions
}

function flowPositions(nodes: JourneyNode[]): Record<string, Point> {
  const positions: Record<string, Point> = {}
  const groups = new Map<JourneyStageType, JourneyNode[]>()
  nodes.forEach((node) => groups.set(node.stage, [...(groups.get(node.stage) ?? []), node]))
  STAGE_ORDER.forEach((stage, column) => {
    const items = groups.get(stage) ?? []
    items.forEach((node, index) => {
      const gap = 430 / Math.max(items.length, 1)
      positions[node.nodeId] = {
        x: 90 + column * 205,
        y: 105 + gap / 2 + index * gap,
      }
    })
  })
  return positions
}

function curvedPath(from: Point, to: Point, index: number): string {
  const middleX = (from.x + to.x) / 2
  const middleY = (from.y + to.y) / 2 - 24 + (index % 3) * 18
  return `M ${from.x} ${from.y} Q ${middleX} ${middleY} ${to.x} ${to.y}`
}

function travelerGlyph(stage: JourneyStageType): string {
  if (stage === 'COLLECTION') return '↗'
  if (stage === 'PROCESSING') return '◆'
  if (stage === 'BRAND') return '✦'
  return '●'
}

function compactLocation(location: string | null): string {
  if (!location) return '위치 정보 없음'
  return location.split(/\s+/).slice(0, 2).join(' ')
}

function compositionGradient(items: ProductJourney['materialComposition']): string {
  if (items.length === 0) return 'conic-gradient(#d9e3e8 0 100%)'
  const colors = ['#216a9f', '#e5d7bd', '#f09a68', '#6aa383', '#8fa5b5']
  let offset = 0
  const stops = items.map((item, index) => {
    const start = offset
    offset += item.percentage
    return `${colors[index % colors.length]} ${start}% ${offset}%`
  })
  return `conic-gradient(${stops.join(', ')})`
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

const REGION_COORDINATES: Record<string, { latitude: number; longitude: number }> = {
  서울특별시: { latitude: 37.5665, longitude: 126.978 },
  부산광역시: { latitude: 35.1796, longitude: 129.0756 },
  대구광역시: { latitude: 35.8714, longitude: 128.6014 },
  인천광역시: { latitude: 37.4563, longitude: 126.7052 },
  광주광역시: { latitude: 35.1595, longitude: 126.8526 },
  대전광역시: { latitude: 36.3504, longitude: 127.3845 },
  울산광역시: { latitude: 35.5384, longitude: 129.3114 },
  세종특별자치시: { latitude: 36.48, longitude: 127.289 },
  경기도: { latitude: 37.4138, longitude: 127.5183 },
  강원특별자치도: { latitude: 37.8228, longitude: 128.1555 },
  충청북도: { latitude: 36.6357, longitude: 127.4917 },
  충청남도: { latitude: 36.6588, longitude: 126.6728 },
  전북특별자치도: { latitude: 35.8203, longitude: 127.1088 },
  전라남도: { latitude: 34.8161, longitude: 126.4629 },
  경상북도: { latitude: 36.4919, longitude: 128.8889 },
  경상남도: { latitude: 35.4606, longitude: 128.2132 },
  제주특별자치도: { latitude: 33.4996, longitude: 126.5312 },
}

function KoreaMapBackdrop() {
  return (
    <g className="journey-map-backdrop" aria-hidden="true">
      <rect width="1000" height="620" rx="24" />
      <path d="M568 39c41 16 76 51 76 88 0 27-17 43-8 71 10 31 49 47 52 81 3 34-32 55-36 90-5 42 32 74 12 111-18 33-59 32-89 48-27 15-42 48-74 55-37 8-61-25-94-38-31-12-72-4-91-34-22-35 10-70 14-107 4-38-20-75-2-110 17-33 60-37 82-66 20-27 12-68 35-93 22-25 59-27 78-55 22-32 11-78 51-99 23-12 48-10 74-8z" />
      <path className="journey-map-backdrop__island" d="M267 540c30-12 77-8 88 9 8 13-21 29-53 31-35 2-59-21-35-40z" />
      <g className="journey-map-backdrop__lines">
        <path d="M460 145c55 28 99 65 155 78" />
        <path d="M390 274c92-9 168 18 257 70" />
        <path d="M370 406c75-25 175-7 267 38" />
        <path d="M505 83c-7 107 4 194 35 284 17 51 2 113-24 162" />
      </g>
    </g>
  )
}

function FlowBackdrop() {
  return (
    <g className="journey-flow-backdrop" aria-hidden="true">
      <rect width="1000" height="620" rx="24" />
      {STAGE_ORDER.map((stage, index) => (
        <g key={stage} transform={`translate(${32 + index * 205} 24)`}>
          <rect width="116" height="30" rx="15" />
          <text x="58" y="20" textAnchor="middle">{index + 1}. {STAGE_LABEL[stage]}</text>
        </g>
      ))}
    </g>
  )
}
