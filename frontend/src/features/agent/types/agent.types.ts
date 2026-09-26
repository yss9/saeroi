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

export type PendingActionStatus =
  | 'PENDING'
  | 'COMPLETED'
  | 'REJECTED'
  | 'EXPIRED'
  /** 담당자가 화면에서 먼저 처리해 이 카드가 할 일이 없어졌다. */
  | 'STALE'

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
  /**
   * 이 대화에서 산 거래 번호. 대화 하나는 제품 하나라, 오른쪽 할 일 목록은 이 거래만 보인다 —
   * 계정의 모든 거래를 보이면 홈페이지에서 산 다른 소재의 "수령 확정" 이 이 제품의 할 일처럼 뜬다.
   */
  projectTradeIds: number[]
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
  /** 서버가 판매 글에서 읽어 카드에 실은 소재 이름 */
  materialName?: string
  /**
   * 누구에게서 무엇을 사는지 — 서버가 판매 글에서 읽어 싣는다. 판매 글이 한 곳뿐이자 모델이 판매자와 등급을
   * 말하지 않고 "폐데님 원단 10kg : 20,000원" 만 말했다. 카드에는 늘 있어야 담당자가 확인할 수 있다.
   */
  sellerName?: string
  grade?: string
  /** 폐소재 · 신소재 */
  originText?: string
  pricePerKg?: number
  /** 배송지. 모델이 대화로 받아 채운 값이라 담당자가 카드에서 확인해야 한다 */
  receiverName?: string
  receiverPhone?: string
  zipCode?: string
  address?: string
  addressDetail?: string
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

/** 여정 한 칸, 또는 현재 단계의 세부 한 칸. */
export interface JourneyMark {
  label: string
  done: boolean
  current: boolean
}

/**
 * 대화 위 경과 표시. **판정은 서버가 한다.**
 *
 * 화면이 계산하면 모델이 말하는 것과 막대가 어긋난다 — "아직 선별이 남았습니다" 라고
 * 말하는데 막대는 다 찬 식이다. 가공 계획은 애초에 화면이 읽을 수도 없다.
 */
/** 남은 일 한 줄. */
export interface JourneyTask {
  /** 무엇의 번호인지 — TRADE · PROCESSING · PRODUCTION · PRODUCT */
  domain: string
  referenceId: number
  /** 소재 이름, 가공 작업, 배치 이름, 제품 이름 */
  subject: string
  /** 지금 할 일, 또는 무엇을 기다리는지 */
  label: string
  /** 담당자가 할 일이면 true, 상대를 기다리는 중이면 false */
  actionable: boolean
}

/** 결제 한 건. */
export interface JourneySpend {
  /** MATERIAL(소재 구매) · PROCESSING(가공비) */
  kind: 'MATERIAL' | 'PROCESSING'
  /** 무엇에 — "폐데님 원단 10kg", "폐데님 원단 10kg 가공" */
  label: string
  /** 가공이면 공정과 업체 — "세척·해체·선별 · pro" */
  detail: string | null
  amount: number
}

export interface JourneyProgress {
  phase: string
  /** 여정 네 칸: 소재 · 가공 · 생산 · 판매 */
  journey: JourneyMark[]
  /** 지금 따라가는 대상. null 이면 막대를 그리지 않는다 */
  subject: string | null
  subjectDetail: string | null
  /** 현재 단계의 세부. 단계가 바뀌면 내용이 통째로 바뀐다 */
  steps: JourneyMark[]
  /** 담당자가 지금 할 일. 기다리는 중이면 그렇다고 적혀 있다 */
  nextTodo: string | null
  /**
   * 이 대화의 프로젝트에서 남은 일 전부. 오른쪽 패널이 그린다.
   *
   * 모델에게 가는 목록과 같은 판정에서 나온다 — 화면이 따로 판정하지 않는다.
   */
  tasks: JourneyTask[]
  /**
   * 이번 조회에서 **새 카드가 떴는가.**
   *
   * 대화는 주기 조회하지 않는다 — 담당자가 말을 걸 때만 바뀐다고 보았기 때문이다. 그런데
   * 공급업체나 가공업체가 움직이면 담당자 차례가 되고, 그때 서버가 카드를 대신 띄운다.
   * 이 값이 참일 때만 대화를 다시 읽는다 — 매번 읽으면 20초마다 대화 전체를 받아 온다.
   */
  conversationChanged: boolean
  /**
   * 이 대화의 프로젝트에서 결제한 금액 — 소재 구매와 가공비. 서버가 모은다.
   *
   * 예전에는 화면이 거래만 더해 가공비가 빠졌다. 가공은 작업 하나가 한 줄이다 — 한 업체에 공정을 여럿
   * 맡기면 공정이 한 줄에 함께 적히고, 업체가 다르면 따로 적힌다.
   */
  spending?: JourneySpend[]
}
