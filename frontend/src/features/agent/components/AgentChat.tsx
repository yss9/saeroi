import { useEffect, useRef, useState } from 'react'
import { linkTradeOnChain } from '../../../services/blockchain/chainLinkApi.ts'
import { getListing } from '../../marketplace/api/getListing.ts'
import { createOrder } from '../../marketplace/services/marketplaceContractService.ts'
import { getTrade } from '../../trade/api/getTrade.ts'
import {
  useActiveConversation,
  useCompleteConversation,
  useSendMessage,
} from '../hooks/useAgentConversation.ts'
import { PHASE_LABEL, type PendingAction } from '../types/agent.types.ts'
import { ConfirmCard } from './ConfirmCard.tsx'
import { PaymentCard } from './PaymentCard.tsx'
import { TradeStatusStrip } from './TradeStatusStrip.tsx'
import { WalletSignCard } from './WalletSignCard.tsx'

/**
 * 대화창 본체.
 *
 * 담당자당 활성 대화가 하나뿐이라 대화 목록이 없다. 창을 열면 진행 중인 대화가 이어지고,
 * 없으면 첫 발화로 시작한다.
 */
export function AgentChat() {
  const { data: conversation, isPending } = useActiveConversation()
  const send = useSendMessage()
  const complete = useCompleteConversation()
  const [draft, setDraft] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  // 새 메시지·카드가 생기거나 "답장 중" 이 켜질 때마다 아래로 붙인다.
  //
  // `send.isPending` 을 의존성에 넣는 것이 중요하다 — 담당자가 말을 보낸 직후에는 아직
  // 서버 응답이 없어 메시지 수가 그대로인데, 그때 "답장 중" 줄이 생기면서 높이가 늘어난다.
  // 이 값이 없으면 바로 그 순간에만 스크롤이 따라가지 않는다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [conversation?.messages.length, conversation?.actions.length, send.isPending])

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const message = draft.trim()
    if (!message || send.isPending) return
    setDraft('')
    send.mutate({ conversationId: conversation?.id ?? null, message })
  }

  if (isPending) {
    return <p className="agent-chat__loading">대화를 불러오는 중…</p>
  }

  return (
    <section className="agent-chat">
      <header className="agent-chat__header">
        <div>
          <h1>AI 어시스턴트</h1>
          {conversation && (
            <p className="agent-chat__phase">{PHASE_LABEL[conversation.phase]}</p>
          )}
        </div>
        {conversation && conversation.status === 'ACTIVE' && (
          <button
            type="button"
            className="agent-chat__complete"
            onClick={() => complete.mutate(conversation.id)}
            disabled={complete.isPending}
          >
            완료
          </button>
        )}
      </header>

      {/* 상대방이 움직여야 넘어가는 구간에서, 대화를 걸지 않아도 상태가 보이게 한다.
          도메인 API 만 읽으므로 크레딧이 들지 않는다. */}
      <TradeStatusStrip />

      <div className="agent-chat__log">
        {!conversation && (
          <p className="agent-chat__empty">
            어떤 제품을 만들고 싶으신가요? 필요한 소재를 함께 찾아드리겠습니다.
          </p>
        )}

        {conversation?.messages.map((message) => (
          <article
            key={message.id}
            className={`agent-message agent-message--${message.role.toLowerCase()}`}
          >
            <p>{message.content}</p>
          </article>
        ))}

        {/*
          첫 발화라 아직 대화가 없을 때. 낙관적 갱신은 기존 대화에만 붙일 수 있어서,
          이 경우에만 보낸 말을 직접 그린다 — 그러지 않으면 20~30초 동안 빈 화면이다.
        */}
        {!conversation && send.isPending && send.variables && (
          <article className="agent-message agent-message--user">
            <p>{send.variables.message}</p>
          </article>
        )}

        {/*
          카드는 말풍선에 붙이지 않고 로그 끝에 모아 그린다.

          카드의 `messageId` 는 **tool 호출 메시지**를 가리키는데 그 줄은 화면에서 걸러진다
          (사용자가 읽을 것은 모델의 말과 카드이지 호출 로그가 아니다). 그래서 말풍선에
          붙이려 하면 붙을 자리가 없어 카드가 아예 보이지 않는다.
        */}
        {conversation?.actions.map((action) => (
          <ActionCard key={action.id} action={action} />
        ))}

        {send.isPending && (
          <p className="agent-chat__thinking" aria-live="polite">
            <span className="agent-chat__dots">
              <i />
              <i />
              <i />
            </span>
            답장을 쓰는 중입니다
          </p>
        )}
        {send.isError && (
          <p className="agent-chat__error">
            응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.
          </p>
        )}

        <div ref={bottomRef} />
      </div>

      <form className="agent-chat__input" onSubmit={handleSubmit}>
        <input
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="무엇을 만들고 싶으신가요?"
          disabled={send.isPending || conversation?.status === 'COMPLETED'}
          maxLength={4000}
        />
        <button type="submit" disabled={send.isPending || !draft.trim()}>
          보내기
        </button>
      </form>
    </section>
  )
}

/**
 * 카드 종류별 분기.
 *
 * 서명 카드에 실제 서명 함수를 주입한다. 컴포넌트가 `new ethers.Contract` 를 직접 만들지
 * 않고 기존 `marketplaceContractService` 를 거치는 것이 이 저장소의 규칙이다.
 */
function ActionCard({ action }: { action: PendingAction }) {
  if (action.actionType === 'PAYMENT') {
    return <PaymentCard action={action} />
  }
  if (action.actionType === 'CONFIRM') {
    return <ConfirmCard action={action} />
  }
  if (action.actionType === 'WALLET_SIGN') {
    // tool 이름으로 서명 방법을 고른다. 타입만 보고 하나로 몰면 수령 확정 카드를 눌렀을 때
    // 주문 생성에 서명하게 되는 식으로 엉뚱한 트랜잭션이 나간다.
    const signer = SIGNERS[action.toolName]
    if (!signer) {
      return (
        <p className="agent-card agent-card--sign agent-card__error">
          이 서명은 아직 화면에 연결되지 않았습니다: {action.toolName}
        </p>
      )
    }
    return <WalletSignCard action={action} onSign={signer} />
  }
  return null
}

/**
 * 서명 tool 이름 → 실제 서명 함수.
 *
 * 새 서명 tool 을 백엔드에 추가하면 여기에도 한 줄을 더해야 한다. 없으면 화면이 그 사실을
 * 드러낸다 — 조용히 아무 일도 안 일어나는 것보다 낫다.
 */
const SIGNERS: Record<string, (payload: { tradeId: number }) => Promise<string>> = {
  request_chain_order: signTradeOrder,
}

/**
 * 거래를 온체인 주문으로 만든다.
 *
 * <b>서명에 필요한 값을 모델에게서 받지 않고 서버에서 다시 읽는다.</b> 모델이 준 것은
 * `tradeId` 뿐이고, 온체인 listingId 와 수량은 거래·판매글을 조회해 얻는다. 모델이 숫자를
 * 지어내도 엉뚱한 주문이 체인에 올라가지 않는다.
 *
 * 컨트랙트 호출과 서버 연결 모두 기존 경로를 그대로 쓴다 — 화면에서 사는 것과 대화로 사는
 * 것이 같은 코드를 지나야 결과가 갈라지지 않는다.
 */
async function signTradeOrder(payload: { tradeId: number }): Promise<string> {
  const trade = await getTrade(payload.tradeId)
  const listing = await getListing(trade.listingId)

  if (listing.chainListingId == null) {
    // 판매자가 온체인 등록을 하지 않은 판매글이다. 구매자 혼자서는 시작할 수 없다.
    throw new Error('이 판매 글은 아직 온체인에 등록되지 않아 블록체인 기록을 만들 수 없습니다.')
  }

  const response = await createOrder(listing.chainListingId, trade.quantity)
  const receipt = await response.wait()

  // 기존 chain-link API 로 서버에 붙인다. 영수증 검증은 그쪽이 한다.
  await linkTradeOnChain(payload.tradeId, {
    onChainId: readOrderId(receipt),
    txHash: response.hash,
  })
  return response.hash
}

/**
 * 영수증에서 온체인 주문 번호를 꺼낸다.
 *
 * `useTradeOnChain` 은 `useOnChainLink` 로 `OrderCreated` 이벤트를 읽는다. 여기서도 같은
 * 이벤트를 봐야 하므로, 연결 전에 그 헬퍼를 재사용하도록 다듬을 여지가 있다.
 */
function readOrderId(receipt: unknown): number {
  const logs = (receipt as { logs?: { topics?: string[]; data?: string }[] })?.logs ?? []
  for (const log of logs) {
    const raw = log.topics?.[1]
    if (raw) return Number(BigInt(raw))
  }
  throw new Error('온체인 주문 번호를 찾지 못했습니다.')
}
