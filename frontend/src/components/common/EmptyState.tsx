import type { ReactNode } from 'react'

export interface EmptyStateProps {
  title: string
  /** 왜 비어 있는지, 무엇을 하면 되는지 */
  description?: string
  /** 다음 행동으로 이어주는 버튼 등 */
  action?: ReactNode
}

/**
 * "결과 없음" 과 "에러" 는 다른 상태다. 조회에 성공했는데 데이터가 0건이면 이걸 쓰고,
 * 조회 자체가 실패했으면 ErrorMessage 를 쓴다. 둘을 섞으면 사용자가
 * 재시도해야 할 상황인지 아닌지 알 수 없다.
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="ui-empty">
      <p className="ui-empty__title">{title}</p>
      {description && <p className="ui-empty__description">{description}</p>}
      {action && <div className="ui-empty__action">{action}</div>}
    </div>
  )
}
