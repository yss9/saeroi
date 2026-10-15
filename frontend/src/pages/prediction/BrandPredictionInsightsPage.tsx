import { EmptyState, ErrorMessage, Loading } from '../../components/common/index.ts'
import { MarketInsightCard } from '../../features/prediction/components/insights/MarketInsightCard.tsx'
import { PredictionAccuracyChart } from '../../features/prediction/components/insights/PredictionAccuracyChart.tsx'
import { formatPoint, formatRate } from '../../features/prediction/components/insights/insightFormat.ts'
import { useBrandPredictionInsights } from '../../features/prediction/hooks/useBrandPredictionInsights.ts'
import { summarizeInsights } from '../../features/prediction/utils/predictionInsightStats.ts'
import '../../features/prediction/components/insights/prediction-insights.css'
import './brand-prediction-insights.css'

/**
 * 예측·선호 분석 — 브랜드가 내 상품의 예측시장을 한 화면에서 읽는다.
 *
 * 위에서 아래로 넓은 것 → 좁은 것 순이다. 전체 지표, 상품 사이의 "예측이 얼마나 맞았나" 비교,
 * 그다음 상품별 분포·이유·의견. 수치 계산은 `predictionInsightStats` 가 한다.
 */
export function BrandPredictionInsightsPage() {
  const insights = useBrandPredictionInsights()

  if (insights.isPending) return <main className="page"><Loading label="예측 결과를 불러오는 중" /></main>
  if (insights.isError) return <main className="page"><ErrorMessage error={insights.error} onRetry={() => void insights.refetch()} /></main>
  if (insights.data.length === 0) {
    return (
      <main className="page brand-prediction-page">
        <header><h1>예측·선호 분석</h1><p>상품별 판매량 예측과 소비자 선호 이유를 확인합니다.</p></header>
        <EmptyState title="예측시장이 열린 상품이 없습니다" description="관리자가 예측시장을 개설하면 결과와 선호 조사가 여기에 표시됩니다." />
      </main>
    )
  }

  const summary = summarizeInsights(insights.data)

  return (
    <main className="page brand-prediction-page">
      <header className="brand-prediction-page__intro">
        <h1>예측·선호 분석</h1>
        <p>내 상품에 열린 예측시장의 결과와 소비자 선호 이유를 익명으로 모아봅니다.</p>
      </header>

      <dl className="insight-kpis" aria-label="전체 지표">
        <div><dt>전체 시장</dt><dd>{summary.marketCount.toLocaleString()}</dd></div>
        <div><dt>누적 참여</dt><dd>{summary.participantCount.toLocaleString()}명</dd></div>
        <div><dt>총 예치</dt><dd>{formatPoint(summary.totalStake)}</dd></div>
        <div><dt>결과 확정</dt><dd>{summary.resolvedCount.toLocaleString()}</dd></div>
        <div>
          <dt>평균 적중률</dt>
          <dd>{formatRate(summary.hitRate)}</dd>
          <small>확정된 시장에서 적중 구간을 고른 참여자 비율</small>
        </div>
      </dl>

      <PredictionAccuracyChart insights={insights.data} />

      <section className="brand-prediction-list" aria-label="상품별 예측시장 분석">
        {insights.data.map((insight) => <MarketInsightCard key={insight.market.id} insight={insight} />)}
      </section>
    </main>
  )
}
