import { useQuery } from '@tanstack/react-query'
import { agentKeys } from '../api/agentKeys.ts'
import { getJourneyProgress } from '../api/agentApi.ts'

/** 상대가 움직였는지 보려고 주기적으로 다시 읽는다. 도메인만 읽으므로 크레딧이 들지 않는다. */
const REFRESH_MS = 20_000

/**
 * 대화의 진행 상황 — 진행 막대와 오른쪽 할 일 패널이 **같은 조회**를 읽는다.
 *
 * 둘 다 서버가 한 번 판정한 결과를 그린다. 예전에는 패널이 거래 목록을 받아 할 일을 스스로
 * 판정했고(`pendingStepOf`), 그래서 막대·패널·대화가 한 화면에서 서로 다른 말을 했다. 패널은
 * 거래만 볼 줄 알아 가공·생산의 할 일은 아예 없었다.
 *
 * 같은 쿼리 키라 react-query 가 요청을 합쳐 준다. 둘이 따로 불러도 네트워크는 한 번이다.
 */
export function useJourneyProgress(conversationId: number | null) {
  return useQuery({
    queryKey: agentKeys.progress(conversationId as number),
    queryFn: () => getJourneyProgress(conversationId as number),
    enabled: conversationId !== null,
    refetchInterval: REFRESH_MS,
    // 창을 보고 있지 않을 때는 조회하지 않는다. 대화창을 열어둔 채 다른 일을 하는 경우가
    // 많아, 이게 없으면 종일 서버를 두드린다.
    refetchIntervalInBackground: false,
  })
}
