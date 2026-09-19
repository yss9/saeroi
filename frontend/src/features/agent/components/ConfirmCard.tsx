import { useState } from 'react'
import { useCompleteAction, useRejectAction } from '../hooks/useAgentConversation.ts'
import { StatusOrActions } from './PaymentCard.tsx'
import type { PendingAction } from '../types/agent.types.ts'

/**
 * 단순 확인 카드.
 *
 * 지갑도 결제도 필요 없지만 <b>되돌리기 어려운 쓰기</b>라 사람이 한 번 봐야 하는 작업이다.
 * 수령 확정이 대표적이다 — 확정하는 순간 소유권이 넘어오고 판매자에게 돈이 간다. 담당자가
 * 물건을 실제로 받았는지는 서버가 알 수 없으므로 모델의 판단에 맡기지 않는다.
 */
export function ConfirmCard({ action }: { action: PendingAction }) {
  const complete = useCompleteAction()
  const reject = useRejectAction()
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setError(null)
    try {
      await complete.mutateAsync({ actionId: action.id })
    } catch {
      // 도메인이 거절한 경우(아직 발송 전 등)는 서버가 대화에 이유를 남긴다.
      setError('처리하지 못했습니다. 대화 내용을 확인해 주세요.')
    }
  }

  const disabled = action.status !== 'PENDING' || complete.isPending

  return (
    <div className="agent-card agent-card--confirm">
      <p className="agent-card__title">확인이 필요합니다</p>
      <p className="agent-card__raw">{action.summary}</p>

      <StatusOrActions
        action={action}
        disabled={disabled}
        busyLabel="처리 중…"
        confirmLabel="확인"
        onConfirm={handleConfirm}
        isBusy={complete.isPending}
        onReject={() => void reject.mutateAsync(action.id)}
      />

      {error && <p className="agent-card__error">{error}</p>}
    </div>
  )
}
