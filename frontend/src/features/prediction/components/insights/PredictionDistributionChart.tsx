import { useId, useState } from 'react'
import type { BrandPredictionInsight } from '../../types/prediction.types.ts'
import { positionInBuckets } from '../../utils/predictionInsightStats.ts'
import { formatPoint, share } from './insightFormat.ts'

type Metric = 'count' | 'stake'

const METRICS: readonly { value: Metric; label: string }[] = [
  { value: 'count', label: '참여 인원' },
  { value: 'stake', label: '예치 포인트' },
]

/**
 * 예상 판매량 구간별 분포 — 세로 막대.
 *
 * **두 가지 무게로 본다.** 인원은 "몇 명이 그렇게 봤나", 예치는 "얼마나 확신을 걸었나" 다. 둘이
 * 다르게 기울면 그 차이가 곧 신호라 전환해서 비교하게 둔다. 한 축에 두 척도를 겹치지 않는다.
 *
 * **실제 판매량을 막대 위에 선으로 세운다.** 결과가 확정되면 적중 구간만 진하게, 나머지는 한
 * 단계 옅게 둔다 — 예측이 실제에서 얼마나 떨어져 있었는지 한눈에 읽힌다.
 */
export function PredictionDistributionChart({ insight }: { insight: BrandPredictionInsight }) {
  const [metric, setMetric] = useState<Metric>('count')
  const [active, setActive] = useState<number | null>(null)
  const titleId = useId()
  const { market } = insight

  const buckets = [...insight.buckets].sort((left, right) => left.index - right.index)
  const values = buckets.map((bucket) => (metric === 'count' ? bucket.responseCount : Number(bucket.stakeAmount)))
  const total = values.reduce((sum, value) => sum + value, 0)
  const max = Math.max(...values, 0)
  const resolved = market.status === 'RESOLVED' && market.soldQuantity !== null
  const soldPosition = resolved ? positionInBuckets(buckets, market.soldQuantity as number) : null
  // 막대 끝에는 가장 큰 막대와 적중 구간만 값을 단다. 나머지는 툴팁과 표가 맡는다.
  const labelled = new Set<number>()
  if (max > 0) labelled.add(values.indexOf(max))
  if (resolved && market.winningBucketIndex !== null) labelled.add(market.winningBucketIndex)

  function valueText(slot: number) {
    const bucket = buckets[slot]
    return metric === 'count'
      ? `${bucket.responseCount.toLocaleString()}명`
      : formatPoint(bucket.stakeAmount)
  }

  return (
    <section className="insight-chart" aria-labelledby={titleId}>
      <header className="insight-chart__header">
        <div>
          <h4 id={titleId}>예상 판매량 분포</h4>
          <p>소비자가 고른 판매량 구간 · {metric === 'count' ? '참여 인원' : '예치 포인트'} 기준</p>
        </div>
        <div className="insight-segmented" role="group" aria-label="분포 기준">
          {METRICS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={metric === option.value}
              onClick={() => setMetric(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </header>

      {resolved ? (
        <ul className="insight-legend" aria-label="범례">
          <li><span className="insight-legend__swatch insight-legend__swatch--predict" aria-hidden="true" />적중 구간</li>
          <li><span className="insight-legend__swatch insight-legend__swatch--predict-soft" aria-hidden="true" />그 밖의 구간</li>
          <li><span className="insight-legend__line insight-legend__line--actual" aria-hidden="true" />실제 판매 {market.soldQuantity?.toLocaleString()}개</li>
        </ul>
      ) : null}

      <div className="insight-columns" style={{ gridTemplateColumns: `repeat(${buckets.length}, minmax(0, 1fr))` }}>
        <div className="insight-columns__plot" aria-hidden={total === 0 ? true : undefined}>
          {buckets.map((bucket, slot) => {
            const value = values[slot]
            const winning = resolved && bucket.index === market.winningBucketIndex
            const height = max === 0 ? 0 : (value / max) * 100
            const description = `${bucket.lowerBound.toLocaleString()}~${bucket.upperBound.toLocaleString()}개 구간, ${valueText(slot)}, ${Math.round(share(value, total) * 100)}%${winning ? ', 적중 구간' : ''}`
            return (
              <div
                key={bucket.index}
                className="insight-columns__slot"
                tabIndex={0}
                aria-label={description}
                onPointerEnter={() => setActive(slot)}
                onPointerLeave={() => setActive(null)}
                onFocus={() => setActive(slot)}
                onBlur={() => setActive(null)}
              >
                {/* 툴팁이 뜨면 같은 값을 말하므로 끝 글자를 감춘다 — 겹치면 둘 다 안 읽힌다. */}
                {labelled.has(slot) && value > 0 && active !== slot ? (
                  <span className="insight-columns__value" style={{ bottom: `${height}%` }}>{valueText(slot)}</span>
                ) : null}
                <span
                  className={[
                    'insight-columns__bar',
                    resolved && !winning ? 'insight-columns__bar--soft' : '',
                    active === slot ? 'insight-columns__bar--active' : '',
                  ].join(' ')}
                  style={{ height: `${height}%` }}
                />
                {active === slot ? (
                  <span className="insight-tooltip" role="tooltip" style={{ bottom: `calc(${height}% + 8px)` }}>
                    <strong>{valueText(slot)} · {Math.round(share(value, total) * 100)}%</strong>
                    <span>{bucket.lowerBound.toLocaleString()}~{bucket.upperBound.toLocaleString()}개{winning ? ' · 적중' : ''}</span>
                  </span>
                ) : null}
              </div>
            )
          })}
          {soldPosition !== null ? (
            <span
              className="insight-columns__marker"
              style={{ left: `${(soldPosition / buckets.length) * 100}%` }}
              aria-hidden="true"
            />
          ) : null}
        </div>
        {buckets.map((bucket) => (
          <span key={bucket.index} className="insight-columns__axis-label">
            {bucket.lowerBound.toLocaleString()}~{bucket.upperBound.toLocaleString()}
          </span>
        ))}
      </div>

      {total === 0 ? <p className="insight-chart__empty">아직 {metric === 'count' ? '참여자' : '예치'}가 없습니다.</p> : null}

      <details className="insight-table">
        <summary>표로 보기</summary>
        <table>
          <caption className="ui-visually-hidden">구간별 참여 인원과 예치 포인트</caption>
          <thead><tr><th scope="col">판매량 구간</th><th scope="col">참여 인원</th><th scope="col">비율</th><th scope="col">예치</th></tr></thead>
          <tbody>
            {buckets.map((bucket) => (
              <tr key={bucket.index}>
                <th scope="row">
                  {bucket.lowerBound.toLocaleString()}~{bucket.upperBound.toLocaleString()}개
                  {resolved && bucket.index === market.winningBucketIndex ? ' (적중)' : ''}
                </th>
                <td>{bucket.responseCount.toLocaleString()}명</td>
                <td>{Math.round(share(bucket.responseCount, insight.participantCount) * 100)}%</td>
                <td>{formatPoint(bucket.stakeAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  )
}
