import { EmptyState, ErrorMessage, Loading, TransactionHash } from '../../../components/common/index.ts'
import { useMaterialJourney } from '../hooks/useMaterialJourney.ts'
import { formatKg } from '../../../utils/quantity.ts'
import {
  MATERIAL_EVENT_TYPE_LABEL,
  MATERIAL_JOURNEY_STAGE_LABEL,
  type MaterialJourneyEntry,
} from '../types/material.types.ts'
import './material-journey-timeline.css'

export interface MaterialJourneyTimelineProps {
  materialId: number | null
}

/**
 * 소재 Journey Timeline (S15P21E104-77).
 *
 * <b>혈통(`MaterialLineageTree`)과 나란히 두되 다른 질문에 답한다.</b> 혈통은
 * "이 소재가 어느 소재에서 나왔고 어느 소재로 이어졌는가"(공간)를, 이 컴포넌트는
 * "이 소재에 무슨 일이 언제 있었는가"(시간)를 보여준다. 서버가 이미 시간순으로
 * 정렬해 주므로 여기서 다시 정렬하지 않는다.
 */
export function MaterialJourneyTimeline({ materialId }: MaterialJourneyTimelineProps) {
  const { data, isPending, isError, error, refetch } = useMaterialJourney(materialId)

  if (isPending) return <Loading variant="skeleton" rows={3} label="소재 이력을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  if (data.events.length === 0) {
    return (
      <EmptyState
        title="아직 기록된 사건이 없습니다"
        description="등록·거래·가공이 진행되면 이 소재의 진행 상황이 여기 시간순으로 표시됩니다."
      />
    )
  }

  return (
    <ol className="material-journey-timeline">
      {data.events.map((entry, index) => (
        <li key={`${entry.eventType}-${entry.timestamp}-${index}`}>
          <TimelineEntry entry={entry} />
        </li>
      ))}
    </ol>
  )
}

function TimelineEntry({ entry }: { entry: MaterialJourneyEntry }) {
  return (
    <article className="material-journey-timeline__entry">
      <div className="material-journey-timeline__marker" aria-hidden="true" />
      <div className="material-journey-timeline__body">
        <header>
          <span className="material-journey-timeline__stage">
            {MATERIAL_JOURNEY_STAGE_LABEL[entry.stage]}
          </span>
          <strong>{MATERIAL_EVENT_TYPE_LABEL[entry.eventType] ?? entry.eventType}</strong>
          <time dateTime={entry.timestamp}>{formatDate(entry.timestamp)}</time>
        </header>

        {(entry.actorName || entry.actorWallet) && (
          <p className="material-journey-timeline__actor">
            {entry.actorName ?? '알 수 없는 업체'}
            {entry.actorWallet && (
              <span title={entry.actorWallet}> · {shortWallet(entry.actorWallet)}</span>
            )}
          </p>
        )}

        {entry.quantity !== null && (
          <p className="material-journey-timeline__quantity">{formatKg(entry.quantity)}</p>
        )}

        {entry.detail && <p className="material-journey-timeline__detail">{entry.detail}</p>}

        {entry.txHash && <TransactionHash hash={entry.txHash} />}
      </div>
    </article>
  )
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function shortWallet(address: string): string {
  return `${address.slice(0, 6)}…${address.slice(-4)}`
}
