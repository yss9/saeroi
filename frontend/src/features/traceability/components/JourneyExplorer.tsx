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
import type { GeoProjection } from 'd3-geo'
import type { GeoJsonObject } from 'geojson'
import { useEffect, useId, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ComposableMap, Geographies, Geography } from 'react-simple-maps'
import { Modal, TransactionHash } from '../../../components/common/index.ts'
import { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'
import {
  JOURNEY_ICON_WEIGHT,
  STAGE_LABEL,
  STAGE_META,
  STAGE_ORDER,
  stageIndex,
} from '../utils/journeyStages.ts'
import { formatKg } from '../../../utils/quantity.ts'
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


function formatDate(value: string | null): string {
  if (!value) return '기록 없음'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}

// 자체 포맷터를 두지 않는다. 화면마다 단위가 달라지면(여기는 1kg 미만을 g 로 보여줬다)
// 같은 수량이 화면에 따라 다르게 읽힌다.
const formatQuantity = formatKg

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
  return <Icon size={size} weight={JOURNEY_ICON_WEIGHT} aria-hidden="true" />
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
  const [activeStage, setActiveStage] = useState<JourneyStageType>(STAGE_ORDER[0])
  const [activeNodeId, setActiveNodeId] = useState<string | null>(() => (
    journey.stages.find((item) => item.stage === STAGE_ORDER[0])?.nodeIds[0]
    ?? journey.edges.find((edge) => edge.stage === STAGE_ORDER[0])?.toNodeId
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
            여정 지도
          </button>
          <button
            type="button"
            className={`journey-view-toggle__flow${viewMode === 'FLOW' ? ' is-active' : ''}`}
            aria-pressed={viewMode === 'FLOW'}
            onClick={() => setViewMode('FLOW')}
          >
            <ShareNetwork size={18} aria-hidden="true" />
            흐름 보기
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
          <p>제품 여정</p>
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
        <Leaf size={23} weight={JOURNEY_ICON_WEIGHT} aria-hidden="true" />
        <div>
          <span>업사이클 소재</span>
          <strong>{Math.round(journey.upcycleRatio)}%</strong>
        </div>
      </section>
      <section className={journey.blockchainVerified ? 'is-verified' : ''}>
        <ShieldCheck size={23} weight={JOURNEY_ICON_WEIGHT} aria-hidden="true" />
        <div>
          <span>여정 기록</span>
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
          <li key={stageType}>
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
  const [openLocation, setOpenLocation] = useState<{ key: string; stage: JourneyStageType; mode: ViewMode } | null>(null)
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
  // 원단 등록(첫 화면)·운송 두 단계는 여정 전체를 미리 보여준다 — 아직 제품이 만들어지기
  // 전이라 그 두 단계에서는 전체 그림을 먼저 보여주는 편이 "앞으로 어떤 여정인지"를 바로
  // 알 수 있다. 그 뒤(가공부터)는 다시 진행된 단계까지만 보여준다 — 아직 확정되지 않은
  // 이후 기록을 미리 노출하지 않기 위해서다.
  //
  // 경계를 STAGE_ORDER의 두 번째 단계 이름으로 적는다. 숫자로 적으면 순서를 바꿀 때
  // 조용히 어긋나고, 실제로 예전에 COLLECTION이 앞에 있을 때는 여기가 'SOURCE'였다.
  const showFullFlow = activeIndex <= stageIndex('COLLECTION')
  const visibleEdges = journey.edges.filter((edge) =>
    viewMode === 'FLOW'
      || showFullFlow
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
      || showFullFlow
      || stageIndex(node.stage) <= activeIndex
      || connectedIds.has(node.nodeId)
      || focusedNodeIds.has(node.nodeId),
  )
  // Journey Map은 실제 위도·경도를 이 SVG 좌표계 위에 그대로 투영해 찍는다(외부 지도
  // 타일 서버 없이). 같은 좌표의 기록만 한 마커로 묶고, 가까운 다른 좌표는 이동시키지
  // 않는다. 좌표가 없는 노드는 찍을 위치가 없으니 지도에서 빠진다.
  //
  // 투영은 전국 고정이 아니라 **단계에 맞춰 확대**한다. `showFullFlow` 인 두 단계는
  // 여정 전체 지점을 보여줘 전체 흐름을 먼저 보게 하고, 그다음부터는 `visibleNodes` —
  // 지금까지 진행된 단계의 지점까지만 — 로 맞춘다. 그래서 가공 단계는 그 소재를 받은
  // 모든 가공업체까지 포함한 범위로 점점 넓어진다. 두 지점이 같은 도시 안처럼 가까우면
  // 그 범위 자체가 좁아 자연히 확대되어 보인다.
  const mapZoomTarget = showFullFlow ? journey.nodes : visibleNodes
  const mapProjection = viewMode === 'MAP' ? zoomedProjection(mapZoomTarget) : KOREA_PROJECTION
  const positions = viewMode === 'MAP'
    ? mapPositions(journey.nodes, mapProjection)
    : flowPositions(journey.nodes)
  const locationGroups = viewMode === 'MAP' ? groupLocatedNodes(visibleNodes) : []
  const openGroup = openLocation?.stage === activeStage && openLocation.mode === viewMode
    ? locationGroups.find((group) => group.key === openLocation.key)
    : undefined
  const nearbyNodes = openGroup
    ? visibleNodes.filter((node) => {
        const anchor = positions[openGroup.nodes[0].nodeId]
        const point = positions[node.nodeId]
        return anchor && point && Math.hypot(point.x - anchor.x, point.y - anchor.y) < MARKER_MIN_SEPARATION_PX
      }).sort((a, b) => stageIndex(a.stage) - stageIndex(b.stage)
        || Date.parse(a.startedAt ?? '9999-12-31') - Date.parse(b.startedAt ?? '9999-12-31'))
    : []
  const choiceLabel = openGroup && openGroup.nodes.length > 1
    ? nearbyNodes.length > openGroup.nodes.length ? '이 위치와 가까운 지점의 기록' : '이 위치의 기록'
    : '가까운 지점의 기록'

  return (
    <div className={`journey-canvas journey-canvas--${viewMode.toLowerCase()}${compact ? ' journey-canvas--compact' : ''}`}>
      <div className="journey-canvas__heading">
        <span>{viewMode === 'MAP' ? '여정 지도' : '흐름 보기'}</span>
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
      <JourneyCanvasSvg viewMode={viewMode} activeStage={activeStage} projection={mapProjection}>
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
            if (viewMode === 'MAP' && from.x === to.x && from.y === to.y) return null
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
                      <TravelerIcon size={20} weight={JOURNEY_ICON_WEIGHT} aria-hidden="true" />
                    </g>
                    {/*
                      begin이 **음수**인 것은 오타가 아니다. 이 `<g>`에는 초기 transform이
                      없어서 animateMotion이 시작되기 전까지 viewBox 원점(캔버스 좌상단)에
                      머무는데, 양수 지연을 주면 그 지연 동안 마커가 좌상단에 조각으로
                      보인다 — 엣지 key에 viewMode와 activeStage가 들어 있어 뷰를 바꾸거나
                      단계를 바꿀 때마다 전부 리마운트되며 매번 다시 나타났다.
                      음수 오프셋은 "문서가 시작되기 전에 이미 시작된 것"으로 취급되어
                      마운트 즉시 그 위상부터 활성이다. 엣지마다 위상을 어긋나게 하는
                      효과는 그대로고, 원점에 머무는 구간만 사라진다.

                      `<g>`에 transform으로 초기 위치를 주는 식으로 고치면 안 된다 —
                      animateMotion의 모션 변환은 기존 transform을 대체하지 않고 그 위에
                      더해져서, 도는 동안 좌표가 두 번 더해져 경로를 벗어난다.
                    */}
                    <animateMotion
                      path={route.path}
                      dur="2.2s"
                      begin={`${-index * 0.18}s`}
                      repeatCount="indefinite"
                    />
                  </g>
                ) : null}
              </g>
            )
          })}
        </g>

        <g className="journey-canvas__nodes">
          {(viewMode === 'MAP' ? locationGroups.map((group) => {
            const active = group.nodes.find((node) => node.nodeId === activeNodeId)
            return { ...group, node: active ?? group.nodes[0] }
          }) : visibleNodes.map((node) => ({ key: node.nodeId, nodes: [node], node }))).map((group) => {
            const node = group.node
            const point = positions[node.nodeId]
            if (!point) return null
            const selected = group.nodes.some((item) => item.nodeId === activeNodeId)
            const current = group.nodes.some((item) => focusedNodeIds.has(item.nodeId) || item.stage === activeStage || connectedIds.has(item.nodeId))
            const NodeIcon = STAGE_META[node.stage].icon
            const nearbyCount = viewMode === 'MAP' ? locationGroups.filter((other) => {
              const otherPoint = positions[other.nodes[0].nodeId]
              return otherPoint && Math.hypot(otherPoint.x - point.x, otherPoint.y - point.y) < MARKER_MIN_SEPARATION_PX
            }).reduce((count, other) => count + other.nodes.length, 0) : 1
            const markerLabel = group.nodes.length > 1
              ? `${node.companyName ?? STAGE_LABEL[node.stage]} · ${group.nodes.length}개 기록`
              : `${node.companyName ?? STAGE_LABEL[node.stage]} 상세 보기`
            const selectMarker = () => {
              if (viewMode === 'MAP' && nearbyCount > 1) {
                setOpenLocation({ key: group.key, stage: activeStage, mode: viewMode })
              } else {
                setOpenLocation(null)
                onSelectNode(node.nodeId)
              }
            }
            return (
              <g
                key={group.key}
                role="button"
                tabIndex={0}
                aria-label={markerLabel}
                className={`journey-node${selected ? ' is-selected' : ''}${current ? ' is-current' : ''}${routeFocused && !focusedNodeIds.has(node.nodeId) ? ' is-dimmed' : ''}`}
                transform={`translate(${point.x} ${point.y})`}
                data-node-id={node.nodeId}
                data-node-stage={node.stage}
                onClick={selectMarker}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    selectMarker()
                  }
                }}
              >
                <title>{markerLabel}{node.locationName ? ` · ${node.locationName}` : ''}</title>
                <circle className="journey-node__halo" r="34" />
                <circle className="journey-node__body" r="23" />
                <g className="journey-node__icon" transform="translate(-12 -12)">
                  <NodeIcon size={24} weight={JOURNEY_ICON_WEIGHT} aria-hidden="true" />
                </g>
                <g className="journey-node__stage-badge" transform="translate(17 -17)">
                  <circle r="10" />
                  <text textAnchor="middle" dy="4">{viewMode === 'MAP' && group.nodes.length > 1 ? group.nodes.length : stageIndex(node.stage) + 1}</text>
                </g>
                {viewMode === 'MAP' && nearbyCount > group.nodes.length ? (
                  <g className="journey-node__nearby-badge" transform="translate(-19 -19)" aria-hidden="true">
                    <circle r="11" />
                    <text textAnchor="middle" dy="4">{nearbyCount}</text>
                  </g>
                ) : null}
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
      {viewMode === 'MAP' && openGroup && nearbyNodes.length > 1 ? (
        <div className="journey-location-choices" role="group" aria-label={choiceLabel}>
          <strong>{choiceLabel}</strong>
          <div>
            {nearbyNodes.map((node) => (
              <button key={node.nodeId} type="button" className={node.nodeId === activeNodeId ? 'is-active' : ''} onClick={() => onSelectNode(node.nodeId)}>
                <span>{STAGE_LABEL[node.stage]}</span>
                <span>{node.companyName ?? node.locationName ?? '위치 정보 없음'}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}
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
  projection,
  children,
}: {
  viewMode: ViewMode
  activeStage: JourneyStageType
  projection: GeoProjection
  children: ReactNode
}) {
  const label = `${STAGE_LABEL[activeStage]} 업체 위치와 이동 경로`
  if (viewMode === 'MAP') {
    return (
      <ComposableMap
        projection={projection}
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
    <aside className="journey-detail">
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
        ? <>
            {selectedStageNode ? <NodeDetail node={selectedStageNode} /> : null}
            <CollectionDetail edges={edges} journey={journey} />
          </>
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
        {node.locationName ? <span><MapPin size={17} weight={JOURNEY_ICON_WEIGHT} />{node.locationName}</span> : null}
      </div>

      <dl className="journey-detail__facts">
        <div>
          <dt><CalendarBlank size={17} weight={JOURNEY_ICON_WEIGHT} />시작</dt>
          <dd>{formatDate(node.startedAt)}</dd>
        </div>
        <div>
          <dt><CheckCircle size={17} weight={JOURNEY_ICON_WEIGHT} />완료</dt>
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
                <span><Leaf size={17} weight={JOURNEY_ICON_WEIGHT} />{material.name}</span>
                <strong>{formatQuantity(material.batchQuantityGrams)}</strong>
                <small>{MATERIAL_TYPE_LABEL[material.materialType] ?? material.materialType}</small>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {node.blockchainProofs.length > 0 ? (
        <section className="journey-detail__section journey-detail__proofs">
          <h3>검증 기록</h3>
          {node.blockchainProofs.map((proof) => (
            <button
              key={proof.txHash}
              type="button"
              className="journey-detail__proof-row"
              onClick={() => setSelectedProof(proof)}
            >
              <ShieldCheck size={18} weight={JOURNEY_ICON_WEIGHT} />
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
            <FileText size={18} weight={JOURNEY_ICON_WEIGHT} />
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
 * (온체인 서명 패널이 이미 쓰는 공통 컴포넌트)를 전체 번호 표시 옵션으로 쓴다.
 * 익스플로러 링크는 같은 컴포넌트가 관리한다.
 */
function BlockchainProofModal({
  proof,
  onClose,
}: {
  proof: BlockchainProof | null
  onClose: () => void
}) {
  return (
    <Modal open={proof !== null} onClose={onClose} title="기록 상세">
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
            <dt>검증 번호</dt>
            <dd>
              <TransactionHash hash={proof.txHash} fullHash />
            </dd>
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
      <h3>{edges.length}개의 수령·이동 기록</h3>
      <ul>
        {edges.map((edge) => {
          const from = journey.nodes.find((node) => node.nodeId === edge.fromNodeId)
          const to = journey.nodes.find((node) => node.nodeId === edge.toNodeId)
          return (
            <li key={edge.edgeId}>
              <div>
                <span>{from?.companyName ?? '출발지'}</span>
                <ArrowRight size={15} weight={JOURNEY_ICON_WEIGHT} aria-hidden="true" />
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
        <ShieldCheck size={34} weight={JOURNEY_ICON_WEIGHT} />
        <div>
          <p className="journey-metrics__eyebrow">이력 검증</p>
          <h2>{journey.blockchainVerified ? '이력 검증 완료' : '기록 확인 중'}</h2>
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
 * (lng, lat) 사각 범위를 d3-geo `fitExtent`용 Feature로 만든다.
 *
 * <p><b>반시계 방향(서→북→동→남)이어야 한다.</b> d3-geo는 시계 방향 고리를 "이
 * 사각형의 바깥 전체"로 읽어 지구 전체를 경계로 잡는다 — 그러면 fitExtent가 계산하는
 * scale이 실제 필요한 값의 1/50 수준으로 무너져 지도가 점 하나로 뭉개진다.
 */
function boundsFeature(minLng: number, minLat: number, maxLng: number, maxLat: number) {
  return {
    type: 'Feature' as const,
    properties: {},
    geometry: {
      type: 'Polygon' as const,
      coordinates: [[
        [minLng, minLat],
        [minLng, maxLat],
        [maxLng, maxLat],
        [maxLng, minLat],
        [minLng, minLat],
      ]],
    },
  }
}

function fitKorea(bounds: ReturnType<typeof boundsFeature>): GeoProjection {
  return geoMercator().fitExtent(
    [[MAP_PADDING, MAP_PADDING], [MAP_VIEW_WIDTH - MAP_PADDING, MAP_VIEW_HEIGHT - MAP_PADDING]],
    bounds,
  )
}

/**
 * 좌표를 아직 확인 못했거나(빈 지도) 지금 보이는 업체가 하나도 없을 때 쓰는 대한민국
 * 전역 투영. `zoomedProjection`이 매번 맞추지 못할 때의 기본값이다.
 */
const KOREA_PROJECTION = fitKorea(boundsFeature(...KOREA_BBOX))

/**
 * **카메라가 좁힐 수 있는 최소 확대 폭(도).** 마커 간격이 아니라 **배경 지도가
 * 지도로 보이는 최소 폭**을 기준으로 정했다 — `skorea-provinces.json`은 시도 경계
 * 해상도라 정점이 성기다. 이 zoom target 중심 반경 실측 결과 0.02도(예전 값, 약
 * 2.2km)로 좁히면 그 폭 안에 해안선 정점이 하나도 없어 배경이 민무늬 사각형으로
 * 뭉개진다 — "좌표가 진짜 지도 위에 있는 게 맞냐"는 인상을 준다. 0.15도(약 16.6km)
 * 부터는 그 폭 안에 정점이 200개 안팎 잡혀 해안선 굴곡이 보인다.
 *
 * <p>이 폭으로도 가까운 지점의 마커가 겹칠 수 있다. 그 경우 좌표를 옮기는 대신
 * 마커를 누르면 근처 지점을 선택할 수 있게 한다.
 */
const MAP_MIN_SPAN_DEG = 0.15
/** 기존 0.35 여백을 0.273으로 줄이면 지도 배율이 약 10% 커지면서 가장자리 여백은 유지된다. */
const MAP_ZOOM_MARGIN_RATIO = 0.273

/**
 * **주어진 노드들 범위에 맞춰 확대한 투영.** `JourneyCanvas`가 단계에 따라 다른
 * 집합을 넘겨 호출한다 — 원단 등록(첫 화면)은 `journey.nodes`(여정 전체)를 줘서
 * 전체 흐름을 먼저 보여주고, 그다음 단계부터는 `visibleNodes`(지금까지 진행된
 * 단계까지)를 줘서 단계가 진행될수록 범위가 그만큼만 넓어지게 한다.
 *
 * 전국 고정 배율(`KOREA_PROJECTION`)로만 그리면 연결된 두 지점이 같은 도시 안처럼
 * 가까울 때 점 두 개가 겹쳐 보여 경로를 알아볼 수 없다 — 그때는 그 지점들 주변만
 * 확대해서 보여준다. 반대로 지점이 전국에 퍼져 있으면 자연히 전국에 가까운 범위가
 * 나온다(별도 상한을 둘 필요가 없다).
 *
 * 좌표가 있는 노드가 없으면 `KOREA_PROJECTION`으로 물러난다 — 그 경우 배경 자체를
 * 그리지 않으므로(`journey-map-empty`) 이 값은 실제로 쓰이지 않는다.
 */
function zoomedProjection(nodes: JourneyNode[]): GeoProjection {
  const located = nodes.filter(
    (node): node is JourneyNode & { latitude: number; longitude: number } =>
      node.latitude !== null && node.longitude !== null,
  )
  if (located.length === 0) return KOREA_PROJECTION

  const lngs = located.map((node) => node.longitude)
  const lats = located.map((node) => node.latitude)
  const [minLng, maxLng] = expandToMinSpan(Math.min(...lngs), Math.max(...lngs), MAP_MIN_SPAN_DEG)
  const [minLat, maxLat] = expandToMinSpan(Math.min(...lats), Math.max(...lats), MAP_MIN_SPAN_DEG)
  const lngMargin = (maxLng - minLng) * MAP_ZOOM_MARGIN_RATIO
  const latMargin = (maxLat - minLat) * MAP_ZOOM_MARGIN_RATIO
  return fitKorea(boundsFeature(
    minLng - lngMargin, minLat - latMargin, maxLng + lngMargin, maxLat + latMargin,
  ))
}

/** [min, max] 폭이 minSpan보다 좁으면 중심을 유지한 채 minSpan까지 양쪽으로 늘린다. */
function expandToMinSpan(min: number, max: number, minSpan: number): [number, number] {
  const span = max - min
  if (span >= minSpan) return [min, max]
  const grow = (minSpan - span) / 2
  return [min - grow, max + grow]
}

/**
 * 위도·경도가 있는 노드만 투영한다. 좌표가 없는 노드는 이 맵에 항목 자체가 없다 —
 * 틀린 위치를 찍기보다 안 보여주는 편이 낫다.
 */
function mapPositions(nodes: JourneyNode[], projection: GeoProjection): Record<string, Point> {
  const positions: Record<string, Point> = {}
  nodes.forEach((node) => {
    if (node.latitude === null || node.longitude === null) return
    const projected = projection([node.longitude, node.latitude])
    if (!projected) return
    positions[node.nodeId] = { x: projected[0], y: projected[1] }
  })
  return positions
}

/** 가까운 핀을 누르면 함께 선택할 수 있도록 찾는 화면 좌표 거리. 핀 자체는 움직이지 않는다. */
const MARKER_MIN_SEPARATION_PX = 72

function groupLocatedNodes(nodes: JourneyNode[]): { key: string; nodes: JourneyNode[] }[] {
  const groups = new Map<string, JourneyNode[]>()
  nodes.forEach((node) => {
    if (node.latitude === null || node.longitude === null) return
    const key = `${node.latitude},${node.longitude}`
    groups.set(key, [...(groups.get(key) ?? []), node])
  })
  return [...groups].map(([key, grouped]) => ({ key, nodes: grouped }))
}

/** `.journey-node__flow-label` 카드의 반폭 — 노드 마크업의 `rect x="-78" width="156"`. */
const FLOW_LABEL_HALF_WIDTH = 78
/** 라벨 카드 바깥에 남길 좌우 여백. */
const FLOW_SIDE_MARGIN = 12
/** 한 열의 노드들이 세로로 퍼지는 폭. */
const FLOW_COLUMN_SPAN = 430

/**
 * 노드 중심에서 위로 **실제 보이는** 끝 — 번호 배지(`translate(17 -17)`의 `r=10`,
 * stroke 1.5)의 위 끝이다.
 *
 * <p>halo(`r=34`)를 쓰면 안 된다. `.journey-node__halo`는 기본 `opacity: 0`이고 선택된
 * 노드 하나만 그것도 10% 틴트로 나타난다 — 보이지 않는 반지름에 맞춰 중앙을 잡으면
 * 다이어그램 전체가 그만큼 아래로 내려앉아 위 여백만 넓어 보인다.
 */
const FLOW_NODE_INK_TOP = 27.75
/** 노드 중심에서 아래로 보이는 끝 — 업체명 카드(`rect y=32 height=54`, stroke 1)의 아래 끝. */
const FLOW_NODE_INK_BOTTOM = 86.5

/**
 * Flow View의 노드 좌표.
 *
 * <p><b>열은 STAGE_ORDER 길이가 아니라 노드가 있는 단계 개수로 센다.</b> 운송
 * (COLLECTION)은 노드 없이 이동선만 있는 단계라 그 자리를 열로 잡으면 다이어그램
 * 한가운데가 빈다. 그 단계를 빼고 남은 열을 캔버스 폭에 고르게 펴면 빈 칸이 사라지고
 * 좌우 여백이 같아진다 — 좌우 순서는 STAGE_ORDER를 그대로 따른다.
 *
 * <p>가로 간격은 리터럴이 아니라 라벨 카드 기하에서 나온다. 첫 열 중심이
 * `여백 + 카드반폭`, 마지막 열 중심이 `폭 - 여백 - 카드반폭`이고 그 사이를 열 수로
 * 나눈다. 다섯 열이 다 찰 때 간격이 820/4 = 205, 첫 열이 90으로 떨어져 예전 값과
 * 정확히 같다.
 */
function flowPositions(nodes: JourneyNode[]): Record<string, Point> {
  const positions: Record<string, Point> = {}
  const groups = new Map<JourneyStageType, JourneyNode[]>()
  nodes.forEach((node) => groups.set(node.stage, [...(groups.get(node.stage) ?? []), node]))
  const columns = STAGE_ORDER.filter((stage) => (groups.get(stage)?.length ?? 0) > 0)
  const firstX = FLOW_SIDE_MARGIN + FLOW_LABEL_HALF_WIDTH
  const lastX = MAP_VIEW_WIDTH - FLOW_SIDE_MARGIN - FLOW_LABEL_HALF_WIDTH
  const columnGap = columns.length > 1 ? (lastX - firstX) / (columns.length - 1) : 0
  columns.forEach((stage, column) => {
    const items = groups.get(stage) ?? []
    items.forEach((node, index) => {
      const gap = FLOW_COLUMN_SPAN / Math.max(items.length, 1)
      positions[node.nodeId] = {
        // 열이 하나뿐이면 나눌 간격이 없으니 그대로 한가운데 둔다.
        x: columns.length > 1 ? firstX + column * columnGap : MAP_VIEW_WIDTH / 2,
        y: flowTopOffset() + gap / 2 + index * gap,
      }
    })
  })
  return positions
}

/**
 * 첫 노드 중심을 어디에 둘지. 열에 노드가 몇 개든 <b>보이는 잉크의 중점이 캔버스
 * 한가운데</b>에 오게 하는 값이다.
 *
 * <p>노드 개수를 k라 하면 첫 노드 중심이 `offset + span/2k`, 마지막이
 * `offset + span - span/2k`라 두 중심의 중점은 k와 무관하게 `offset + span/2`다. 거기에
 * 잉크가 위로 {@link FLOW_NODE_INK_TOP}, 아래로 {@link FLOW_NODE_INK_BOTTOM}만큼
 * 퍼지므로 잉크 중점은 그보다 `(아래 - 위)/2`만큼 내려간다. 그 값을 캔버스 절반에서
 * 빼면 offset이 나온다 — 열 개수·노드 개수가 달라져도 성립한다.
 */
function flowTopOffset(): number {
  return MAP_VIEW_HEIGHT / 2
    - FLOW_COLUMN_SPAN / 2
    - (FLOW_NODE_INK_BOTTOM - FLOW_NODE_INK_TOP) / 2
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
 * Journey Map 배경 — 시도 경계가 있는 실제 대한민국 지도. 감싸는 `<ComposableMap>`이
 * 넘겨준 투영(`JourneyCanvas`의 `mapProjection` — 보통 `zoomedProjection`)을 컨텍스트로
 * 그대로 받아 그린다.
 *
 * <p>경계 데이터(218KB)는 이 배경을 그릴 때만 필요하다. 최상단에서 그냥 import 하면
 * Journey Map을 한 번도 열지 않는 화면(로그인, 쇼핑, 생산 관리 등)까지 그 무게를
 * 그대로 지고 가므로, 여기서만 동적으로 불러온다 — 노드·경로는 `mapProjection`
 * 값만으로 이미 자리를 잡고 있어 이 로드를 기다리지 않는다.
 */
function MapBackdrop() {
  const [geography, setGeography] = useState<GeoJsonObject | null>(null)
  // 실제 시도 경계 데이터라 모서리가 각져 있다 — clipPath 없이 그리면 땅이 카드
  // 모서리의 둥근 바다색 rect 밖으로(정확히는 그 rect의 각진 원본 경계까지) 그대로
  // 삐져나와 "지도가 카드를 벗어난다"는 인상을 준다. 바다 rect와 같은 모양으로 잘라야
  // 땅도 똑같이 둥근 모서리 안에서 끝난다.
  const clipId = `journey-map-clip-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`

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
      <defs>
        <clipPath id={clipId}>
          <rect width={MAP_VIEW_WIDTH} height={MAP_VIEW_HEIGHT} rx="24" />
        </clipPath>
      </defs>
      <rect width={MAP_VIEW_WIDTH} height={MAP_VIEW_HEIGHT} rx="24" />
      {geography && (
        <g clipPath={`url(#${clipId})`}>
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
        </g>
      )}
    </g>
  )
}

/**
 * Flow View 배경 — 카드 한 장뿐이다. 예전에는 여기에 "1. 원단 등록 … 5. 최종 수령" 칩을
 * 다시 그렸는데, 캔버스 바깥 위쪽 {@link JourneyStepper}가 같은 다섯 단계를 이미
 * 보여주고 있어 같은 줄이 두 번 나왔다. 칩은 없애고 스테퍼 하나만 남긴다.
 */
function FlowBackdrop() {
  return (
    <g className="journey-flow-backdrop" aria-hidden="true">
      <rect width={MAP_VIEW_WIDTH} height={MAP_VIEW_HEIGHT} rx="24" />
    </g>
  )
}
