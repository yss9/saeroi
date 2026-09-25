import { createQueryKeys } from '../../../utils/queryKeys.ts'

/**
 * 에이전트 도메인의 Query Key.
 *
 * 담당자당 활성 대화가 하나뿐이라 목록보다 `active()` 를 훨씬 자주 쓴다.
 */
const keys = createQueryKeys('agent')

export const agentKeys = {
  ...keys,

  /** 진행 중인 대화. 대화 창이 열릴 때 이걸로 시작한다. */
  active: () => [...keys.all, 'active'] as const,

  /**
   * 대화 위 진행 막대.
   *
   * **여기 있는 이유** — 예전에는 막대가 자기 파일에서 `['agent','progress',id]` 를 직접
   * 만들어 썼다. 그래서 카드를 승인하거나 말을 보낼 때 아무도 이 키를 무효화하지 않았고,
   * 단계가 끝나도 막대는 20초 폴링을 기다려야 바뀌었다. 막대에는 "다음에 무엇을 하면
   * 되는지"(`nextTodo`)가 함께 실려 있어서, 늦는 만큼 담당자가 다음 할 일을 늦게 안다.
   */
  progress: (conversationId: number) => [...keys.all, 'progress', conversationId] as const,
}
