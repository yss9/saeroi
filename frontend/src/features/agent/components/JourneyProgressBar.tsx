import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { agentKeys } from '../api/agentKeys.ts'
import { getJourneyProgress } from '../api/agentApi.ts'
import type { JourneyMark } from '../types/agent.types.ts'

/** 상대가 움직였는지 보려고 주기적으로 다시 읽는다. 도메인만 읽으므로 크레딧이 들지 않는다. */
const REFRESH_MS = 20_000

/**
 * 지금 여정이 어디까지 왔는지. <b>대화 위에 항상 떠 있다.</b>
 *
 * 담당자가 스크롤을 올려 예전 답변을 뒤지지 않고도 "지금 뭐 하는 중인지" 를 알 수 있어야
 * 한다. 그것이 이 창에서 가장 자주 하는 질문이다.
 *
 * <h2>두 층인 이유</h2>
 *
 * 위는 여정 네 칸이다. 대화가 길어져도 전체에서 어디쯤인지 잃지 않는다. 아래는 지금 단계의
 * 세부이고 단계가 바뀌면 내용이 통째로 바뀐다 — 네 단계의 세부를 모두 펼치면 스무 줄이 되어
 * 아무도 읽지 않는다.
 *
 * <h2>판정을 하지 않는다</h2>
 *
 * 무엇이 끝났고 다음에 뭘 해야 하는지는 **서버가 계산해서 내려준다.** 화면이 따로 계산하면
 * 모델이 말하는 것과 막대가 어긋나고, 담당자는 둘 중 어느 것이 맞는지 알 수 없게 된다.
 * 가공 계획은 대화에 저장되어 있어 화면이 읽을 수도 없다.
 */
export function JourneyProgressBar({ conversationId }: { conversationId: number | null }) {
  const queryClient = useQueryClient()
  const { data, dataUpdatedAt } = useQuery({
    queryKey: ['agent', 'progress', conversationId],
    queryFn: () => getJourneyProgress(conversationId as number),
    enabled: conversationId !== null,
    refetchInterval: REFRESH_MS,
    // 창을 보고 있지 않을 때는 조회하지 않는다. 대화창을 열어둔 채 다른 일을 하는 경우가
    // 많아, 이게 없으면 종일 서버를 두드린다.
    refetchIntervalInBackground: false,
  })

  /*
    서버가 카드를 새로 띄웠으면 대화를 다시 읽는다.

    공급업체가 발송하거나 가공업체가 수락하면 담당자 차례가 되는데, 담당자는 그런 단계가
    있다는 것조차 모른다. 그래서 서버가 카드를 대신 띄우고, 화면은 이 신호를 보고 대화를
    갱신해 그 카드를 보여 준다.

    `conversationChanged` 일 때만 무효화한다. 매번 하면 20초마다 대화 전체를 받아 오게 되고,
    그건 대화를 주기 조회하지 않기로 한 이유를 그대로 되돌리는 것이다.

    **`dataUpdatedAt` 을 함께 본다.** 플래그만 의존성에 두면 값이 `true` 에서 `true` 로
    이어질 때 effect 가 다시 돌지 않는다 — 연달아 두 개의 카드가 뜨면 두 번째를 놓친다.
    조회 시각은 폴링마다 바뀌므로, 판정은 플래그가 하고 재실행은 이 값이 맡는다.
  */
  useEffect(() => {
    if (data?.conversationChanged) {
      void queryClient.invalidateQueries({ queryKey: agentKeys.active() })
    }
  }, [data?.conversationChanged, dataUpdatedAt, queryClient])

  if (!data) return null

  return (
    <nav className="agent-progress" aria-label="여정 진행 상황">
      <ol className="agent-progress__journey">
        {data.journey.map((mark) => (
          <li key={mark.label} className={classOf(mark, 'agent-progress__phase')}>
            {mark.label}
          </li>
        ))}
      </ol>

      {data.subject && (
        <p className="agent-progress__subject">
          <span className="agent-progress__name">{data.subject}</span>
          {data.subjectDetail && (
            <span className="agent-progress__amount">{data.subjectDetail}</span>
          )}
        </p>
      )}

      {data.steps.length > 0 && (
        <ol className="agent-progress__steps">
          {data.steps.map((mark) => (
            <li key={mark.label} className={classOf(mark, 'agent-progress__step')}>
              <span className="agent-progress__check" aria-hidden="true">
                ✓
              </span>
              <span className="agent-progress__label">{mark.label}</span>
            </li>
          ))}
        </ol>
      )}

      {/* 담당자가 할 일이 없는 구간에서는 "기다리는 중" 이라고 분명히 말한다. 비워 두면
          무언가 놓친 것이 있는지 대화를 거슬러 찾게 된다. */}
      {data.nextTodo && <p className="agent-progress__todo">{data.nextTodo}</p>}
    </nav>
  )
}

function classOf(mark: JourneyMark, base: string): string {
  return [base, mark.done ? 'is-done' : 'is-todo', mark.current ? 'is-current' : '']
    .join(' ')
    .trim()
}
