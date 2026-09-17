import { ArrowRight } from '@phosphor-icons/react'
import type {
  ConsumerOrderStage,
  ConsumerOrderStageSummary,
} from '../types/consumerMyPage.types.ts'
import './consumer-mypage.css'

const orderStageMeta: Record<ConsumerOrderStage, { label: string }> = {
  PAYMENT_PENDING: { label: '결제 대기' },
  PREPARING: { label: '상품 준비' },
  SHIPPING: { label: '배송 중' },
  DELIVERED: { label: '배송 완료' },
}

/**
 * 주문 현황.
 *
 * **주문 내역 아래에 둔다.** 목록이 "무엇을 샀나" 라면 이 표는 "지금 몇 건이 어디까지
 * 왔나" 다 — 같은 화면에서 이어 보는 값이지, 마이페이지 첫 화면에서 물을 값이 아니다.
 *
 * **한 건도 없으면 그리지 않는다.** 0 이 네 칸 늘어선 표는 "아직 주문이 없다" 를 말하는
 * 가장 나쁜 방법이다 — 처음 온 사람은 그 표가 무엇을 세는 것인지조차 모른다.
 *
 * **칸을 누르면 그 상태의 주문만 아래 목록에 남는다(2026-09-17).** 배경은 모든 칸이
 * 같은 아이보리색이고(예전엔 건수>0인 칸만 파랗게 칠했다), 지금 클릭된 칸만 파란
 * 테두리로 표시한다 — hover 시에는 채워진 파란색으로 "누를 수 있다"를 보여준다.
 */
export function ConsumerOrderSummary({
  stages,
  selectedStage,
  onSelectStage,
}: {
  stages: readonly ConsumerOrderStageSummary[]
  selectedStage: ConsumerOrderStage | null
  onSelectStage: (stage: ConsumerOrderStage | null) => void
}) {
  const total = stages.reduce((sum, stage) => sum + stage.count, 0)
  if (total === 0) return null

  return (
    <section className="consumer-mypage-card consumer-order-overview" aria-labelledby="order-overview-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">ORDER STATUS</p>
          <h2 id="order-overview-title">주문 현황</h2>
        </div>
      </div>

      <ol className="consumer-order-steps">
        {stages.map(({ stage, count }, index) => {
          const meta = orderStageMeta[stage]
          const isSelected = selectedStage === stage
          return (
            <li key={stage} className={isSelected ? 'is-selected' : undefined}>
              <button
                type="button"
                className="consumer-order-steps__button"
                aria-pressed={isSelected}
                onClick={() => onSelectStage(isSelected ? null : stage)}
              >
                <span className="consumer-order-steps__number">{count}</span>
                <strong>{meta.label}</strong>
              </button>
              {index < stages.length - 1 && (
                <ArrowRight className="consumer-order-steps__arrow" aria-hidden="true" />
              )}
            </li>
          )
        })}
      </ol>
    </section>
  )
}
