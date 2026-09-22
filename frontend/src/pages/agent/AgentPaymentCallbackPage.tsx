import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { approvePayment, cancelTrade } from '../../features/trade/api/paymentApi.ts'
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
 *
 * <p><b>결제창에서 그냥 닫고 나온 경우도 여기로 온다.</b> 그때는 `pg_token` 이 없다. 거래를
 * 취소해 두지 않으면 CREATED 로 남아 판매 글의 수량을 계속 묶는다 — 담당자는 결제를 안 한
 * 것뿐인데 그 소재의 잔량이 줄어 있고, 이유는 어디에도 보이지 않는다. 일반 결제 복귀 화면
 * (`PaymentReturnPage`)이 같은 이유로 같은 처리를 한다.
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
    if (!Number.isInteger(tradeId) || tradeId <= 0 || !conversation) return
    sent.current = true

    void (async () => {
      try {
        // pg_token 이 없으면 결제창에서 닫고 나온 것이다. 승인할 것이 없고, 대신 묶여 있는
        // 수량을 풀어야 한다.
        const message = pgToken
          ? await approveAndDescribe(tradeId, pgToken)
          : await cancelAndDescribe(tradeId)

        // 무슨 일이 있었는지 대화에 넣고 모델이 이어 말하게 한다.
        await reportEvent.mutateAsync({ conversationId: conversation.id, message })
      } catch {
        setError(
          pgToken
            ? '결제 승인에 실패했습니다. 대화에서 상태를 확인해 주세요.'
            : '거래를 정리하지 못했습니다. 대화에서 상태를 확인해 주세요.',
        )
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

/** 결제를 승인하고, 대화에 넣을 문장을 만든다. */
/**
 * 이 문장은 **담당자가 읽는다.** 대화에 어시스턴트 말풍선으로 그대로 남는다.
 *
 * 그래서 모델에게 주는 지시("담당자에게 알려주고 안내하세요")나 도구 이름을 넣지 않는다.
 * 예전에는 넣었는데 두 가지가 망가졌다 — 담당자가 그 지시문을 읽었고, 모델이 그것을 자기
 * 말투로 배워 다음 턴에 통째로 베꼈다. 지금 무엇을 해야 하는지는 서버가 매 턴 시스템
 * 프롬프트로 넣는다(`AgentTurnStore.loadPrompt`).
 *
 * 문장을 끝맺는 것도 같은 이유다. 어중간하게 끊으면 모델이 이어 써서 한 문장이 두
 * 말풍선으로 쪼개진다.
 */
async function approveAndDescribe(tradeId: number, pgToken: string): Promise<string> {
  await approvePayment(tradeId, pgToken)
  return `결제가 완료되었습니다. (거래 ${tradeId}번)`
}

/**
 * 결제창을 닫고 온 경우. 거래를 취소해 판매 수량을 되돌린다.
 *
 * 이미 취소된 거래면 서버가 거절하는데, 그건 목적이 이미 이뤄진 것이라 실패로 다루지
 * 않는다. 여기서 오류를 띄우면 담당자는 결제를 안 했을 뿐인데 무엇이 잘못됐다고 읽는다.
 */
async function cancelAndDescribe(tradeId: number): Promise<string> {
  try {
    await cancelTrade(tradeId, '결제창에서 취소')
  } catch {
    return `결제가 진행되지 않았습니다. (거래 ${tradeId}번) 이 거래가 아직 남아 있을 수 있습니다.`
  }
  return `결제창을 닫아 거래 ${tradeId}번이 취소되었습니다. 판매 글의 수량은 되돌렸습니다.`
}
