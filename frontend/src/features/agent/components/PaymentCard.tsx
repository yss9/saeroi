import { useState } from 'react'
import { useCompleteAction } from '../hooks/useAgentConversation.ts'
import type { PendingAction, PurchasePayload } from '../types/agent.types.ts'

/**
 * 결제 확인 카드.
 *
 * <b>에이전트는 결제를 직접 하지 못한다.</b> 모델이 결제 tool 을 불러도 서버는 실행하지
 * 않고 이 카드만 만든다. 담당자가 누른 뒤에야 거래가 생성되고 결제 세션이 열린다.
 *
 * 그래서 이 버튼을 누르는 순간 두 가지가 한 번에 일어난다 — 거래 생성과 결제 준비.
 * 묶은 이유는 거래를 만들면 판매 잔여 수량이 즉시 줄어들기 때문이다. 거절하면 거래
 * 자체가 만들어지지 않아 결제되지 않은 유령 거래가 남지 않는다.
 */
export function PaymentCard({ action }: { action: PendingAction }) {
  const complete = useCompleteAction()
  const [error, setError] = useState<string | null>(null)

  const detail = parsePayload(action.payloadJson)

  async function handlePay() {
    setError(null)
    try {
      const updated = await complete.mutateAsync({ actionId: action.id })
      // 서버가 거래를 만들고 결제 세션을 연다. 그 결과에서 결제창 주소를 꺼낸다.
      const redirectUrl = findRedirectUrl(updated.actions, action.id)
      if (redirectUrl) {
        window.location.href = redirectUrl
        return
      }
      setError('결제창 주소를 받지 못했습니다. 대화에서 다시 시도해 주세요.')
    } catch {
      // 도메인이 거절한 경우(재고 소진 등)는 서버가 대화에 이유를 남긴다.
      setError('결제를 시작하지 못했습니다. 대화 내용을 확인해 주세요.')
    }
  }

  const disabled = action.status !== 'PENDING' || complete.isPending

  return (
    <div className="agent-card agent-card--payment">
      <p className="agent-card__title">소재 구매 결제</p>

      <dl className="agent-card__detail">
        {detail?.quantityGram != null && (
          <>
            <dt>수량</dt>
            <dd>{(detail.quantityGram / 1000).toLocaleString()} kg</dd>
          </>
        )}
        {detail?.totalPrice != null && (
          <>
            <dt>결제 금액</dt>
            <dd>{detail.totalPrice.toLocaleString()} 원</dd>
          </>
        )}
      </dl>

      {/*
        승인 전에는 인자를 그대로 보여준다. 모델이 쓴 요약이 아니라 실제 실행될 값이라야
        담당자가 무엇에 동의하는지 정확히 안다.
      */}
      {!detail && <p className="agent-card__raw">{action.summary}</p>}

      <StatusOrActions
        action={action}
        disabled={disabled}
        busyLabel="결제창으로 이동 중…"
        confirmLabel="결제하기"
        onConfirm={handlePay}
        isBusy={complete.isPending}
      />

      {error && <p className="agent-card__error">{error}</p>}
    </div>
  )
}

/** 카드가 이미 처리됐으면 상태만, 아니면 버튼을 보여준다. */
export function StatusOrActions({
  action,
  disabled,
  busyLabel,
  confirmLabel,
  onConfirm,
  isBusy,
  onReject,
}: {
  action: PendingAction
  disabled: boolean
  busyLabel: string
  confirmLabel: string
  onConfirm: () => void
  isBusy: boolean
  onReject?: () => void
}) {
  if (action.status === 'COMPLETED') {
    return <p className="agent-card__status">완료되었습니다.</p>
  }
  if (action.status === 'REJECTED') {
    return <p className="agent-card__status">취소했습니다.</p>
  }
  if (action.status === 'EXPIRED') {
    return <p className="agent-card__status">기한이 지났습니다. 대화에서 다시 요청해 주세요.</p>
  }

  return (
    <div className="agent-card__actions">
      <button type="button" onClick={onConfirm} disabled={disabled}>
        {isBusy ? busyLabel : confirmLabel}
      </button>
      {onReject && (
        <button type="button" className="agent-card__reject" onClick={onReject} disabled={disabled}>
          취소
        </button>
      )}
    </div>
  )
}

function parsePayload(payloadJson: string): Partial<PurchasePayload> | null {
  try {
    return JSON.parse(payloadJson) as Partial<PurchasePayload>
  } catch {
    // 모델이 만든 인자라 형태가 어긋날 수 있다. 그때는 summary 를 그대로 보여준다.
    return null
  }
}

/**
 * 완료 응답에서 이 카드의 결제창 주소를 찾는다.
 *
 * `payloadJson`(모델이 만든 인자)이 아니라 `resultJson`(서버가 실행한 결과)에서 읽는다 —
 * 결제 세션은 승인 시점에 서버가 열기 때문이다.
 */
function findRedirectUrl(actions: PendingAction[], actionId: number): string | null {
  const updated = actions.find((candidate) => candidate.id === actionId)
  if (!updated?.resultJson) return null
  try {
    const result = JSON.parse(updated.resultJson) as { redirectUrl?: string }
    return result.redirectUrl ?? null
  } catch {
    return null
  }
}
