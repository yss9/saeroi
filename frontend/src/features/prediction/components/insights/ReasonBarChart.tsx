import { useId } from 'react'
import type { BrandPredictionReasonInsight } from '../../types/prediction.types.ts'
import { REASON_LABELS, share } from './insightFormat.ts'

/**
 * 구매 판단 이유 — 많은 순으로 선 가로 막대.
 *
 * 순서를 고정 목록이 아니라 응답 수로 정한다. 브랜드가 알고 싶은 것은 "무엇이 가장 컸나" 이고,
 * 고정 순서면 그걸 눈으로 비교해 찾아야 한다. 막대 끝의 값이 표 역할을 하므로 표를 따로 두지 않는다.
 */
export function ReasonBarChart({
  reasons,
  participantCount,
}: {
  reasons: readonly BrandPredictionReasonInsight[]
  participantCount: number
}) {
  const titleId = useId()
  const sorted = [...reasons].sort((left, right) => right.responseCount - left.responseCount)
  const max = Math.max(...sorted.map((reason) => reason.responseCount), 0)
  const top = sorted[0]

  return (
    <section className="insight-chart" aria-labelledby={titleId}>
      <header className="insight-chart__header">
        <div>
          <h4 id={titleId}>구매 판단 이유</h4>
          <p>
            {top && top.responseCount > 0
              ? `가장 많이 꼽은 이유는 ${REASON_LABELS[top.reasonCode]}(${Math.round(share(top.responseCount, participantCount) * 100)}%)예요.`
              : '아직 응답이 없습니다.'}
          </p>
        </div>
      </header>
      <ul className="insight-hbars">
        {sorted.map((reason) => {
          const rate = share(reason.responseCount, participantCount)
          return (
            <li key={reason.reasonCode}>
              <span className="insight-hbars__label">{REASON_LABELS[reason.reasonCode]}</span>
              <span className="insight-hbars__track" aria-hidden="true">
                <span
                  className="insight-hbars__bar"
                  style={{ width: max === 0 ? 0 : `${(reason.responseCount / max) * 100}%` }}
                />
              </span>
              <span className="insight-hbars__value">
                {reason.responseCount.toLocaleString()}명 · {Math.round(rate * 100)}%
              </span>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
