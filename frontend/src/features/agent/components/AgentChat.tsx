import type { ContractTransactionResponse } from 'ethers'
import { useEffect, useRef, useState } from 'react'
import { linkTradeOnChain } from '../../../services/blockchain/chainLinkApi.ts'
import { getListing } from '../../marketplace/api/getListing.ts'
import {
  confirmReceipt,
  createOrder,
  getOrder,
} from '../../marketplace/services/marketplaceContractService.ts'
import { processingJobApi } from '../../processing/api/processingJobApi.ts'
import {
  approveProcessingResultOnChain,
  createProcessingJob,
  getProcessingBrandLockId,
  readProcessingEventValues,
} from '../../processing/services/processingContractService.ts'
import { getTrade } from '../../trade/api/getTrade.ts'
import {
  useActiveConversation,
  useCompleteConversation,
  useSendMessage,
} from '../hooks/useAgentConversation.ts'
import { PHASE_LABEL, type PendingAction } from '../types/agent.types.ts'
import { ConfirmCard } from './ConfirmCard.tsx'
import { PaymentCard } from './PaymentCard.tsx'
import { AgentTaskPanel } from './AgentTaskPanel.tsx'
import { TradeProgressBar } from './TradeProgressBar.tsx'
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
  const inputRef = useRef<HTMLInputElement>(null)

  // 새 메시지·카드가 생기거나 "답장 중" 이 켜질 때마다 아래로 붙인다.
  //
  // `send.isPending` 을 의존성에 넣는 것이 중요하다 — 담당자가 말을 보낸 직후에는 아직
  // 서버 응답이 없어 메시지 수가 그대로인데, 그때 "답장 중" 줄이 생기면서 높이가 늘어난다.
  // 이 값이 없으면 바로 그 순간에만 스크롤이 따라가지 않는다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [conversation?.messages.length, conversation?.actions.length, send.isPending])

  /**
   * 답장이 오면 입력창에 커서를 되돌린다.
   *
   * <b>전송 중에는 입력창을 disabled 로 두는데, 비활성화되는 순간 브라우저가 포커스를
   * 거둬 가고 다시 활성화될 때 돌려주지 않는다.</b> 그래서 한 마디 할 때마다 입력창을
   * 다시 클릭해야 했다. 한 턴이 20~30초라 그동안 담당자는 다른 창을 보고 있다가
   * 돌아오는데, 그때 바로 이어 칠 수 있어야 한다.
   *
   * 대화가 끝난 뒤에는 되돌리지 않는다 — 입력창이 계속 disabled 라 의미가 없고,
   * 닫힌 대화에 커서가 깜빡이면 아직 쓸 수 있다는 뜻으로 읽힌다.
   */
  useEffect(() => {
    if (send.isPending || conversation?.status === 'COMPLETED') return
    inputRef.current?.focus()
  }, [send.isPending, conversation?.status])

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

  // 이번 대화가 언제 시작됐는지. 진행 막대와 절차 패널이 이 시점 이후의 거래만 센다.
  // 대화는 항상 담당자의 첫 발화로 시작하므로 그 시각이 곧 여정의 시작이다.
  const startedAt = conversation?.messages[0]?.createdAt ?? null

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
      <TradeProgressBar startedAt={startedAt} />

      {/*
        왼쪽은 대화, 오른쪽은 절차. 절차를 대화 위에 쌓으면 매번 지나쳐야 하고, 대화가
        길어질수록 담당자는 스크롤을 올려 상태를 찾게 된다 — 그 이동을 없애려고 옆에 둔다.
      */}
      <div className="agent-chat__body">
        <div className="agent-chat__main">
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
          ref={inputRef}
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
        </div>

        <AgentTaskPanel conversation={conversation ?? null} />
      </div>
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
const SIGNERS: Record<string, (payload: SignPayload) => Promise<string>> = {
  request_chain_order: (payload) => signTradeOrder(requireId(payload, 'tradeId')),
  request_custody_transfer: (payload) =>
    signCustodyTransfer(requireId(payload, 'jobId'), payload.carrier, payload.trackingNumber),
  approve_processing_result: (payload) => signProcessingApproval(requireId(payload, 'jobId')),
  request_chain_receipt: (payload) => signTradeReceipt(requireId(payload, 'tradeId')),
}

/** 서명 카드가 넘겨주는 값. 모델이 만든 인자라 무엇이 들었는지 보장되지 않는다. */
type SignPayload = {
  tradeId?: number
  jobId?: number
  carrier?: string
  trackingNumber?: string
}

/**
 * 서명에 꼭 필요한 id 를 꺼낸다.
 *
 * 없으면 여기서 멈춘다. 없는 값을 0 이나 NaN 으로 흘려보내면 엉뚱한 건에 서명하거나
 * 컨트랙트가 알 수 없는 이유로 revert 한다.
 */
function requireId(payload: SignPayload, field: 'tradeId' | 'jobId'): number {
  const value = payload[field]
  if (typeof value !== 'number' || !Number.isInteger(value) || value <= 0) {
    throw new Error(`서명에 필요한 ${field} 를 찾지 못했습니다. 대화에서 다시 요청해 주세요.`)
  }
  return value
}

/**
 * 가공 작업을 체인에 올리고 소재를 업체로 보낸다.
 *
 * **발송은 온체인 등록 뒤에만 된다.** 서버가 그렇게 막고 있다 — 물건이 먼저 떠나면 체인에
 * 아무 기록도 없는 채로 소재가 남의 손에 가 있는 구간이 생긴다. 그래서 서명 한 번에
 * 등록과 발송이 이어 붙는다.
 *
 * **이미 등록된 작업이면 등록을 건너뛴다.** 등록은 됐는데 발송 보고가 실패한 상태에서 다시
 * 누르면, 건너뛰지 않는 한 컨트랙트가 같은 작업을 두 번 만들려다 revert 한다. 다음에 할
 * 일을 `stepDone` 이 아니라 **온체인 상태로 정한다**는 규칙이 여기 적용된다.
 */
async function signCustodyTransfer(
  jobId: number,
  carrier?: string,
  trackingNumber?: string,
): Promise<string> {
  const preparation = await processingJobApi.chainPreparation(jobId)

  let txHash: string
  if (preparation.chainJobId == null) {
    const receipt = await confirmedTransaction(
      createProcessingJob(
        preparation.processorWallet,
        preparation.inputTokenId,
        preparation.inputQuantity,
        preparation.processTypes,
      ),
    )
    const values = readProcessingEventValues(receipt, 'ProcessingJobCreated', 'jobId')
    if (values.length !== 1) throw new Error('온체인 작업 번호를 확인하지 못했습니다.')
    const onChainId = Number(values[0])
    const chainLockId = await getProcessingBrandLockId(onChainId)
    await processingJobApi.linkChain(jobId, {
      onChainId,
      chainLockId,
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber,
    })
    txHash = receipt.hash
  } else {
    // 등록은 이미 끝났다. 발송만 남은 상태로 다시 들어온 것이다.
    txHash = `linked:${preparation.chainJobId}`
  }

  await processingJobApi.ship(jobId, { carrier, trackingNumber })
  return txHash
}

/**
 * 가공 결과를 승인한다. **여기서 새 소재가 생긴다.**
 *
 * 온체인 승인에 서명하고 그 영수증을 기존 API 로 붙인다 — 화면에서 승인하는 것과 같은
 * 경로다. 서버가 영수증을 RPC 로 확인한 뒤에야 소재를 발행한다.
 */
async function signProcessingApproval(jobId: number): Promise<string> {
  const preparation = await processingJobApi.chainPreparation(jobId)
  if (preparation.chainJobId == null) {
    throw new Error('이 작업은 아직 온체인에 등록되지 않아 승인할 수 없습니다.')
  }

  const receipt = await confirmedTransaction(approveProcessingResultOnChain(preparation.chainJobId))
  await processingJobApi.receiveReturn(jobId, { txHash: receipt.hash })
  return receipt.hash
}

/**
 * 수령을 체인에 기록한다. **소유권이 실제로 옮겨가는 자리다.**
 *
 * 컨트랙트가 순서를 강제한다 — 주문이 체인에서 `SHIPPED` 여야 받는다. 판매자가 발송
 * 서명을 하지 않았으면 revert 하는데, 그 메시지만으로는 담당자가 무엇을 기다려야 하는지
 * 알 수 없다. 그래서 서명 전에 체인 상태를 읽어 이유를 말해 준다.
 *
 * 이미 수령이 기록된 주문이면 실패가 아니라 **앞선 시도가 성공한 것**이다. 다시 서명하면
 * revert 하므로 그 사실만 돌려주고 카드를 닫는다.
 */
async function signTradeReceipt(tradeId: number): Promise<string> {
  const trade = await getTrade(tradeId)
  if (trade.chainTradeId == null) {
    throw new Error('이 거래는 블록체인에 기록되지 않아 체인에 남길 수령 기록이 없습니다.')
  }

  // `getOrder` 가 숫자를 문자열로 디코딩해 준다. 직접 비교하면 컨트랙트 enum 의 0번이
  // NONE 인 것을 잊고 한 칸 밀린 판정을 하게 된다 — 실제로 그렇게 헷갈렸다.
  const order = await getOrder(trade.chainTradeId)
  if (order.status === 'RECEIVED' || order.status === 'COMPLETED') {
    // 이미 옮겨졌다. 다시 서명하면 revert 하므로 실패가 아니라 "이미 됐다" 로 닫는다.
    // 카드가 이 코드를 보고 완료 처리한다 — BLOCKCHAIN_ALREADY_LINKED 와 같은 관용구다.
    throw Object.assign(new Error('이 거래의 수령은 이미 블록체인에 기록돼 있습니다.'), {
      code: 'CHAIN_ALREADY_DONE',
    })
  }
  if (order.status !== 'SHIPPED') {
    throw new Error(
      '판매자가 아직 발송을 블록체인에 기록하지 않아 수령을 서명할 수 없습니다. ' +
        '컨트랙트가 발송 뒤에만 수령을 받습니다 — 판매자의 발송 서명이 끝난 뒤 다시 시도해 주세요.',
    )
  }

  const receipt = await confirmedTransaction(confirmReceipt(trade.chainTradeId))
  return receipt.hash
}

/**
 * 트랜잭션이 블록에 실릴 때까지 기다린다.
 *
 * 기다리지 않고 다음 API 를 부르면 서버가 영수증을 못 찾아 거절한다. 담당자는 서명까지
 * 마쳤는데 실패 메시지를 보게 된다.
 */
async function confirmedTransaction(response: Promise<ContractTransactionResponse>) {
  const transaction = await response
  const receipt = await transaction.wait()
  if (!receipt || receipt.status !== 1) {
    throw new Error('블록체인 트랜잭션이 확정되지 않았습니다.')
  }
  return receipt
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
async function signTradeOrder(tradeId: number): Promise<string> {
  const trade = await getTrade(tradeId)
  const listing = await getListing(trade.listingId)

  if (listing.chainListingId == null) {
    // 판매자가 온체인 등록을 하지 않은 판매글이다. 구매자 혼자서는 시작할 수 없다.
    throw new Error('이 판매 글은 아직 온체인에 등록되지 않아 블록체인 기록을 만들 수 없습니다.')
  }

  const response = await createOrder(listing.chainListingId, trade.quantity)
  const receipt = await response.wait()

  // 기존 chain-link API 로 서버에 붙인다. 영수증 검증은 그쪽이 한다.
  await linkTradeOnChain(tradeId, {
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
