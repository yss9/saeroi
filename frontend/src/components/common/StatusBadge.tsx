import type { ReactNode } from 'react'

/**
 * 상태 색상은 도메인마다 다른 문자열을 쓰지만(TRADE 의 'SHIPPED', MATERIAL 의 'ACTIVE' …)
 * 사용자에게 주는 신호는 몇 가지로 수렴한다. 그래서 공통 컴포넌트는
 * **도메인 상태를 알지 못하고** 톤만 받는다.
 *
 * 각 도메인은 자기 상태 → tone 매핑을 feature 안에 둔다.
 *   예) features/trade/utils/tradeStatusTone.ts
 */
export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'

export interface StatusBadgeProps {
  tone?: StatusTone
  children: ReactNode
}

export function StatusBadge({ tone = 'neutral', children }: StatusBadgeProps) {
  return <span className={`ui-badge ui-badge--${tone}`}>{children}</span>
}
