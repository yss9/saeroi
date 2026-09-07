export interface LoadingProps {
  /** 안내 문구. 스크린 리더에도 읽힌다. */
  label?: string
  /** 목록 자리를 미리 차지하는 스켈레톤으로 표시한다. */
  variant?: 'spinner' | 'skeleton'
  /** variant='skeleton' 일 때 표시할 줄 수 */
  rows?: number
}

/**
 * 로딩 표현은 상황에 따라 다르다 (ARCHITECTURE.md §Loading 참고).
 *   목록/상세 조회 → 이 컴포넌트 (skeleton)
 *   버튼 요청     → Button 의 loading prop
 *   블록체인 TX   → TransactionStatus
 */
export function Loading({ label = '불러오는 중', variant = 'spinner', rows = 3 }: LoadingProps) {
  if (variant === 'skeleton') {
    return (
      <div className="ui-skeleton" role="status" aria-label={label}>
        {Array.from({ length: rows }, (_, index) => (
          <div className="ui-skeleton__row" key={index} />
        ))}
      </div>
    )
  }

  return (
    <div className="ui-loading" role="status">
      <span className="ui-loading__spinner" aria-hidden="true" />
      <span className="ui-loading__label">{label}</span>
    </div>
  )
}
