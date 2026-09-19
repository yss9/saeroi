/** BRAND 대화형 에이전트의 계약. 백엔드 `agent` 패키지와 1:1 이다. */

/** 여정 단계. 이 값으로 모델에 노출되는 tool 이 정해진다. */
export type JourneyPhase =
  | 'INTAKE'
  | 'MATERIAL_SOURCING'
  | 'PROCESSING'
  | 'PRODUCTION'
  | 'PRODUCT'
  | 'DONE'

export type ConversationStatus = 'ACTIVE' | 'COMPLETED' | 'ABANDONED'

/** 화면에 그리는 메시지는 사용자 발화와 모델의 답뿐이다. tool 호출/결과는 서버가 걸러 보낸다. */
export type AgentMessageRole = 'USER' | 'ASSISTANT' | 'TOOL'

/**
 * 담당자가 직접 해야 하는 일의 종류.
 *
 * 서버가 대신 할 수 없는 것만 여기 온다 — 지갑 개인키는 브라우저에만 있고,
 * 결제는 사용자가 PG 화면에서 승인해야 한다.
 */
export type PendingActionType = 'WALLET_SIGN' | 'PAYMENT' | 'CONFIRM'

export type PendingActionStatus = 'PENDING' | 'COMPLETED' | 'REJECTED' | 'EXPIRED'

export interface AgentMessage {
  id: number
  role: AgentMessageRole
  content: string | null
  toolName: string | null
  createdAt: string
}

/**
 * 대화 말풍선 안에 뜨는 확인 카드.
 *
 * `payloadJson` 을 그대로 받는 이유는, 화면이 서명이나 결제를 만들려면 실제 인자가
 * 필요하고 담당자가 무엇에 동의하는지도 이 값에서 나오기 때문이다.
 *
 * `stepDone` 은 **표시용이다.** 진행 상태의 정본은 체인이라, 다단계 서명에서 다음에 할
 * 단계는 온체인 상태를 읽어 정한다.
 */
export interface PendingAction {
  id: number
  messageId: number
  actionType: PendingActionType
  toolName: string
  summary: string
  payloadJson: string
  /** 실행 결과. 결제 카드면 승인 뒤 여기에 결제창 주소가 들어온다. 승인 전에는 null. */
  resultJson: string | null
  status: PendingActionStatus
  stepTotal: number
  stepDone: number
  expiresAt: string
}

export interface ConversationDetail {
  id: number
  title: string
  phase: JourneyPhase
  status: ConversationStatus
  messages: AgentMessage[]
  actions: PendingAction[]
}

export interface ConversationSummary {
  id: number
  title: string
  phase: JourneyPhase
  status: ConversationStatus
  updatedAt: string
}

/** 결제 카드가 담고 있는 값. `purchase_material` 의 실행 결과다. */
export interface PurchasePayload {
  tradeId: number
  totalPrice: number
  quantityGram: number
  redirectUrl: string
}

/** 가공비 결제 카드가 담고 있는 값. 소재 구매와 같은 PaymentCard 가 쓴다. */
export interface ProcessingFeePayload {
  jobId: number
  fee: number
  redirectUrl: string
}

/** 서명 카드가 담고 있는 값. */
export interface ChainOrderPayload {
  tradeId: number
  summary: string
}

export const PHASE_LABEL: Record<JourneyPhase, string> = {
  INTAKE: '요구사항 파악',
  MATERIAL_SOURCING: '소재 확보',
  PROCESSING: '가공',
  PRODUCTION: '생산',
  PRODUCT: '제품 등록',
  DONE: '완료',
}
