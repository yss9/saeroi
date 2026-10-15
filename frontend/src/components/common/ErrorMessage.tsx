import { toErrorMessage } from '../../utils/errors.ts'
import { Button } from './Button.tsx'

export interface ErrorMessageProps {
  /** AppError, Error, 문자열 무엇이든 받는다 — 호출부가 타입을 좁힐 필요가 없다. */
  error: unknown
  /** 재시도 가능하면 넘긴다. TanStack Query 의 refetch 를 그대로 연결하면 된다. */
  onRetry?: () => void
}

export function ErrorMessage({ error, onRetry }: ErrorMessageProps) {
  return (
    <div className="ui-error" role="alert">
      <p className="ui-error__message">{toErrorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  )
}
