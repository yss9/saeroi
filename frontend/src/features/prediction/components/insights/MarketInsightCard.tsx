import { StatusBadge } from '../../../../components/common/index.ts'
import type { BrandPredictionInsight } from '../../types/prediction.types.ts'
import { marketStats } from '../../utils/predictionInsightStats.ts'
import {
  formatDate,
  formatPoint,
  formatQuantity,
  formatRate,
  formatSignedRate,
  MARKET_STATUS,
  share,
} from './insightFormat.ts'
import { PredictionCommentList } from './PredictionCommentList.tsx'
import { PredictionDistributionChart } from './PredictionDistributionChart.tsx'
import { ReasonBarChart } from './ReasonBarChart.tsx'

/** 상품 하나의 예측시장 분석. 머리의 지표 → 분포 · 이유 → 의견 순으로 읽는다. */
export function MarketInsightCard({ insight }: { insight: BrandPredictionInsight }) {
  const { market } = insight
  const stats = marketStats(insight)
  const resolved = market.status === 'RESOLVED' && market.soldQuantity !== null
  const median = stats.medianBucketIndex === null
    ? null
    : insight.buckets.find((bucket) => bucket.index === stats.medianBucketIndex) ?? null

  return (
    <article className="insight-card brand-prediction-card">
      <header className="brand-prediction-card__header">
        <div>
          <StatusBadge tone={MARKET_STATUS[market.status].tone}>{MARKET_STATUS[market.status].label}</StatusBadge>
          <h3>{market.productName}</h3>
          <p>
            {formatDate(market.bettingEndsAt)} 예측 마감 · 판매 {formatDate(market.salesStartsAt)}~{formatDate(market.salesEndsAt)}
            {' · '}{market.unitPrice.toLocaleString()}원 · 생산 {market.productionQuantity.toLocaleString()}개
          </p>
        </div>
        <dl className="insight-kpis insight-kpis--compact">
          <div><dt>참여자</dt><dd>{insight.participantCount.toLocaleString()}명</dd></div>
          <div><dt>총 예치</dt><dd>{formatPoint(insight.totalStakeAmount)}</dd></div>
          <div>
            <dt>실제 판매</dt>
            <dd>{resolved ? `${market.soldQuantity?.toLocaleString()}개 (${formatRate(stats.sellThrough)})` : '집계 전'}</dd>
          </div>
          <div><dt>적중률</dt><dd>{formatRate(stats.hitRate)}</dd></div>
        </dl>
      </header>

      {insight.participantCount > 0 ? (
        <p className="insight-summary">
          중앙 구간은{' '}
          <strong>{median ? `${median.lowerBound.toLocaleString()}~${median.upperBound.toLocaleString()}개` : '—'}</strong>
          (참여자 {Math.round(share(median?.responseCount ?? 0, insight.participantCount) * 100)}%)이고, 예치로 가중한 기대
          판매량은 <strong>{formatQuantity(stats.expectedByStake)}</strong>
          {resolved ? (
            <>
              입니다. 실제로는 <strong>{formatQuantity(market.soldQuantity)}</strong> 팔려 예측 오차는{' '}
              <strong>{formatSignedRate(stats.errorRate)}</strong>예요.
            </>
          ) : '입니다.'}
        </p>
      ) : null}

      <div className="brand-prediction-card__columns">
        <PredictionDistributionChart insight={insight} />
        <ReasonBarChart reasons={insight.reasons} participantCount={insight.participantCount} />
      </div>

      <PredictionCommentList comments={insight.comments} />
    </article>
  )
}
