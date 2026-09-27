import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  completeAction,
  completeConversation,
  getActiveConversation,
  rejectAction,
  reportActionStep,
  reportEvent,
  sendMessage,
  startConversation,
} from '../api/agentApi.ts'
import { agentKeys } from '../api/agentKeys.ts'
import type { AgentMessage, ConversationDetail } from '../types/agent.types.ts'

/**
 * 진행 중인 대화.
 *
 * `refetchInterval` 을 두지 않는다. 대화는 담당자가 말을 걸 때만 바뀌고, 주기 조회는
 * 서버 부하만 늘린다. 판매자 발송 같은 외부 변화는 담당자가 "발송됐어?" 라고 물을 때
 * 에이전트가 조회해서 알려준다.
 */
export function useActiveConversation() {
  return useQuery({
    queryKey: agentKeys.active(),
    queryFn: getActiveConversation,
    staleTime: 0,
  })
}

/**
 * 발화를 보낸다.
 *
 * <b>낙관적 갱신이 필수다.</b> 한 턴에 LLM 이 여러 번 불려 응답까지 20~30초가 걸리는데,
 * 그동안 담당자가 방금 친 말이 화면에 없으면 입력창만 비워진 채 아무 일도 안 일어난 것처럼
 * 보인다. 보낸 즉시 말풍선을 띄우고, 응답이 오면 서버가 준 것으로 갈아끼운다.
 *
 * 응답이 곧 최신 대화 전체라 다시 조회하지 않는다.
 */
export function useSendMessage() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ conversationId, message }: { conversationId: number | null; message: string }) =>
      conversationId === null ? startConversation(message) : sendMessage(conversationId, message),

    onMutate: ({ message }) => {
      const previous = queryClient.getQueryData<ConversationDetail | null>(agentKeys.active())
      if (!previous) {
        // 첫 발화라 아직 대화가 없다. 화면은 mutation 의 variables 로 직접 그린다.
        return { previous }
      }
      queryClient.setQueryData<ConversationDetail>(agentKeys.active(), {
        ...previous,
        messages: [...previous.messages, optimisticUserMessage(message)],
      })
      return { previous }
    },

    onError: (_error, _variables, context) => {
      // 실패하면 낙관적으로 넣은 말풍선을 되돌린다. 남겨두면 보내지지 않은 말이 보낸 것처럼
      // 남는다.
      if (context?.previous !== undefined) {
        queryClient.setQueryData(agentKeys.active(), context.previous)
      }
    },

    onSuccess: (detail) => writeCache(queryClient, detail),
  })
}

/**
 * 서버 응답 전에 띄우는 임시 말풍선.
 *
 * id 를 음수로 둔다. 서버가 주는 id 는 양수라 절대 겹치지 않고, 렌더 키가 충돌하지 않는다.
 */
export function optimisticUserMessage(content: string): AgentMessage {
  return {
    id: -Date.now(),
    role: 'USER',
    content,
    toolName: null,
    createdAt: new Date().toISOString(),
  }
}

/** 카드 완료. 마지막 단계에서만 부른다. */
export function useCompleteAction() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ actionId, resultJson }: { actionId: number; resultJson?: string }) =>
      completeAction(actionId, resultJson),
    onSuccess: (detail) => writeCache(queryClient, detail),
  })
}

/**
 * 다단계 서명의 중간 단계 보고.
 *
 * 대화를 돌려받지 않으므로 캐시를 덮지 않고 무효화만 한다 — 카드의 진행 표시가
 * 갱신되면 충분하다.
 */
export function useReportActionStep() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ actionId, resultJson }: { actionId: number; resultJson: string }) =>
      reportActionStep(actionId, resultJson),
    onSuccess: () => {
      // 다단계 서명의 중간 단계도 막대를 움직인다 — 생산 온체인 기록이 그렇다.
      void queryClient.invalidateQueries({ queryKey: agentKeys.all })
    },
  })
}

export function useRejectAction() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (actionId: number) => rejectAction(actionId),
    onSuccess: (detail) => writeCache(queryClient, detail),
  })
}

/** 결제 복귀 등 대화 밖 사건을 알리고 모델이 이어 말하게 한다. */
export function useReportEvent() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ conversationId, message }: { conversationId: number; message: string }) =>
      reportEvent(conversationId, message),
    onSuccess: (detail) => writeCache(queryClient, detail),
  })
}

export function useCompleteConversation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (conversationId: number) => completeConversation(conversationId),
    onSuccess: () => {
      // 대화가 닫혔다. 활성 대화 조회가 null 을 돌려주게 만든다.
      void queryClient.invalidateQueries({ queryKey: agentKeys.all })
    },
  })
}

/**
 * 대화가 바뀌었다. <b>진행 막대도 함께 갱신한다.</b>
 *
 * 카드를 승인하면 단계가 끝나는데, 막대는 자기 주기(20초)로만 다시 읽었다. 결제를 마치고도
 * 한참 동안 그 칸에 불이 들어오지 않았고, 막대가 함께 내려주는 "다음에 무엇을 하면
 * 되는지"도 그만큼 늦게 바뀌었다 — 담당자가 다음 할 일을 늦게 알게 되는 자리다.
 *
 * 무효화만 하고 기다리지 않는다. 막대는 대화와 별개로 그려지므로 늦게 도착해도 대화가
 * 멈추지 않는다.
 */
function writeCache(
  queryClient: ReturnType<typeof useQueryClient>,
  detail: ConversationDetail,
): void {
  queryClient.setQueryData(agentKeys.active(), detail)
  void queryClient.invalidateQueries({ queryKey: agentKeys.progress(detail.id) })
}
