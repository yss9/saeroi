import { EmptyState, ErrorMessage, Loading, StatusBadge, type StatusTone } from '../../components/common/index.ts'
import { useBrandPredictionInsights } from '../../features/prediction/hooks/useBrandPredictionInsights.ts'
import { formatPredictionPoint } from '../../features/prediction/services/predictionContractService.ts'
import type {
  BrandPredictionInsight,
  PredictionMarket,
  PredictionReasonCode,
} from '../../features/prediction/types/prediction.types.ts'
import './brand-prediction-insights.css'

const REASON_LABELS: Record<PredictionReasonCode, string> = {
  DESIGN: '디자인',
  PRICE: '가격',
  BRAND: '브랜드',
  SUSTAINABILITY: '친환경 가치',
  PRACTICALITY: '실용성',
  TREND: '트렌드',
  OTHER: '기타',
}

const STATUS: Record<PredictionMarket['status'], { label: string; tone: StatusTone }> = {
  OPEN: { label: '예측 진행 중', tone: 'info' },
  RESOLVING: { label: '결과 집계 중', tone: 'warning' },
  RESOLVED: { label: '결과 확정', tone: 'success' },
  CANCELED: { label: '시장 취소', tone: 'danger' },
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value))
}

function percent(value: number, total: number) {
  if (total === 0) return '0%'
  return `${Math.round((value / total) * 100)}%`
}

function point(value: string) {
  return formatPredictionPoint(BigInt(value))
}

function MarketInsight({ insight }: { insight: BrandPredictionInsight }) {
  const { market } = insight
  const soldRate = market.soldQuantity === null || market.productionQuantity === 0
    ? null
    : Math.round((market.soldQuantity / market.productionQuantity) * 100)

  return (
    <article className="brand-prediction-card">
      <header className="brand-prediction-card__header">
        <div>
          <StatusBadge tone={STATUS[market.status].tone}>{STATUS[market.status].label}</StatusBadge>
          <h3>{market.productName}</h3>
          <p>{formatDate(market.bettingEndsAt)} 예측 마감 · 생산 {market.productionQuantity.toLocaleString()}개</p>
        </div>
        <dl className="brand-prediction-card__totals">
          <div><dt>참여자</dt><dd>{insight.participantCount.toLocaleString()}명</dd></div>
          <div><dt>총 예치</dt><dd>{point(insight.totalStakeAmount)} PRED</dd></div>
          <div>
            <dt>실제 판매</dt>
            <dd>{market.soldQuantity === null ? '집계 전' : `${market.soldQuantity.toLocaleString()}개 (${soldRate}%)`}</dd>
          </div>
        </dl>
      </header>

      <div className="brand-prediction-card__columns">
        <section aria-labelledby={`bucket-title-${market.id}`}>
          <h4 id={`bucket-title-${market.id}`}>예상 판매량 분포</h4>
          <ul className="brand-insight-bars">
            {insight.buckets.map((bucket) => {
              const share = percent(bucket.responseCount, insight.participantCount)
              return (
                <li key={bucket.index}>
                  <div className="brand-insight-bars__label">
                    <span>{bucket.lowerBound.toLocaleString()}~{bucket.upperBound.toLocaleString()}개</span>
                    <strong>{bucket.responseCount}명 · {share}</strong>
                  </div>
                  <div className="brand-insight-bars__track" aria-hidden="true">
                    <span style={{ width: share }} />
                  </div>
                  <small>{point(bucket.stakeAmount)} PRED 예치</small>
                </li>
              )
            })}
          </ul>
        </section>

        <section aria-labelledby={`reason-title-${market.id}`}>
          <h4 id={`reason-title-${market.id}`}>구매 판단 이유</h4>
          <ul className="brand-reason-list">
            {insight.reasons.map((reason) => (
              <li key={reason.reasonCode}>
                <span>{REASON_LABELS[reason.reasonCode]}</span>
                <strong>{reason.responseCount}명 · {percent(reason.responseCount, insight.participantCount)}</strong>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="brand-comments" aria-labelledby={`comment-title-${market.id}`}>
        <h4 id={`comment-title-${market.id}`}>익명 선호 의견</h4>
        {insight.comments.length === 0 ? (
          <p className="brand-comments__empty">작성된 자유 의견이 없습니다.</p>
        ) : (
          <ul>
            {insight.comments.map((comment, index) => (
              <li key={`${comment.submittedAt}-${index}`}>
                <span>{REASON_LABELS[comment.reasonCode]}</span>
                <p>{comment.reasonText}</p>
                <time dateTime={comment.submittedAt}>{formatDate(comment.submittedAt)}</time>
              </li>
            ))}
          </ul>
        )}
      </section>
    </article>
  )
}

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

  const participants = insights.data.reduce((sum, item) => sum + item.participantCount, 0)
  const resolved = insights.data.filter((item) => item.market.status === 'RESOLVED').length

  return (
    <main className="page brand-prediction-page">
      <header className="brand-prediction-page__intro">
        <div><h1>예측·선호 분석</h1><p>내 상품에 열린 예측시장의 결과와 소비자 선호 이유를 익명으로 모아봅니다.</p></div>
        <dl>
          <div><dt>전체 시장</dt><dd>{insights.data.length}</dd></div>
          <div><dt>결과 확정</dt><dd>{resolved}</dd></div>
          <div><dt>누적 참여</dt><dd>{participants.toLocaleString()}명</dd></div>
        </dl>
      </header>
      <section className="brand-prediction-list" aria-label="상품별 예측시장 분석">
        {insights.data.map((insight) => <MarketInsight key={insight.market.id} insight={insight} />)}
      </section>
    </main>
  )
}
