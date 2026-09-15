import { ArrowRight } from '@phosphor-icons/react'
import type {
  ConsumerOrderStage,
  ConsumerOrderStageSummary,
} from '../types/consumerMyPage.types.ts'
import './consumer-mypage.css'

const orderStageMeta: Record<ConsumerOrderStage, { label: string; description: string }> = {
  PAYMENT_PENDING: { label: '결제 대기', description: '결제를 기다리고 있어요' },
  PREPARING: { label: '상품 준비', description: '브랜드에서 준비 중이에요' },
  SHIPPING: { label: '배송 중', description: '새로운 쓰임이 오고 있어요' },
  DELIVERED: { label: '배송 완료', description: '배송이 완료됐어요' },
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
 * 칸 강조는 **건수**를 따른다. 이 표의 질문이 "지금 몇 건이 어디까지 왔나" 이므로, 비어
 * 있는 칸은 눈이 그냥 지나가고 내 주문이 있는 칸이 바로 보여야 한다.
 */
export function ConsumerOrderSummary({ stages }: { stages: readonly ConsumerOrderStageSummary[] }) {
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
          return (
            // **건수가 있는 칸만 강조한다.** 자리로 칠하면(예전 CSS 가 그랬다) 0 건인
            // 칸이 눈에 띄고 내 주문이 있는 칸은 회색으로 남아, 표가 정확히 반대로 읽힌다.
            <li key={stage} className={count > 0 ? 'is-active' : undefined}>
              <span className="consumer-order-steps__number">{count}</span>
              <strong>{meta.label}</strong>
              <span>{meta.description}</span>
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
