import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { approvePayment } from '../../features/trade/api/paymentApi.ts'
import {
  useActiveConversation,
  useReportEvent,
} from '../../features/agent/hooks/useAgentConversation.ts'
import './agent.css'

/**
 * 카카오페이에서 대화창으로 돌아오는 지점.
 *
 * <p><b>거래 상세로 보내지 않는다.</b> 대화 안에서 결제까지 끝내는 것이 이 기능의 요점인데,
 * 기본 복귀 주소(`/trades/{id}/payment/return`)로 가면 대화창이 다른 화면으로 바뀌어
 * 담당자가 하던 이야기를 잃는다.
 *
 * <p>여기서 승인을 마치고, 그 사실을 대화에 알린 뒤 대화창으로 돌아간다. 모델이 현황을
 * 확인해 다음에 할 일을 안내한다.
 */
export default function AgentPaymentCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { data: conversation } = useActiveConversation()
  const reportEvent = useReportEvent()
  const [error, setError] = useState<string | null>(null)

  // 승인은 한 번만 보낸다. StrictMode 의 이중 실행이나 새로고침으로 두 번 들어가면
  // 서버가 거절하지만(CREATED 가 아닌 거래는 승인 불가), 굳이 보낼 이유가 없다.
  const sent = useRef(false)

  const tradeId = Number(params.get('tradeId'))
  const pgToken = params.get('pg_token')

  useEffect(() => {
    if (sent.current) return
    if (!Number.isInteger(tradeId) || tradeId <= 0 || !pgToken || !conversation) return
    sent.current = true

    void (async () => {
      try {
        await approvePayment(tradeId, pgToken)
        // 결제가 끝났다는 사실을 대화에 넣고 모델이 이어 말하게 한다.
        await reportEvent.mutateAsync({
          conversationId: conversation.id,
          message:
            `결제가 완료되었습니다 (거래 ${tradeId}번). ` +
            '담당자에게 결제 내역과 지금 상태를 알려주고, 다음에 할 일을 안내하세요.',
        })
      } catch {
        setError('결제 승인에 실패했습니다. 대화에서 상태를 확인해 주세요.')
        return
      }
      navigate('/agent', { replace: true })
    })()
  }, [conversation, navigate, pgToken, reportEvent, tradeId])

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
