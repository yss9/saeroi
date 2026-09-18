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
import { useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { Modal, TransactionHash } from '../../../components/common/index.ts'
import { resolveRegionCoordinates } from '../regionCoordinates.ts'
import { routeLabelPosition } from '../routeLabelPosition.ts'
import type {
  BlockchainProof,
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

const REFERENCE_TYPE_LABEL: Record<BlockchainProof['referenceType'], string> = {
  MATERIAL: '소재 발행',
  TRADE: '거래',
  PROCESSING: '가공',
  PRODUCTION: '생산',
}

const PROOF_STATUS_LABEL: Record<BlockchainProof['status'], string> = {
  PENDING: '확정 대기',
  SUCCESS: '확정됨',
  FAILED: '실패',
}

function stageIndex(stage: JourneyStageType): number {
  return STAGE_ORDER.indexOf(stage)
}

function stageIcon(stage: JourneyStageType, size = 20) {
  const Icon = STAGE_META[stage].icon
  return <Icon size={size} weight="duotone" aria-hidden="true" />
}

function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => (
    typeof window.matchMedia === 'function' && window.matchMedia(query).matches
  ))

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return
    const media = window.matchMedia(query)
    const update = () => setMatches(media.matches)
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [query])

  return matches
}

function useReducedMotion() {
  return useMediaQuery('(prefers-reduced-motion: reduce)')
}

export function JourneyExplorer({ journey }: { journey: ProductJourney }) {
  const [activeStage, setActiveStage] = useState<JourneyStageType>('SOURCE')
  const [activeNodeId, setActiveNodeId] = useState<string | null>(() => (
    journey.stages.find((item) => item.stage === 'SOURCE')?.nodeIds[0]
    ?? journey.edges.find((edge) => edge.stage === 'SOURCE')?.toNodeId
    ?? null
  ))
  const [focusedNodeId, setFocusedNodeId] = useState<string | null>(null)
  const [viewMode, setViewMode] = useState<ViewMode>('MAP')
  const isMobile = useMediaQuery('(max-width: 760px)')

  const handleStageChange = (nextStage: JourneyStageType) => {
    const nextStageInfo = journey.stages.find((item) => item.stage === nextStage)
    const nextEdge = journey.edges.find((edge) => edge.stage === nextStage)
    const firstNode = nextStageInfo?.nodeIds[0]
      ?? nextEdge?.toNodeId
      ?? nextEdge?.fromNodeId
      ?? null
    setActiveStage(nextStage)
    setActiveNodeId(firstNode)
    setFocusedNodeId(null)
  }

  const handleNodeSelect = (nodeId: string) => {
    setActiveNodeId(nodeId)
    setFocusedNodeId(nodeId)
  }

  if (isMobile) {
    return (
      <MobileJourneyExplorer
        journey={journey}
        activeStage={activeStage}
        activeNodeId={activeNodeId}
        focusedNodeId={focusedNodeId}
        onChangeStage={handleStageChange}
        onSelectNode={handleNodeSelect}
      />
    )
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
          focusedNodeId={focusedNodeId}
          viewMode={viewMode}
          onSelectNode={handleNodeSelect}
        />
        <JourneyDetailPanel
          journey={journey}
          activeStage={activeStage}
          activeNodeId={activeNodeId}
          focusedNodeId={focusedNodeId}
          onSelectNode={handleNodeSelect}
        />
      </div>

      <JourneyMetrics journey={journey} />
    </section>
  )
}

function MobileJourneyExplorer({
  journey,
  activeStage,
  activeNodeId,
  focusedNodeId,
  onChangeStage,
  onSelectNode,
}: {
  journey: ProductJourney
  activeStage: JourneyStageType
  activeNodeId: string | null
  focusedNodeId: string | null
  onChangeStage: (stage: JourneyStageType) => void
  onSelectNode: (nodeId: string) => void
}) {
  return (
    <section className="journey-explorer journey-explorer--mobile" aria-label="모바일 제품 여정">
      <header className="mobile-journey__intro">
        <div>
          <p>PRODUCT JOURNEY</p>
          <h2>여정 확인</h2>
        </div>
        <span>{journey.nodes.length}개의 기록 지점</span>
      </header>

      <JourneyCanvas
        journey={journey}
        activeStage={activeStage}
        activeNodeId={activeNodeId}
        focusedNodeId={focusedNodeId}
        viewMode="MAP"
        compact
        onSelectNode={onSelectNode}
      />

      <section className="mobile-journey__stages" aria-labelledby="mobile-journey-stages-title">
        <div className="mobile-journey__section-heading">
          <h2 id="mobile-journey-stages-title">여정 단계</h2>
        </div>
        <JourneyStepper
          stages={journey.stages}
          activeStage={activeStage}
          onChange={onChangeStage}
        />
      </section>

      <JourneyDetailPanel
        journey={journey}
        activeStage={activeStage}
        activeNodeId={activeNodeId}
        focusedNodeId={focusedNodeId}
        onSelectNode={onSelectNode}
      />

      <MobileJourneySummary journey={journey} />
    </section>
  )
}

function MobileJourneySummary({ journey }: { journey: ProductJourney }) {
  return (
    <div className="mobile-journey__summary" aria-label="제품 여정 요약">
      <section>
        <Leaf size={23} weight="duotone" aria-hidden="true" />
        <div>
          <span>업사이클 소재</span>
          <strong>{Math.round(journey.upcycleRatio)}%</strong>
        </div>
      </section>
      <section className={journey.blockchainVerified ? 'is-verified' : ''}>
        <ShieldCheck size={23} weight="duotone" aria-hidden="true" />
        <div>
          <span>Journey 기록</span>
          <strong>{journey.blockchainVerified ? '검증 완료' : '확인 중'}</strong>
        </div>
      </section>
    </div>
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
  focusedNodeId,
  viewMode,
  compact = false,
  onSelectNode,
}: {
  journey: ProductJourney
  activeStage: JourneyStageType
  activeNodeId: string | null
  focusedNodeId: string | null
  viewMode: ViewMode
  compact?: boolean
  onSelectNode: (nodeId: string) => void
}) {
  const reducedMotion = useReducedMotion()
  const svgRef = useRef<SVGSVGElement>(null)
  const [mapScale, setMapScale] = useState(1)
  const positions = useMemo(
    () => viewMode === 'MAP' ? mapPositions(journey.nodes) : flowPositions(journey.nodes),
    [journey.nodes, viewMode],
  )
  const activeIndex = stageIndex(activeStage)
  const focusedEdgeIds = useMemo(
    () => focusedNodeId ? traceFocusedRoute(journey.edges, focusedNodeId) : new Set<string>(),
    [journey.edges, focusedNodeId],
  )
  const focusedVisibleEdgeIds = useMemo(() => new Set(
    journey.edges
      .filter((edge) => focusedEdgeIds.has(edge.edgeId) && stageIndex(edge.stage) <= activeIndex)
      .map((edge) => edge.edgeId),
  ), [activeIndex, focusedEdgeIds, journey.edges])
  const focusedNodeIds = useMemo(() => {
    const nodeIds = new Set<string>(focusedNodeId ? [focusedNodeId] : [])
    journey.edges.forEach((edge) => {
      if (!focusedVisibleEdgeIds.has(edge.edgeId)) return
      nodeIds.add(edge.fromNodeId)
      nodeIds.add(edge.toNodeId)
    })
    return nodeIds
  }, [focusedNodeId, focusedVisibleEdgeIds, journey.edges])
  const routeFocused = focusedNodeId !== null
  const activeEdgeIds = useMemo(() => new Set(
    journey.edges
      .filter((edge) => {
        const destination = journey.nodes.find((node) => node.nodeId === edge.toNodeId)
        return edge.stage === activeStage || destination?.stage === activeStage
      })
      .map((edge) => edge.edgeId),
  ), [activeStage, journey.edges, journey.nodes])
  const visibleEdges = journey.edges.filter((edge) =>
    viewMode === 'FLOW'
      || stageIndex(edge.stage) <= activeIndex
      || focusedVisibleEdgeIds.has(edge.edgeId),
  )
  const connectedIds = new Set(
    journey.edges
      .filter((edge) => edge.stage === activeStage)
      .flatMap((edge) => [edge.fromNodeId, edge.toNodeId]),
  )
  const visibleNodes = journey.nodes.filter((node) =>
    viewMode === 'FLOW'
      || stageIndex(node.stage) <= activeIndex
      || connectedIds.has(node.nodeId)
      || focusedNodeIds.has(node.nodeId),
  )
  const mapViewport = useMemo(() => {
    const points = Object.values(positions)
    const left = Math.min(315, ...points.map((point) => point.x - 48))
    const top = Math.min(0, ...points.map((point) => point.y - 48))
    const right = Math.max(840, ...points.map((point) => point.x + 48))
    const bottom = Math.max(620, ...points.map((point) => point.y + 48))
    return { left, top, right, bottom }
  }, [positions])
  useEffect(() => {
    const svg = svgRef.current
    if (!svg || viewMode !== 'MAP') return
    const measure = () => {
      const box = svg.getBoundingClientRect()
      if (box.width && box.height) setMapScale(Math.min(box.width / (mapViewport.right - mapViewport.left), box.height / (mapViewport.bottom - mapViewport.top)))
    }
    measure()
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(measure)
    observer.observe(svg)
    return () => observer.disconnect()
  }, [viewMode, mapViewport])
  // Geographic positions stay truthful; only drawing order changes for overlapping markers.
  const markerPriority = (node: JourneyNode) =>
    (node.stage === activeStage ? 1 : 0) + (node.nodeId === activeNodeId ? 2 : 0)
  const renderedNodes = viewMode === 'MAP'
    ? [...visibleNodes].sort((a, b) => markerPriority(a) - markerPriority(b))
    : visibleNodes

  return (
    <div className={`journey-canvas journey-canvas--${viewMode.toLowerCase()}${compact ? ' journey-canvas--compact' : ''}`}>
      <div className="journey-canvas__heading">
        <span>{viewMode === 'MAP' ? 'Journey Map' : 'Flow View'}</span>
        <strong>{STAGE_LABEL[activeStage]}</strong>
      </div>
      <svg
        ref={svgRef}
        viewBox={viewMode === 'MAP' ? `${mapViewport.left} ${mapViewport.top} ${mapViewport.right - mapViewport.left} ${mapViewport.bottom - mapViewport.top}` : '0 0 1000 620'}
        role="img"
        aria-label={`${STAGE_LABEL[activeStage]} 업체 위치와 이동 경로`}
      >
        {viewMode === 'MAP' ? <KoreaMapBackdrop /> : <FlowBackdrop />}

        <g className="journey-canvas__edges">
          {visibleEdges.map((edge, index) => {
            const from = positions[edge.fromNodeId]
            const to = positions[edge.toNodeId]
            if (!from || !to) return null
            const focused = focusedVisibleEdgeIds.has(edge.edgeId)
            const active = routeFocused
              ? focused && activeEdgeIds.has(edge.edgeId)
              : activeEdgeIds.has(edge.edgeId)
            const selected = focused || activeNodeId === edge.fromNodeId || activeNodeId === edge.toNodeId
            const route = curvedRoute(from, to, index)
            const label = routeLabelPosition(route.label, visibleNodes.flatMap((node) => {
              const point = positions[node.nodeId]
              return point ? [point] : []
            }), viewMode === 'MAP', mapScale, mapViewport)
            const TravelerIcon = STAGE_META[edge.stage].icon
            return (
              <g
                key={`${viewMode}-${activeStage}-${edge.edgeId}`}
                className={`journey-canvas__edge${active ? ' is-active' : ''}${selected ? ' is-selected' : ''}${routeFocused && !focused ? ' is-dimmed' : ''}`}
                style={{ '--edge-color': STAGE_META[edge.stage].color } as CSSProperties}
                data-edge-id={edge.edgeId}
                data-edge-stage={edge.stage}
              >
                <path className="journey-canvas__edge-shadow" d={route.path} />
                <path className="journey-canvas__edge-line" d={route.path} />
                {active && !reducedMotion ? (
                  <g className="journey-canvas__traveler" data-traveler-stage={edge.stage}>
                    <g transform={viewMode === 'MAP' ? `scale(${1 / mapScale})` : undefined}>
                      <circle r="12" />
                      <g transform="translate(-7 -7)">
                        <TravelerIcon size={14} weight="fill" aria-hidden="true" />
                      </g>
                    </g>
                    <animateMotion
                      path={route.path}
                      dur="2.2s"
                      begin={`${index * 0.18}s`}
                      repeatCount="indefinite"
                    />
                  </g>
                ) : null}
                {activeStage === 'COLLECTION' && edge.stage === 'COLLECTION' && label ? (
                  <g className="journey-canvas__route-label" transform={`translate(${label.x} ${label.y}) scale(${viewMode === 'MAP' ? 1 / mapScale : 1})`}>
                    <rect x="-37" y="-12" width="74" height="24" rx="12" />
                    <text textAnchor="middle" dy="4">{STAGE_LABEL[edge.stage]}</text>
                  </g>
                ) : null}
              </g>
            )
          })}
        </g>

        <g className="journey-canvas__nodes">
          {renderedNodes.map((node) => {
            const point = positions[node.nodeId]
            if (!point) return null
            const selected = node.nodeId === activeNodeId
            const current = focusedNodeIds.has(node.nodeId) || node.stage === activeStage || connectedIds.has(node.nodeId)
            const NodeIcon = STAGE_META[node.stage].icon
            return (
              <g
                key={node.nodeId}
                role="button"
                tabIndex={0}
                aria-label={`${node.companyName ?? STAGE_LABEL[node.stage]} 상세 보기`}
                className={`journey-node${selected ? ' is-selected' : ''}${current ? ' is-current' : ''}${routeFocused && !focusedNodeIds.has(node.nodeId) ? ' is-dimmed' : ''}`}
                style={{ '--node-color': STAGE_META[node.stage].color } as CSSProperties}
                transform={`translate(${point.x} ${point.y})`}
                data-node-id={node.nodeId}
                data-node-stage={node.stage}
                onClick={() => onSelectNode(node.nodeId)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') onSelectNode(node.nodeId)
                }}
              >
                <g transform={viewMode === 'MAP' ? `scale(${1 / mapScale})` : undefined}>
                <circle className="journey-node__halo" r={viewMode === 'MAP' ? 16 : 34} />
                <circle className="journey-node__body" r={viewMode === 'MAP' ? 13 : 23} />
                <g className="journey-node__icon" transform={viewMode === 'MAP' ? 'translate(-7 -7)' : 'translate(-12 -12)'}>
                  <NodeIcon size={viewMode === 'MAP' ? 14 : 24} weight="fill" aria-hidden="true" />
                </g>
                <g className="journey-node__stage-badge" transform={viewMode === 'MAP' ? 'translate(10 -10)' : 'translate(17 -17)'}>
                  <circle r={viewMode === 'MAP' ? 6 : 10} />
                  <text textAnchor="middle" dy="4">{stageIndex(node.stage) + 1}</text>
                </g>
                {viewMode === 'FLOW' ? (
                  <g className="journey-node__flow-label">
                    <rect x="-78" y="32" width="156" height="54" rx="11" />
                    <text textAnchor="middle" y="54">{node.companyName ?? STAGE_LABEL[node.stage]}</text>
                    <text className="journey-node__location" textAnchor="middle" y="73">
                      {compactLocation(node.locationName)}
                    </text>
                  </g>
                ) : null}
                </g>
              </g>
            )
          })}
        </g>
      </svg>
      {visibleEdges.some((edge) => edge.stage === 'COLLECTION') ? <details className="journey-route-list">
        <summary>수거 경로 보기</summary>
        {visibleEdges.filter((edge) => edge.stage === 'COLLECTION').map((edge) => <p key={edge.edgeId}>
          {journey.nodes.find((node) => node.nodeId === edge.fromNodeId)?.companyName} → {journey.nodes.find((node) => node.nodeId === edge.toNodeId)?.companyName} · 수거
        </p>)}
      </details> : null}
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
  focusedNodeId,
  onSelectNode,
}: {
  journey: ProductJourney
  activeStage: JourneyStageType
  activeNodeId: string | null
  focusedNodeId: string | null
  onSelectNode: (nodeId: string) => void
}) {
  const focusedNode = journey.nodes.find((item) => item.nodeId === focusedNodeId)
  const detailStage = focusedNode?.stage ?? activeStage
  const stage = journey.stages.find((item) => item.stage === detailStage)
  const edges = journey.edges.filter((edge) => edge.stage === detailStage)
  const candidateIds = Array.from(new Set([
    ...(stage?.nodeIds ?? []),
    ...edges.flatMap((edge) => [edge.fromNodeId, edge.toNodeId]),
  ]))
  const candidates = candidateIds
    .map((id) => journey.nodes.find((node) => node.nodeId === id))
    .filter((node): node is JourneyNode => node !== undefined)
    .filter((node) => node.stage === detailStage)
  const node = journey.nodes.find((item) => item.nodeId === activeNodeId) ?? candidates[0]
  const selectedStageNode = node?.stage === detailStage ? node : candidates[0]

  return (
    <aside
      className="journey-detail"
      style={{ '--stage-color': STAGE_META[detailStage].color } as CSSProperties}
    >
      <header className="journey-detail__header">
        <span className="journey-detail__icon">{stageIcon(detailStage, 24)}</span>
        <div>
          <p>STEP {stageIndex(detailStage) + 1}</p>
          <h2>{STAGE_LABEL[detailStage]}</h2>
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
              className={candidate.nodeId === selectedStageNode?.nodeId ? 'is-active' : ''}
              onClick={() => onSelectNode(candidate.nodeId)}
            >
              {candidate.companyName ?? compactLocation(candidate.locationName)}
            </button>
          ))}
        </div>
      ) : null}

      {detailStage === 'COLLECTION'
        ? <CollectionDetail edges={edges} journey={journey} />
        : selectedStageNode
          ? <NodeDetail node={selectedStageNode} />
          : <p className="journey-detail__empty">등록된 단계 정보가 없습니다.</p>}
    </aside>
  )
}

function NodeDetail({ node }: { node: JourneyNode }) {
  const [selectedProof, setSelectedProof] = useState<BlockchainProof | null>(null)

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
            <button
              key={proof.txHash}
              type="button"
              className="journey-detail__proof-row"
              onClick={() => setSelectedProof(proof)}
            >
              <ShieldCheck size={18} weight="duotone" />
              <span>{proof.functionName}</span>
              <span className="journey-detail__proof-more">자세히</span>
            </button>
          ))}
        </section>
      ) : null}

      <BlockchainProofModal proof={selectedProof} onClose={() => setSelectedProof(null)} />

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

/**
 * 블록체인 기록 상세. 목록에서는 한 줄로 보이던 것을 눌렀을 때만 전체 값을 보여준다.
 *
 * <p>Hash·주소 표시와 익스플로러 이동은 여기서 새로 만들지 않고 {@link TransactionHash}
 * (온체인 서명 패널이 이미 쓰는 공통 컴포넌트)를 그대로 쓴다 — 같은 값을 두 군데서 서로
 * 다르게 자르거나, 한쪽만 익스플로러 링크가 되는 어긋남을 막는다.
 */
function BlockchainProofModal({
  proof,
  onClose,
}: {
  proof: BlockchainProof | null
  onClose: () => void
}) {
  return (
    <Modal open={proof !== null} onClose={onClose} title="블록체인 기록 상세">
      {proof && (
        <dl className="journey-proof-modal">
          <div>
            <dt>대상</dt>
            <dd>{REFERENCE_TYPE_LABEL[proof.referenceType]}</dd>
          </div>
          <div>
            <dt>호출 함수</dt>
            <dd>{proof.functionName}</dd>
          </div>
          <div>
            <dt>상태</dt>
            <dd>{PROOF_STATUS_LABEL[proof.status]}</dd>
          </div>
          <div>
            <dt>트랜잭션</dt>
            <dd>
              <TransactionHash hash={proof.txHash} />
            </dd>
          </div>
          <div>
            <dt>컨트랙트</dt>
            <dd title={proof.contractAddress}>{proof.contractAddress}</dd>
          </div>
          <div>
            <dt>확정 블록</dt>
            <dd>{proof.blockNumber !== null ? proof.blockNumber.toLocaleString('ko-KR') : '대기 중'}</dd>
          </div>
          <div>
            <dt>확정 시각</dt>
            <dd>{formatDate(proof.confirmedAt)}</dd>
          </div>
        </dl>
      )}
    </Modal>
  )
}

function CollectionDetail({
  edges,
  journey,
}: {
  edges: JourneyEdge[]
  journey: ProductJourney
}) {
  if (edges.length === 0) return <p className="journey-detail__empty">등록된 이동 기록이 없습니다.</p>
  return (
    <section className="journey-detail__movements">
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
        </div>
      </section>
    </div>
  )
}

function mapPositions(nodes: JourneyNode[]): Record<string, Point> {
  const positions: Record<string, Point> = {}
  nodes.forEach((node) => {
    let latitude = node.latitude
    let longitude = node.longitude
    if ((latitude === null || longitude === null) && node.locationName) {
      const region = resolveRegionCoordinates(node.locationName)
      latitude = region?.latitude ?? null
      longitude = region?.longitude ?? null
    }
    if (latitude === null || longitude === null) {
      return
    }
    positions[node.nodeId] = projectGeo(latitude, longitude)
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

function curvedRoute(from: Point, to: Point, index: number): { path: string; label: Point } {
  const middleX = (from.x + to.x) / 2
  const middleY = (from.y + to.y) / 2 - 24 + (index % 3) * 18
  return {
    path: `M ${from.x} ${from.y} Q ${middleX} ${middleY} ${to.x} ${to.y}`,
    label: {
      x: (from.x + 2 * middleX + to.x) / 4,
      y: (from.y + 2 * middleY + to.y) / 4 - 16,
    },
  }
}

function traceFocusedRoute(edges: JourneyEdge[], nodeId: string): Set<string> {
  const route = new Set<string>()
  const visitedBackward = new Set<string>()
  const visitedForward = new Set<string>()

  const walkBackward = (currentNodeId: string) => {
    if (visitedBackward.has(currentNodeId)) return
    visitedBackward.add(currentNodeId)
    edges.filter((edge) => edge.toNodeId === currentNodeId).forEach((edge) => {
      route.add(edge.edgeId)
      walkBackward(edge.fromNodeId)
    })
  }
  const walkForward = (currentNodeId: string) => {
    if (visitedForward.has(currentNodeId)) return
    visitedForward.add(currentNodeId)
    edges.filter((edge) => edge.fromNodeId === currentNodeId).forEach((edge) => {
      route.add(edge.edgeId)
      walkForward(edge.toNodeId)
    })
  }

  walkBackward(nodeId)
  walkForward(nodeId)
  return route
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


// Natural Earth 1:50m Admin 0 공개 경계에서 대한민국 도형만 추출해 화면용으로 단순화한 좌표.
// 좌표 순서는 GeoJSON과 동일한 [경도, 위도].
const KOREA_SHAPES: Array<Array<[number, number]>> = [
  [
    [126.633887, 37.781836], [126.664551, 37.800732], [126.666797, 37.917187],
    [127.009668, 38.240527], [127.090332, 38.283887], [127.294043, 38.313281],
    [128.038965, 38.308545], [128.168652, 38.359326], [128.339453, 38.607861],
    [128.374609, 38.623437], [128.618848, 38.176074], [129.051563, 37.677637],
    [129.335156, 37.274561], [129.418262, 37.059033], [129.473438, 36.741895],
    [129.433008, 36.636621], [129.44502, 36.470703], [129.392578, 36.322705],
    [129.403516, 36.052148], [129.458301, 36.006445], [129.572852, 36.050537],
    [129.561719, 35.947656], [129.419141, 35.497852], [129.21416, 35.181836],
    [128.980078, 35.101514], [128.642578, 35.11958], [128.510938, 35.100977],
    [128.418848, 35.015674], [128.443945, 34.870361], [128.094531, 34.933594],
    [128.03623, 35.021973], [127.873242, 34.966309], [127.714844, 34.954687],
    [127.639355, 34.889697], [127.742188, 34.782568], [127.71543, 34.721045],
    [127.632422, 34.690234], [127.523633, 34.840088], [127.404297, 34.823096],
    [127.389648, 34.743018], [127.479102, 34.625244], [127.324609, 34.463281],
    [127.173438, 34.546143], [127.194922, 34.605029], [127.260547, 34.66167],
    [127.24707, 34.755127], [127.030762, 34.606885], [126.897461, 34.438867],
    [126.82627, 34.451074], [126.754785, 34.511865], [126.61084, 34.403516],
    [126.584375, 34.317529], [126.531445, 34.314258], [126.481738, 34.493945],
    [126.332617, 34.589648], [126.264453, 34.673242], [126.301074, 34.719971],
    [126.524512, 34.6979], [126.472852, 34.756348], [126.593359, 34.824365],
    [126.420703, 34.823389], [126.397852, 34.932812], [126.291113, 35.15415],
    [126.360547, 35.216895], [126.492773, 35.50127], [126.614062, 35.570996],
    [126.486523, 35.606348], [126.488477, 35.64707], [126.717383, 35.768848],
    [126.753027, 35.871973], [126.647461, 35.922412], [126.693457, 36.01416],
    [126.54043, 36.166162], [126.548242, 36.477637], [126.487695, 36.693799],
    [126.38877, 36.651172], [126.180859, 36.691602], [126.160547, 36.771924],
    [126.217188, 36.870947], [126.487012, 37.007471], [126.577734, 37.01958],
    [126.686719, 36.960352], [126.784473, 36.948437], [126.87207, 36.824463],
    [126.879102, 36.862061], [126.976855, 36.939404], [126.868945, 36.975732],
    [126.746387, 37.193555], [126.790527, 37.294922], [126.650293, 37.447119],
    [126.656836, 37.551172], [126.563379, 37.716504], [126.633887, 37.781836],
  ],
  [[128.741016, 34.798535], [128.64668, 34.736865], [128.489258, 34.865283], [128.667969, 35.008789], [128.721875, 35.013574], [128.741016, 34.798535]],
  [[128.06582, 34.805859], [128.054688, 34.708057], [127.983984, 34.703223], [127.941797, 34.76626], [127.873438, 34.734961], [127.832227, 34.874512], [127.91543, 34.920996], [128.037988, 34.878613], [128.06582, 34.805859]],
  [[126.233691, 34.370508], [126.169727, 34.355176], [126.108594, 34.39873], [126.122852, 34.443945], [126.247461, 34.56333], [126.343848, 34.544922], [126.379883, 34.497949], [126.335449, 34.426416], [126.233691, 34.370508]],
  [[126.520703, 37.736816], [126.516016, 37.604687], [126.46084, 37.610352], [126.407227, 37.649414], [126.369336, 37.772021], [126.411621, 37.822656], [126.493555, 37.782568], [126.520703, 37.736816]],
  [[126.753027, 34.343994], [126.769922, 34.296436], [126.689063, 34.30542], [126.646094, 34.351123], [126.651855, 34.390332], [126.7, 34.395898], [126.753027, 34.343994]],
  [[127.799023, 34.615039], [127.787793, 34.584082], [127.737305, 34.630908], [127.787109, 34.682129], [127.799023, 34.615039]],
  [[126.417578, 36.492578], [126.403809, 36.427881], [126.3375, 36.470557], [126.318555, 36.612549], [126.386621, 36.571143], [126.417578, 36.492578]],
  [[126.171973, 34.731152], [126.158789, 34.706982], [126.115234, 34.714209], [126.052051, 34.837549], [126.00752, 34.86748], [126.078418, 34.914844], [126.168555, 34.829687], [126.171973, 34.731152]],
  [[130.916016, 37.478467], [130.870605, 37.44873], [130.810254, 37.509912], [130.903711, 37.553711], [130.934277, 37.529736], [130.916016, 37.478467]],
  [[126.326953, 33.223633], [126.240234, 33.214844], [126.165625, 33.312012], [126.199414, 33.368066], [126.337695, 33.4604], [126.695508, 33.549316], [126.901172, 33.515137], [126.93125, 33.443848], [126.872852, 33.341162], [126.581738, 33.23833], [126.326953, 33.223633]],
]

const KOREA_REGION_LINES: Array<Array<[number, number]>> = [
  [[37.77, 126.72], [37.52, 127.02], [37.32, 127.42], [37.45, 128.02], [37.64, 128.92]],
  [[37.18, 126.62], [36.94, 127.18], [36.82, 127.78], [36.92, 128.52], [37.08, 129.36]],
  [[36.42, 126.49], [36.52, 127.06], [36.34, 127.54], [36.45, 128.22], [36.29, 129.50]],
  [[35.79, 126.45], [35.94, 127.12], [35.72, 127.62], [35.89, 128.25], [35.70, 129.45]],
  [[35.18, 126.08], [35.37, 126.82], [35.29, 127.47], [35.44, 128.10], [35.14, 129.17]],
  [[38.44, 127.28], [37.86, 127.42], [37.28, 127.56], [36.72, 127.44], [36.22, 127.62], [35.70, 127.48], [34.65, 127.54]],
]

function projectGeo(latitude: number, longitude: number): Point {
  return {
    // 카드 안에서 경로와 마커가 충분한 간격을 갖도록 대한민국 영역을 확대해 투영한다.
    x: 500 + (longitude - 127.65) * 94,
    y: 10 + ((38.75 - latitude) / (38.75 - 33.1)) * 590,
  }
}

function geoPath(points: Array<[number, number]>, close = false): string {
  const commands = points.map(([latitude, longitude], index) => {
    const point = projectGeo(latitude, longitude)
    return `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`
  })
  return `${commands.join(' ')}${close ? ' Z' : ''}`
}

function geoJsonPath(points: Array<[number, number]>): string {
  const commands = points.map(([longitude, latitude], index) => {
    const point = projectGeo(latitude, longitude)
    return `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`
  })
  return `${commands.join(' ')} Z`
}

function KoreaMapBackdrop() {
  return (
    <g className="journey-map-backdrop" aria-hidden="true">
      <rect width="1000" height="620" rx="24" />
      {KOREA_SHAPES.map((shape, index) => (
        <path
          key={index}
          className={index === 0 ? 'journey-map-backdrop__mainland' : 'journey-map-backdrop__island'}
          d={geoJsonPath(shape)}
        />
      ))}
      <g className="journey-map-backdrop__lines">
        {KOREA_REGION_LINES.map((line, index) => <path key={index} d={geoPath(line)} />)}
      </g>
    </g>
  )
}

function FlowBackdrop() {
  return (
    <g className="journey-flow-backdrop" aria-hidden="true">
      <rect width="1000" height="620" rx="24" />
      {STAGE_ORDER.map((stage, index) => (
        <g
          key={stage}
          className="journey-flow-backdrop__stage"
          style={{ '--stage-color': STAGE_META[stage].color } as CSSProperties}
          transform={`translate(${24 + index * 205} 18)`}
        >
          <rect width="154" height="40" rx="20" />
          <g className="journey-flow-backdrop__icon" transform="translate(12 8)">
            {stageIcon(stage, 24)}
          </g>
          <text x="88" y="26" textAnchor="middle">{index + 1}. {STAGE_LABEL[stage]}</text>
        </g>
      ))}
    </g>
  )
}
