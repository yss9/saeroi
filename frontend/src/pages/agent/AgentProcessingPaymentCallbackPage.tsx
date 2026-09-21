import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { processingRequestApi } from '../../features/processing/api/processingRequestApi.ts'
import {
  useActiveConversation,
  useReportEvent,
} from '../../features/agent/hooks/useAgentConversation.ts'
import './agent.css'

/**
 * 가공비 결제에서 대화창으로 돌아오는 지점.
 *
 * <p>소재 결제의 `AgentPaymentCallbackPage` 와 같은 일을 가공 쪽에서 한다. 기본 복귀 주소
 * (`/processing/jobs/{id}/payment/callback`)로 가면 대화창이 작업 상세 화면으로 바뀌어
 * 담당자가 하던 이야기를 잃는다.
 *
 * <p><b>소재 결제와 다른 점이 하나 있다.</b> 결제창을 닫고 온 경우(`pg_token` 없음)에
 * 여기서 작업을 취소하지 않는다. 가공 작업은 결제 기한까지 열려 있고 담당자가 다시 결제할
 * 수 있어서, 임의로 취소하면 고른 견적이 날아간다 — 소재 거래에서 수량이 묶이는 것과 달리
 * 여기서는 기다리는 것이 맞다. 기한이 지나면 배치({@code ProcessingJobExpiryWorker})가
 * 정리한다.
 */
export default function AgentProcessingPaymentCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { data: conversation } = useActiveConversation()
  const reportEvent = useReportEvent()
  const [error, setError] = useState<string | null>(null)

  // pg_token 은 한 번만 쓸 수 있다. StrictMode 가 effect 를 두 번 돌리면 두 번째 승인이
  // 거절되고, 담당자는 성공한 결제를 실패로 본다.
  const sent = useRef(false)

  const jobId = Number(params.get('jobId'))
  const pgToken = params.get('pg_token')

  useEffect(() => {
    if (sent.current) return
    if (!Number.isInteger(jobId) || jobId <= 0 || !conversation) return
    sent.current = true

    void (async () => {
      try {
        const message = pgToken
          ? await confirmAndDescribe(jobId, pgToken)
          : notPaidMessage(jobId)

        await reportEvent.mutateAsync({ conversationId: conversation.id, message })
      } catch {
        setError('가공비 결제 승인에 실패했습니다. 대화에서 상태를 확인해 주세요.')
        return
      }
      navigate('/agent', { replace: true })
    })()
  }, [conversation, jobId, navigate, pgToken, reportEvent])

  return (
    <main className="agent-page">
      <section className="agent-chat">
        <div className="agent-chat__log">
          {error ? (
            <>
              <p className="agent-chat__error">{error}</p>
              <button type="button" onClick={() => navigate('/agent', { replace: true })}>
                대화로 돌아가기
              </button>
            </>
          ) : (
            <p className="agent-chat__thinking">
              <span className="agent-chat__dots">
                <i />
                <i />
                <i />
              </span>
              결제를 확인하고 대화로 돌아갑니다
            </p>
          )}
        </div>
      </section>
    </main>
  )
}

/**
 * 결제를 확정하고 대화에 알릴 문장을 만든다.
 *
 * **다음 단계를 여기서 단정하지 않는다.** 예전에는 "다음은 소재를 업체로 보내는 단계" 라고
 * 적어 보냈는데, 결제 직후 작업은 `PAID` 이고 인계는 업체가 수락한 뒤에만 된다
 * (`ProcessingJob.ship` 이 `ACCEPTED` 를 요구한다). 모델이 그 문장을 믿고 안내하면 담당자는
 * 시킨 대로 눌렀다가 도메인 거절을 본다.
 *
 * 서버가 사건 보고에 **그 순간의 실제 상태**를 붙여 준다(`AgentPendingWork`). 화면은 무슨
 * 일이 있었는지만 알리고, 다음에 할 일은 그쪽이 말하게 둔다 — 소재 결제 콜백도 같다.
 */
async function confirmAndDescribe(jobId: number, pgToken: string): Promise<string> {
  await processingRequestApi.confirmPayment(jobId, pgToken)
  return (
    `가공비 결제가 완료되었습니다 (작업 ${jobId}번). ` +
    '담당자에게 결제 내역과 지금 상태를 알려주고, 다음에 할 일을 안내하세요.'
  )
}

function notPaidMessage(jobId: number): string {
  return (
    `담당자가 결제창을 닫아 가공비가 결제되지 않았습니다 (작업 ${jobId}번). ` +
    '작업은 결제 기한까지 그대로 있으니 다시 결제할 수 있다고 알리고, ' +
    '계속 진행할지 담당자에게 물어보세요.'
  )
}
