import type { ContractTransactionResponse } from 'ethers'
import { useEffect, useRef, useState } from 'react'
import { useWideLayout } from '../hooks/useWideLayout.ts'
import { pinnedToPanel } from './pinnedCards.ts'
import { timelineOf } from './agentTimeline.ts'
import { registerProcessingChainJob } from '../../processing/services/registerProcessingChainJob.ts'
import { AgentMessageBody } from './AgentMessageBody.tsx'
import { linkTradeOnChain } from '../../../services/blockchain/chainLinkApi.ts'
import { getListing } from '../../marketplace/api/getListing.ts'
import {
  confirmReceipt,
  createOrder,
  getOrder,
} from '../../marketplace/services/marketplaceContractService.ts'
import { processingJobApi } from '../../processing/api/processingJobApi.ts'
import { chainAlreadyPassed } from '../../processing/services/processingChainProgress.ts'
import {
  approveProcessingResultOnChain,
  PROCESSING_CHAIN_STATUS,
  getProcessingChainStatus,
  markProcessingShipped,
  processingEvidenceHash,
} from '../../processing/services/processingContractService.ts'
import { getTrade } from '../../trade/api/getTrade.ts'
import {
  useActiveConversation,
  useCompleteConversation,
  useSendMessage,
} from '../hooks/useAgentConversation.ts'
import {
  PHASE_LABEL,
  type PendingAction,
} from '../types/agent.types.ts'
import { ConfirmCard } from './ConfirmCard.tsx'
import { PaymentCard } from './PaymentCard.tsx'
import { AgentTaskPanel } from './AgentTaskPanel.tsx'
import { JourneyProgressBar } from './JourneyProgressBar.tsx'
import { ProductionChainCard } from './ProductionChainCard.tsx'
import { WalletSignCard } from './WalletSignCard.tsx'
import { sendTrackedOperation } from '../../../services/blockchain/operationApi.ts'

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
  // 두 단으로 그려지는 폭인가. 떠 있는 카드를 어디에 그릴지가 여기 달렸다.
  const wide = useWideLayout()
  const openCards = (conversation?.actions ?? []).filter((action) => action.status === 'PENDING')

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
          도메인 API 만 읽으므로 크레딧이 들지 않는다.

          단계마다 보여줄 것이 다르다 — 소재는 거래 하나, 가공은 공정 목록, 생산은 배치,
          판매는 제품이다. 그 판정은 서버가 하고 여기서는 그리기만 한다. */}
      <JourneyProgressBar conversationId={conversation?.id ?? null} />

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

        {timelineOf(conversation).map((entry) =>
          entry.kind === 'message' ? (
            <article
              key={`m${entry.message.id}`}
              className={`agent-message agent-message--${entry.message.role.toLowerCase()}`}
            >
              {/*
                담당자가 친 말은 **그대로** 보여 준다. 마크다운으로 해석하면 별표나 밑줄이
                든 문장이 담당자가 쓴 것과 다르게 보인다 — 자기가 뭐라고 썼는지는 화면에
                적힌 대로여야 한다.
              */}
              {entry.message.role === 'USER' ? (
                <p>{entry.message.content}</p>
              ) : (
                <AgentMessageBody content={entry.message.content} />
              )}
            </article>
          ) : pinnedToPanel(entry.action, wide) ? (
            // 넓은 화면에서는 이 카드를 오른쪽 패널이 그린다. 여기에는 무엇을 기다리는지만
            // 한 줄 남긴다 — 양쪽에 그리면 버튼이 둘이 되고, 서명 카드는 내부 상태를 들고
            // 있어서 어느 쪽을 눌렀는지에 따라 다르게 굴러간다.
            <WaitingHere key={`a${entry.action.id}`} action={entry.action} />
          ) : (
            <ActionCard key={`a${entry.action.id}`} action={entry.action} />
          ),
        )}

        {/*
          첫 발화라 아직 대화가 없을 때. 낙관적 갱신은 기존 대화에만 붙일 수 있어서,
          이 경우에만 보낸 말을 직접 그린다 — 그러지 않으면 20~30초 동안 빈 화면이다.
        */}
        {!conversation && send.isPending && send.variables && (
          <article className="agent-message agent-message--user">
            <p>{send.variables.message}</p>
          </article>
        )}

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

        <AgentTaskPanel
          conversation={conversation ?? null}
          // 카드를 패널로 옮기지 않고 **그리는 함수만** 넘긴다. 서명 방법 표(SIGNERS)와
          // 그것이 부르는 체인 호출이 전부 이 파일에 있어서, 패널이 직접 그리게 하면
          // 두 파일이 서로를 import 하게 된다.
          pinnedCards={wide ? openCards.filter((action) => pinnedToPanel(action, wide)) : []}
          renderCard={(action) => <ActionCard action={action} />}
        />
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
/** 카드가 오른쪽에 있을 때 대화에 남는 한 줄. 무엇을 기다리는 중인지는 보여야 한다. */
function WaitingHere({ action }: { action: PendingAction }) {
  return (
    <p className="agent-waiting">
      <span className="agent-waiting__mark" aria-hidden="true">
        →
      </span>
      {action.summary}
      <span className="agent-waiting__where">오른쪽에서 확인해 주세요</span>
    </p>
  )
}

function ActionCard({ action }: { action: PendingAction }) {
  // **처리된 카드는 한 줄로 접는다.**
  //
  // 승인·거절이 끝난 카드가 전체 크기로 대화 끝에 쌓이면, 지금 눌러야 할 카드가 그 사이에
  // 묻힌다. 여정이 길어질수록 심해진다 — 결제·서명·확인이 건마다 하나씩 남는다.
  //
  // 지우지는 않는다. 무엇에 동의했는지가 대화 기록이고, 분쟁 때 근거가 된다.
  if (action.status !== 'PENDING') {
    return <ProcessedAction action={action} />
  }

  if (action.actionType === 'PAYMENT') {
    return <PaymentCard action={action} />
  }
  if (action.actionType === 'CONFIRM') {
    return <ConfirmCard action={action} />
  }
  if (action.actionType === 'WALLET_SIGN') {
    // 생산 온체인 기록만 카드가 따로다. 네 번의 서명을 컨트랙트가 순서대로 강제하고 다음
    // 단계는 체인 상태로 정해야 해서, 단발 서명용 WalletSignCard 의 모양에 맞지 않는다.
    // 생산 화면의 패널을 그대로 쓰므로 서명 구현이 두 벌이 되지 않는다.
    if (action.toolName === 'record_production_chain') {
      return <ProductionChainCard action={action} />
    }

    // 나머지는 tool 이름으로 서명 방법을 고른다. 타입만 보고 하나로 몰면 수령 확정 카드를
    // 눌렀을 때 주문 생성에 서명하게 되는 식으로 엉뚱한 트랜잭션이 나간다.
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

/** 처리가 끝난 카드. 한 줄만 차지한다. */
function ProcessedAction({ action }: { action: PendingAction }) {
  return (
    <p className={`agent-done agent-done--${action.status.toLowerCase()}`}>
      <span className="agent-done__mark" aria-hidden="true">
        {action.status === 'COMPLETED' ? '✓' : '·'}
      </span>
      <span className="agent-done__label">{PROCESSED_LABEL[action.actionType]}</span>
      <span className="agent-done__summary">{action.summary}</span>
      <span className="agent-done__status">{PROCESSED_STATUS[action.status]}</span>
    </p>
  )
}

const PROCESSED_LABEL: Record<string, string> = {
  PAYMENT: '결제',
  WALLET_SIGN: '서명',
  CONFIRM: '확인',
}

const PROCESSED_STATUS: Record<string, string> = {
  COMPLETED: '완료',
  REJECTED: '취소',
  EXPIRED: '기한 지남',
  STALE: '이미 처리됨',
}

/**
 * 서명 tool 이름 → 실제 서명 함수.
 *
 * 새 서명 tool 을 백엔드에 추가하면 여기에도 한 줄을 더해야 한다. 없으면 화면이 그 사실을
 * 드러낸다 — 조용히 아무 일도 안 일어나는 것보다 낫다.
 */
const SIGNERS: Record<string, (payload: SignPayload) => Promise<string>> = {
  request_chain_order: (payload) => signTradeOrder(requireId(payload, 'tradeId')),
  register_processing_chain_job: (payload) => registerProcessingChainJob(requireId(payload, 'jobId')),
  request_custody_transfer: (payload) =>
    signCustodyTransfer(requireId(payload, 'jobId'), payload.carrier, payload.trackingNumber),
  approve_processing_result: (payload) => signProcessingApproval(requireId(payload, 'jobId')),
  // 수령은 카드 하나다 — 서명하면 그 자리에서 수령까지 확정된다. 예전에는 서명 카드와
  // 확인 카드가 따로 떠서 두 번 눌러야 했다.
  confirm_receipt: (payload) => signTradeReceipt(requireId(payload, 'tradeId')),
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

  // **여기서 작업을 만들지 않는다.** 예전에는 chainJobId 가 없으면 createProcessingJob 을
  // 부르고 곧바로 markShipped 를 시도했는데, 그건 구조적으로 성공할 수 없다 — 방금 만든
  // 작업은 REQUESTED 이고, markShipped 는 ACCEPTED 일 때만 받는다. 그 사이에 업체의
  // 온체인 수락이 들어가야 하는데 그럴 틈이 없다.
  //
  // 실제로 `execution reverted: "Processing: invalid status"` 라는 원시 문자열이 담당자
  // 화면에 그대로 떴고, 두 번 눌러도 같은 결과였다. 등록은 register_processing_chain_job
  // 이 맡는다 — 가공 화면이 "온체인 작업 등록" 버튼을 따로 둔 것과 같은 모양이다.
  if (preparation.chainJobId == null) {
    throw new Error(
      '이 작업은 아직 블록체인에 등록되지 않았습니다. 먼저 온체인 작업 등록을 끝내고, ' +
        '업체가 체인에서 수락한 뒤에 인계할 수 있습니다.',
    )
  }
  const chainJobId = preparation.chainJobId

  const chainStatus = await getProcessingChainStatus(chainJobId).catch(() => null)
  if (chainStatus === PROCESSING_CHAIN_STATUS.REQUESTED) {
    throw new Error('가공업체가 아직 블록체인에서 작업을 수락하지 않았습니다. 수락 후 다시 시도해 주세요.')
  }

  // **이미 서명이 남았으면 건너뛴다.** 서명이 체인에 들어간 뒤 서버 호출이 실패하는 창이
  // 있다(네트워크 끊김, 토큰 만료). 그때 다시 시도하면 markShipped 가 revert 하고 — 체인이
  // 이미 SHIPPED 라 require(ACCEPTED)를 어긴다 — 그 뒤의 서버 호출까지 못 가서 DB 가
  // 영원히 따라잡지 못한다. 그래서 재시도는 서버만 채운다.
  let shipTxHash: string | null = null
  if (!(await chainAlreadyPassed(chainJobId, 'SHIPPED'))) {
    const shipReceipt = await confirmedTransaction(
      sendTrackedOperation(
        { contract: 'PROCESSING_REGISTRY', functionName: 'markShipped', referenceType: 'PROCESSING', referenceId: jobId },
        (operationId) => markProcessingShipped(
          chainJobId,
          processingEvidenceHash({
          type: 'PROCESSING_OUTBOUND_SHIPMENT',
          jobId,
          carrier: carrier ?? '',
          trackingNumber: trackingNumber ?? '',
          }),
          operationId,
        ),
      ),
    )
    shipTxHash = shipReceipt.hash
  }

  await processingJobApi.ship(jobId, { carrier, trackingNumber })
  // 건너뛴 경우에도 해시를 돌려줘야 카드가 완료로 닫힌다. 서버는 체인을 직접 읽어 판정한다.
  return shipTxHash ?? 'already-signed'
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

  // **이미 승인 서명이 남았으면 다시 서명하지 않는다.** 서명 뒤 서버 호출이 실패했던 경우다 — 다시
  // 서명하면 체인이 이미 승인된 작업이라 되돌린다. 해시 없이 보내면 서버가 체인에서 그 거래를 찾아
  // 검증한 뒤 새 소재를 발행한다.
  if (await chainAlreadyPassed(preparation.chainJobId, 'CONFIRMED')) {
    await processingJobApi.receiveReturn(jobId, {})
    return 'already-signed'
  }

  const receipt = await confirmedTransaction(sendTrackedOperation(
    { contract: 'PROCESSING_REGISTRY', functionName: 'approveProcessingResult', referenceType: 'PROCESSING', referenceId: jobId },
    (operationId) => approveProcessingResultOnChain(preparation.chainJobId!, operationId),
  ))
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

  const receipt = await confirmedTransaction(sendTrackedOperation(
    { contract: 'MARKETPLACE', functionName: 'confirmReceipt', referenceType: 'TRADE', referenceId: tradeId },
    (operationId) => confirmReceipt(trade.chainTradeId!, operationId),
  ))

  // **서버에 따로 알리지 않는다.** 다음 단계인 confirm_receipt 가 체인을 읽어 수령이
  // 남았는지 확인하고, 그때 서명 시각까지 채운다. 여기서 한 번 더 보고하면 같은 일을
  // 두 곳에서 하게 되고, 어긋나는 순간 어느 쪽이 정본인지 알 수 없다.
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
    throw new Error('이력 저장이 완료되지 않았습니다.')
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

  const response = await sendTrackedOperation(
    { contract: 'MARKETPLACE', functionName: 'createOrder', referenceType: 'TRADE', referenceId: tradeId },
    (operationId) => createOrder(listing.chainListingId!, trade.quantity, operationId),
  )
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
