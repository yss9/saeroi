import { useId, useState } from 'react'
import type { BrandPredictionInsight } from '../../types/prediction.types.ts'
import { marketStats } from '../../utils/predictionInsightStats.ts'
import { formatQuantity, formatRate, formatSignedRate } from './insightFormat.ts'

/**
 * 소비자 예측 vs 실제 판매 — 상품마다 한 줄인 점 그래프.
 *
 * **가로축은 생산 수량 대비 비율(0~100%)이다.** 상품마다 생산량이 달라 개수로 그으면 줄끼리
 * 비교할 수 없다. 비율로 맞추면 "소비자는 절반 팔린다고 봤는데 80% 팔렸다" 가 모든 줄에서 같은
 * 자로 읽힌다. 정확한 개수는 줄 끝 글자와 툴팁·표가 말한다.
 *
 * 예측은 예치 가중 기대 판매량이다 — 확신을 건 만큼 무게를 준 소비자의 판단.
 */
export function PredictionAccuracyChart({ insights }: { insights: readonly BrandPredictionInsight[] }) {
  const titleId = useId()
  const [active, setActive] = useState<number | null>(null)
  const rows = insights.map((insight) => ({ insight, stats: marketStats(insight) }))

  return (
    <section className="insight-card insight-chart" aria-labelledby={titleId}>
      <header className="insight-chart__header">
        <div>
          <h2 id={titleId}>소비자 예측 vs 실제 판매</h2>
          <p>생산 수량 대비 비율 · 예측은 예치 포인트로 가중한 기대 판매량</p>
        </div>
      </header>
      <ul className="insight-legend" aria-label="범례">
        <li><span className="insight-legend__dot insight-legend__dot--predict" aria-hidden="true" />소비자 예측</li>
        <li><span className="insight-legend__dot insight-legend__dot--actual" aria-hidden="true" />실제 판매</li>
        <li><span className="insight-legend__track" aria-hidden="true" />생산 수량</li>
      </ul>

      <div className="insight-dots">
        <div className="insight-dots__axis" aria-hidden="true">
          {[0, 25, 50, 75, 100].map((tick) => (
            <span key={tick} style={{ left: `${tick}%` }}>{tick}%</span>
          ))}
        </div>
        <ul>
          {rows.map(({ insight, stats }, row) => {
            const { market } = insight
            const production = market.productionQuantity
            const predicted = stats.expectedByStake
            const sold = market.soldQuantity
            const toPercent = (value: number) => (production > 0 ? Math.min(100, (value / production) * 100) : 0)
            const summary = [
              `예측 ${formatQuantity(predicted)}`,
              sold === null ? '실제 집계 전' : `실제 ${formatQuantity(sold)}`,
              `생산 ${production.toLocaleString()}개`,
            ].join(' · ')
            return (
              <li
                key={market.id}
                className="insight-dots__row"
                tabIndex={0}
                aria-label={`${market.productName}: ${summary}`}
                onPointerEnter={() => setActive(row)}
                onPointerLeave={() => setActive(null)}
                onFocus={() => setActive(row)}
                onBlur={() => setActive(null)}
              >
                <span className="insight-dots__name">{market.productName}</span>
                <span className="insight-dots__plot" aria-hidden="true">
                  <span className="insight-dots__track" />
                  {predicted !== null && sold !== null ? (
                    <span
                      className="insight-dots__gap"
                      style={{
                        left: `${Math.min(toPercent(predicted), toPercent(sold))}%`,
                        width: `${Math.abs(toPercent(predicted) - toPercent(sold))}%`,
                      }}
                    />
                  ) : null}
                  {predicted !== null ? (
                    <span className="insight-dots__dot insight-dots__dot--predict" style={{ left: `${toPercent(predicted)}%` }} />
                  ) : null}
                  {sold !== null ? (
                    <span className="insight-dots__dot insight-dots__dot--actual" style={{ left: `${toPercent(sold)}%` }} />
                  ) : null}
                  {active === row ? (
                    <span className="insight-tooltip insight-tooltip--row" role="tooltip">
                      <strong>{sold === null ? `예측 ${formatQuantity(predicted)}` : `오차 ${formatSignedRate(stats.errorRate)}`}</strong>
                      <span>{summary}</span>
                    </span>
                  ) : null}
                </span>
                <span className="insight-dots__value">
                  {sold === null ? `예측 ${formatQuantity(predicted)}` : `${formatRate(stats.sellThrough)} 판매`}
                </span>
              </li>
            )
          })}
        </ul>
      </div>

      <details className="insight-table">
        <summary>표로 보기</summary>
        <table>
          <caption className="ui-visually-hidden">상품별 소비자 예측과 실제 판매</caption>
          <thead>
            <tr>
              <th scope="col">상품</th><th scope="col">생산</th><th scope="col">소비자 예측</th>
              <th scope="col">실제 판매</th><th scope="col">판매율</th><th scope="col">예측 오차</th><th scope="col">적중률</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ insight, stats }) => (
              <tr key={insight.market.id}>
                <th scope="row">{insight.market.productName}</th>
                <td>{insight.market.productionQuantity.toLocaleString()}개</td>
                <td>{formatQuantity(stats.expectedByStake)}</td>
                <td>{formatQuantity(insight.market.soldQuantity)}</td>
                <td>{formatRate(stats.sellThrough)}</td>
                <td>{formatSignedRate(stats.errorRate)}</td>
                <td>{formatRate(stats.hitRate)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </section>
  )
}
