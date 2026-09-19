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
}
