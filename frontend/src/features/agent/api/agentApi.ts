import { apiClient } from '../../../services/apiClient.ts'
import type { ConversationDetail, ConversationSummary } from '../types/agent.types.ts'

/**
 * 한 턴에 LLM 이 여러 번 불리므로 기본 10초로는 턱없이 모자란다.
 * tool 을 4번까지 부를 수 있어 최악의 경우 5회 생성이 이어진다.
 */
const TURN_TIMEOUT_MS = 120_000

/** 진행 중인 대화. 없으면 null — 화면이 "무엇을 만드시겠어요?" 를 그린다. */
export async function getActiveConversation(): Promise<ConversationDetail | null> {
  const { data } = await apiClient.get<ConversationDetail | null>('/v1/agent/conversations/active')
  return data ?? null
}

export async function getConversation(conversationId: number): Promise<ConversationDetail> {
  const { data } = await apiClient.get<ConversationDetail>(
    `/v1/agent/conversations/${conversationId}`,
  )
  return data
}

/**
 * 대화를 시작한다.
 *
 * 진행 중인 대화가 있으면 서버가 새로 만들지 않고 거기에 이어 붙인다 —
 * 담당자당 활성 대화는 하나다.
 */
export async function startConversation(message: string): Promise<ConversationDetail> {
  const { data } = await apiClient.post<ConversationDetail>(
    '/v1/agent/conversations',
    { message },
    { timeout: TURN_TIMEOUT_MS },
  )
  return data
}

export async function sendMessage(
  conversationId: number,
  message: string,
): Promise<ConversationDetail> {
  const { data } = await apiClient.post<ConversationDetail>(
    `/v1/agent/conversations/${conversationId}/messages`,
    { message },
    { timeout: TURN_TIMEOUT_MS },
  )
  return data
}

/** 담당자가 완료 처리한다. 그때까지 만들어진 거래는 그대로 남는다. */
export async function completeConversation(conversationId: number): Promise<void> {
  await apiClient.post(`/v1/agent/conversations/${conversationId}/complete`)
}

export async function listConversations(size = 20): Promise<ConversationSummary[]> {
  const { data } = await apiClient.get<ConversationSummary[]>('/v1/agent/conversations', {
    params: { size },
  })
  return data
}

/**
 * 카드를 완료 처리한다. 마지막 단계에서만 부른다.
 *
 * 서버가 도메인 서비스를 실행하고 대화를 이어가므로 응답이 오기까지 LLM 호출이 낀다.
 */
export async function completeAction(
  actionId: number,
  resultJson?: string,
): Promise<ConversationDetail> {
  const { data } = await apiClient.post<ConversationDetail>(
    `/v1/agent/actions/${actionId}/complete`,
    { resultJson },
    { timeout: TURN_TIMEOUT_MS },
  )
  return data
}

/**
 * 다단계 서명의 중간 단계를 보고한다.
 *
 * 서버가 LLM 을 부르지 않으므로 빠르고, 대화 전체를 돌려주지 않는다.
 * 화면은 카드의 진행 표시만 갱신하면 된다.
 */
export async function reportActionStep(actionId: number, resultJson: string): Promise<void> {
  await apiClient.post(`/v1/agent/actions/${actionId}/step`, { resultJson })
}

/**
 * 대화 밖에서 끝낸 일을 알린다 (결제 복귀 등).
 *
 * 모델을 한 번 부르므로 크레딧이 든다. 아무 때나 부르지 않는다.
 */
export async function reportEvent(
  conversationId: number,
  message: string,
): Promise<ConversationDetail> {
  const { data } = await apiClient.post<ConversationDetail>(
    `/v1/agent/conversations/${conversationId}/events`,
    { message },
    { timeout: TURN_TIMEOUT_MS },
  )
  return data
}

export async function rejectAction(actionId: number): Promise<ConversationDetail> {
  const { data } = await apiClient.post<ConversationDetail>(
    `/v1/agent/actions/${actionId}/reject`,
    undefined,
    { timeout: TURN_TIMEOUT_MS },
  )
  return data
}
