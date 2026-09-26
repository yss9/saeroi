import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AgentTaskPanel } from './AgentTaskPanel.tsx'
import type { ConversationDetail, JourneyProgress } from '../types/agent.types.ts'

const progress = vi.hoisted(() => ({ current: null as Partial<JourneyProgress> | null }))

vi.mock('../hooks/useAgentTrades.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useAgentTrades.ts')>()),
  useAgentTrades: () => ({ data: { content: [] } }),
}))
vi.mock('../hooks/useJourneyProgress.ts', () => ({
  useJourneyProgress: () => ({ data: progress.current }),
}))

/**
 * "결제한 금액" 은 소재 구매와 **가공비**를 함께 보인다.
 *
 * 예전에는 화면이 거래만 더해, 150,000원을 낸 가공비가 빠진 채 20,000원만 떴다. 가공은 작업 하나가 한 줄이고,
 * 한 업체에 맡긴 공정들은 그 줄에 함께 적힌다.
 */
describe('AgentTaskPanel 결제한 금액', () => {
  afterEach(cleanup)

  it('소재 구매와 가공비를 나눠 소계와 합계를 보인다', () => {
    progress.current = {
      tasks: [],
      spending: [
        { kind: 'MATERIAL', label: '폐데님 원단 10kg', detail: null, amount: 20_000 },
        { kind: 'PROCESSING', label: '폐데님 원단 10kg 가공', detail: '세척·해체·선별 · pro', amount: 150_000 },
        { kind: 'PROCESSING', label: '폐데님 가공 소재 9.5kg 가공', detail: '재단 · 홍길동', amount: 20_000 },
      ],
    }

    render(<AgentTaskPanel conversation={conversation()} />)

    // 합계
    expect(screen.getByText('190,000원')).toBeTruthy()
    // 소계
    expect(screen.getByText('소재 구매')).toBeTruthy()
    expect(screen.getByText('가공비')).toBeTruthy()
    expect(screen.getByText('170,000원')).toBeTruthy()
    // 업체가 다르면 따로, 한 업체의 공정은 한 줄에
    expect(screen.getByText('세척·해체·선별 · pro')).toBeTruthy()
    expect(screen.getByText('재단 · 홍길동')).toBeTruthy()
  })

  it('결제한 것이 없으면 칸을 그리지 않는다', () => {
    progress.current = { tasks: [], spending: [] }

    render(<AgentTaskPanel conversation={conversation()} />)

    expect(screen.queryByText('결제한 금액')).toBeNull()
  })
})

function conversation(): ConversationDetail {
  return {
    id: 22,
    title: '폐데님 바지',
    phase: 'PRODUCTION',
    status: 'ACTIVE',
    messages: [],
    actions: [],
    projectTradeIds: [],
  } as unknown as ConversationDetail
}
