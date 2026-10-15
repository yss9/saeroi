import { useId, useState } from 'react'
import type { BrandPredictionComment, PredictionReasonCode } from '../../types/prediction.types.ts'
import { formatDate, REASON_LABELS } from './insightFormat.ts'

/**
 * 익명 선호 의견 — 이유로 거른다.
 *
 * 의견이 쌓이면 "가격 얘기만 모아 보고 싶다" 가 먼저 나온다. 칩은 의견이 있는 이유만 보인다 —
 * 빈 칩을 누르면 빈 목록만 나와 고장난 것처럼 읽힌다.
 */
export function PredictionCommentList({ comments }: { comments: readonly BrandPredictionComment[] }) {
  const titleId = useId()
  const [filter, setFilter] = useState<PredictionReasonCode | 'ALL'>('ALL')

  const counts = new Map<PredictionReasonCode, number>()
  for (const comment of comments) counts.set(comment.reasonCode, (counts.get(comment.reasonCode) ?? 0) + 1)
  const visible = filter === 'ALL' ? comments : comments.filter((comment) => comment.reasonCode === filter)

  return (
    <section className="brand-comments" aria-labelledby={titleId}>
      <header className="insight-chart__header">
        <h4 id={titleId}>익명 선호 의견 <small>{comments.length.toLocaleString()}건</small></h4>
        {comments.length > 0 ? (
          <div className="insight-chips" role="group" aria-label="이유로 거르기">
            <button type="button" aria-pressed={filter === 'ALL'} onClick={() => setFilter('ALL')}>
              전체 {comments.length}
            </button>
            {[...counts.entries()].map(([code, count]) => (
              <button key={code} type="button" aria-pressed={filter === code} onClick={() => setFilter(code)}>
                {REASON_LABELS[code]} {count}
              </button>
            ))}
          </div>
        ) : null}
      </header>
      {comments.length === 0 ? (
        <p className="brand-comments__empty">작성된 자유 의견이 없습니다.</p>
      ) : (
        <ul>
          {visible.map((comment, index) => (
            <li key={`${comment.submittedAt}-${index}`}>
              <span>{REASON_LABELS[comment.reasonCode]}</span>
              <p>{comment.reasonText}</p>
              <time dateTime={comment.submittedAt}>{formatDate(comment.submittedAt)}</time>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
