import type { AgentMessage, ConversationDetail, PendingAction } from '../types/agent.types.ts'

/**
 * 대화와 카드를 <b>시간 순서대로</b> 엮는다.
 *
 * 예전에는 카드를 로그 끝에 모아 그렸다. 카드의 `messageId` 가 가리키는 tool 호출 메시지는
 * 화면에서 걸러지므로(사용자가 읽을 것은 모델의 말과 카드이지 호출 로그가 아니다) 말풍선에
 * 붙일 자리가 없었기 때문이다.
 *
 * 그 결과 여정이 길어질수록 처리된 카드가 바닥에 쌓이고, **지금 눌러야 할 카드가 그 사이에
 * 묻혔다.** 게다가 카드가 대화의 어느 대목에서 나온 것인지도 알 수 없었다 — "이 결제가
 * 무엇에 대한 것인가" 는 앞뒤 맥락과 함께 읽어야 한다.
 *
 * 그래서 붙일 자리가 없으면 **바로 앞 메시지 뒤에 놓는다.** 카드의 `messageId` 는 걸러진
 * 줄이지만 그 번호는 여전히 순서를 말해 주므로, 그보다 작은 id 를 가진 마지막 메시지 뒤가
 * 제자리다. 정렬만으로 같은 결과가 나온다 — 메시지 id 와 카드의 `messageId` 를 한 축에
 * 놓고 세우면 된다.
 *
 * 같은 자리를 가리키는 카드가 여럿이면(한 턴에 여러 tool 을 부른 경우) 카드끼리는 자기
 * id 순으로 둔다. 만들어진 차례가 곧 담당자가 눌러야 할 차례다.
 *
 * <b>아직 보내지지 않은 말풍선은 언제나 끝이다.</b> 낙관적 말풍선은 렌더 키가 겹치지 않도록
 * id 가 음수인데, 그걸 그대로 정렬하면 방금 친 말이 대화 맨 위로 올라갔다가 답이 온 뒤에야
 * 아래로 내려온다.
 */
export function timelineOf(conversation: ConversationDetail | null | undefined): TimelineEntry[] {
  if (!conversation) return []

  const entries: TimelineEntry[] = [
    ...conversation.messages.map((message) => ({
      kind: 'message' as const,
      // 아직 서버에 없는 말풍선은 id 가 음수다(`optimisticUserMessage`). 그대로 정렬하면
      // 방금 친 말이 대화 맨 위로 올라갔다가 답이 온 뒤에야 제자리로 내려온다.
      // 보낸 말은 언제나 끝이므로 정렬에서 맨 뒤로 보낸다.
      order: message.id < 0 ? Number.MAX_SAFE_INTEGER : message.id,
      tie: message.id < 0 ? message.id : 0,
      message,
    })),
    ...conversation.actions.map((action) => ({
      kind: 'action' as const,
      // 카드는 자기를 만든 tool 호출 **뒤에** 온다. 같은 번호면 메시지가 먼저다.
      order: action.messageId,
      tie: action.id,
      action,
    })),
  ]

  return entries.sort((a, b) => a.order - b.order || a.tie - b.tie)
}

export type TimelineEntry =
  | { kind: 'message'; order: number; tie: number; message: AgentMessage }
  | { kind: 'action'; order: number; tie: number; action: PendingAction }
