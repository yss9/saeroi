import {
  ArrowRight,
  CalendarBlank,
  CheckCircle,
  FileText,
  Leaf,
  MapPin,
  MapTrifold,
  ShareNetwork,
  ShieldCheck,
} from '@phosphor-icons/react'
import { geoMercator } from 'd3-geo'
import type { GeoJsonObject } from 'geojson'
import { useEffect, useMemo, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { ComposableMap, Geographies, Geography } from 'react-simple-maps'
import { Modal, TransactionHash } from '../../../components/common/index.ts'
import { STAGE_LABEL, STAGE_META, STAGE_ORDER, stageIndex } from '../utils/journeyStages.ts'
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
  // Journey Map은 실제 위도·경도를 이 SVG 좌표계 위에 그대로 투영해 찍는다(외부 지도
  // 타일 서버 없이) — 타일 CDN이 API 키를 요구하거나 막히는 상황에 기록 조회 자체가
  // 흔들리지 않도록. 좌표가 없는 노드는 찍을 위치가 없으니 지도에서 빠진다.
  const positions = useMemo(
    () => (viewMode === 'MAP' ? mapPositions(journey.nodes) : flowPositions(journey.nodes)),
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

  return (
    <div className={`journey-canvas journey-canvas--${viewMode.toLowerCase()}${compact ? ' journey-canvas--compact' : ''}`}>
      <div className="journey-canvas__heading">
        <span>{viewMode === 'MAP' ? 'Journey Map' : 'Flow View'}</span>
        <strong>{STAGE_LABEL[activeStage]}</strong>
      </div>
      {viewMode === 'MAP' && Object.keys(positions).length === 0 ? (
        <div
          className="journey-map-empty"
          role="img"
          aria-label={`${STAGE_LABEL[activeStage]} 업체 위치와 이동 경로`}
        >
          <p>등록된 업체 주소의 좌표를 아직 확인하지 못했습니다.</p>
        </div>
      ) : (
      <JourneyCanvasSvg viewMode={viewMode} activeStage={activeStage}>
        {viewMode === 'MAP' ? <MapBackdrop /> : <FlowBackdrop />}

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
            const focused = focusedVisibleEdgeIds.has(edge.edgeId)
            const active = routeFocused
              ? focused && activeEdgeIds.has(edge.edgeId)
              : activeEdgeIds.has(edge.edgeId)
            const selected = focused || activeNodeId === edge.fromNodeId || activeNodeId === edge.toNodeId
            const route = curvedRoute(from, to, index)
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
                <path className="journey-canvas__edge-line" d={route.path} markerEnd="url(#journey-arrow)" />
                {active && edge.stage === 'COLLECTION' ? (
                  <g
                    className="journey-canvas__route-label"
                    transform={`translate(${route.label.x} ${route.label.y})`}
                  >
                    <rect x="-37" y="-12" width="74" height="24" rx="12" />
                    <text textAnchor="middle" dy="4">
                      {stageIndex(edge.stage) + 1}. {STAGE_LABEL[edge.stage]}
                    </text>
                  </g>
                ) : null}
                {active && !reducedMotion ? (
                  <g className="journey-canvas__traveler" data-traveler-stage={edge.stage}>
                    <circle r="16" />
                    <g transform="translate(-10 -10)">
                      <TravelerIcon size={20} weight="fill" aria-hidden="true" />
                    </g>
                    <animateMotion
                      path={route.path}
                      dur="2.2s"
                      begin={`${index * 0.18}s`}
                      repeatCount="indefinite"
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
                <title>{node.companyName ?? STAGE_LABEL[node.stage]}{node.locationName ? ` · ${node.locationName}` : ''}</title>
                <circle className="journey-node__halo" r="34" />
                <circle className="journey-node__body" r="23" />
                <g className="journey-node__icon" transform="translate(-12 -12)">
                  <NodeIcon size={24} weight="fill" aria-hidden="true" />
                </g>
                <g className="journey-node__stage-badge" transform="translate(17 -17)">
                  <circle r="10" />
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
            )
          })}
        </g>
      </JourneyCanvasSvg>
      )}
      <div className="journey-canvas__legend" aria-hidden="true">
        <span><i className="is-active" />현재 단계</span>
        <span><i />이전 경로</span>
      </div>
    </div>
  )
}

/**
 * Journey Map만 `<ComposableMap>`을 쓴다 — `<Geographies>`가 배경 지도를 그리려면
 * react-simple-maps의 투영 컨텍스트(`MapProvider`) 안에 있어야 하기 때문이다. Flow
 * View는 배경이 지리 정보가 아니라 그냥 카드라 평범한 `<svg>`로 충분하다. 자식(엣지·노드
 * `<g>`)은 두 모드가 완전히 같은 마크업을 쓰므로 여기서 한 번만 감싼다.
 */
function JourneyCanvasSvg({
  viewMode,
  activeStage,
  children,
}: {
  viewMode: ViewMode
  activeStage: JourneyStageType
  children: ReactNode
}) {
  const label = `${STAGE_LABEL[activeStage]} 업체 위치와 이동 경로`
  if (viewMode === 'MAP') {
    return (
      <ComposableMap
        projection={KOREA_PROJECTION}
        width={MAP_VIEW_WIDTH}
        height={MAP_VIEW_HEIGHT}
        role="img"
        aria-label={label}
      >
        {children}
      </ComposableMap>
    )
  }
  return (
    <svg viewBox={`0 0 ${MAP_VIEW_WIDTH} ${MAP_VIEW_HEIGHT}`} role="img" aria-label={label}>
      {children}
    </svg>
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

const MAP_VIEW_WIDTH = 1000
const MAP_VIEW_HEIGHT = 620
const MAP_PADDING = 40

/**
 * `assets/skorea-provinces.json`(통계청 KOSTAT 공공누리 제1유형 시도 경계, 2018)의
 * bbox 값을 그대로 옮겨 적었다. 그 파일은 이 지도에서만 쓰고 218KB라 다른 화면까지
 * 번들을 키우지 않도록 `MapBackdrop`이 필요할 때만 동적 import 한다 — 그래서 투영
 * 계산에 쓸 경계값은 파일을 기다리지 않도록 여기 상수로 따로 둔다. 데이터 파일을
 * 바꾸면 이 값도 같이 맞춰야 한다.
 * 출처: https://github.com/southkorea/southkorea-maps (kostat/2018, KOGL 제1유형)
 */
const KOREA_BBOX: [number, number, number, number] = [
  124.60971767865396, 33.11235576032225, 131.87146666639862, 38.61370930709446,
]

/**
 * 항상 대한민국 전역이 같은 자리에 오도록 고정한 투영이다. 활성 노드 범위에 맞춰 매번
 * 다시 맞추면(과거 시도) 업체가 한 지역에 몰렸을 때 해안선이 캔버스 밖으로 밀려나
 * 알아볼 수 없는 모양만 남을 수 있다 — 전역을 항상 보여주는 편이 안전하다. 노드
 * 마커는 배경 지도 파일이 아직 로드되지 않았어도 이 값으로 바로 자리를 잡는다.
 */
const KOREA_PROJECTION = geoMercator().fitExtent(
  [[MAP_PADDING, MAP_PADDING], [MAP_VIEW_WIDTH - MAP_PADDING, MAP_VIEW_HEIGHT - MAP_PADDING]],
  {
    type: 'Feature',
    properties: {},
    geometry: {
      type: 'Polygon',
      // 반시계 방향(서→북→동→남)이어야 한다. d3-geo는 시계 방향 고리를 "이 사각형의
      // 바깥 전체"로 읽어 지구 전체를 경계로 잡는다 — 그러면 fitExtent가 계산하는
      // scale이 실제 필요한 값의 1/50 수준으로 무너져 지도가 점 하나로 뭉개진다.
      coordinates: [[
        [KOREA_BBOX[0], KOREA_BBOX[1]],
        [KOREA_BBOX[0], KOREA_BBOX[3]],
        [KOREA_BBOX[2], KOREA_BBOX[3]],
        [KOREA_BBOX[2], KOREA_BBOX[1]],
        [KOREA_BBOX[0], KOREA_BBOX[1]],
      ]],
    },
  },
)

/**
 * 위도·경도가 있는 노드만 투영한다. 좌표가 없는 노드는 이 맵에 항목 자체가 없다 —
 * 틀린 위치를 찍기보다 안 보여주는 편이 낫다.
 */
function mapPositions(nodes: JourneyNode[]): Record<string, Point> {
  const positions: Record<string, Point> = {}
  nodes.forEach((node) => {
    if (node.latitude === null || node.longitude === null) return
    const projected = KOREA_PROJECTION([node.longitude, node.latitude])
    if (!projected) return
    positions[node.nodeId] = { x: projected[0], y: projected[1] }
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

/**
 * Journey Map 배경 — 시도 경계가 있는 실제 대한민국 지도. `KOREA_PROJECTION`으로 그린다.
 *
 * <p>경계 데이터(218KB)는 이 배경을 그릴 때만 필요하다. 최상단에서 그냥 import 하면
 * Journey Map을 한 번도 열지 않는 화면(로그인, 쇼핑, 생산 관리 등)까지 그 무게를
 * 그대로 지고 가므로, 여기서만 동적으로 불러온다 — 노드·경로는 `KOREA_PROJECTION`
 * 값만으로 이미 자리를 잡고 있어 이 로드를 기다리지 않는다.
 */
function MapBackdrop() {
  const [geography, setGeography] = useState<GeoJsonObject | null>(null)

  useEffect(() => {
    let cancelled = false
    import('../assets/skorea-provinces.json').then((module) => {
      if (!cancelled) setGeography(module.default as unknown as GeoJsonObject)
    })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <g className="journey-map-backdrop" aria-hidden="true">
      <rect width={MAP_VIEW_WIDTH} height={MAP_VIEW_HEIGHT} rx="24" />
      {geography && (
        <Geographies geography={geography}>
          {({ geographies }) =>
            geographies.map((geo) => (
              <Geography
                key={geo.rsmKey}
                geography={geo}
                tabIndex={-1}
                className="journey-map-backdrop__geo"
              />
            ))
          }
        </Geographies>
      )}
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
