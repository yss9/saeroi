import { describe, expect, it } from 'vitest'
import { timelineOf } from './AgentChat.tsx'
import type { ConversationDetail } from '../types/agent.types.ts'

/**
 * 카드가 대화의 제자리에 놓이는지.
 *
 * 예전에는 카드를 로그 끝에 모아 그려서, 여정이 길어질수록 처리된 카드가 바닥에 쌓이고
 * 지금 눌러야 할 카드가 그 사이에 묻혔다.
 */
describe('timelineOf', () => {
  const message = (id: number, content: string) => ({
    id,
    role: 'ASSISTANT' as const,
    content,
    toolName: null,
    createdAt: '2026-09-21T12:00:00',
  })

  const action = (id: number, messageId: number) => ({
    id,
    messageId,
    actionType: 'CONFIRM' as const,
    toolName: 'confirm_receipt',
    summary: `카드 ${id}`,
    payloadJson: '{}',
    resultJson: null,
    status: 'PENDING' as const,
    stepTotal: 1,
    stepDone: 0,
    expiresAt: null,
  })

  const conversation = (messages: unknown[], actions: unknown[]) =>
    ({ id: 1, title: 't', phase: 'MATERIAL_SOURCING', status: 'ACTIVE', messages, actions }) as
      unknown as ConversationDetail

  it('카드가 자기를 만든 대목 뒤에 놓인다', () => {
    const timeline = timelineOf(
      conversation([message(1, '첫 말'), message(5, '나중 말')], [action(10, 2)]),
    )

    expect(timeline.map((entry) => (entry.kind === 'message' ? entry.message.content : 'CARD')))
      .toEqual(['첫 말', 'CARD', '나중 말'])
  })

  it('끝에 몰아넣지 않는다 — 옛 카드가 새 대화를 가리지 않는다', () => {
    const timeline = timelineOf(
      conversation(
        [message(1, '옛 말'), message(9, '지금 말')],
        [action(10, 2), action(11, 20)],
      ),
    )

    const shapes = timeline.map((entry) => (entry.kind === 'message' ? 'MSG' : 'CARD'))
    expect(shapes).toEqual(['MSG', 'CARD', 'MSG', 'CARD'])
  })

  it('한 턴에 카드가 여럿이면 만들어진 차례로 둔다', () => {
    const timeline = timelineOf(conversation([message(1, '말')], [action(21, 2), action(20, 2)]))

    expect(timeline.filter((e) => e.kind === 'action').map((e) => (e as { action: { id: number } }).action.id))
      .toEqual([20, 21])
  })

  it('아직 보내지지 않은 말풍선은 언제나 끝이다', () => {
    // 낙관적 말풍선은 렌더 키가 겹치지 않도록 id 가 음수다. 그대로 정렬하면 방금 친 말이
    // 대화 맨 위로 올라갔다가 답이 온 뒤에야 내려온다.
    const timeline = timelineOf(
      conversation(
        [message(1, '옛 말'), message(9, '지금 말'), message(-1758424000000, '방금 친 말')],
        [action(10, 2)],
      ),
    )

    const last = timeline.at(-1)
    expect(last?.kind).toBe('message')
    expect(last?.kind === 'message' ? last.message.content : null).toBe('방금 친 말')
  })

  it('대화가 없으면 빈 목록이다', () => {
    expect(timelineOf(null)).toEqual([])
  })
})
