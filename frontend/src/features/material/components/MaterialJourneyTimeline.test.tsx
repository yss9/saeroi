// 소재 Journey Timeline.
//
// 여기서 막는 거짓말 셋:
//   1) 서버가 준 시간순을 다시 정렬해 뒤바꾼다
//   2) 온체인 해시가 없는 사건에 링크를 지어낸다
//   3) 사건이 없는 것을 오류로 보여준다

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { MaterialJourney, MaterialJourneyEntry } from '../types/material.types.ts'
import { MaterialJourneyTimeline } from './MaterialJourneyTimeline.tsx'

const api = vi.hoisted(() => ({ getMaterialJourney: vi.fn() }))
vi.mock('../api/getMaterialJourney.ts', () => api)

function entry(overrides: Partial<MaterialJourneyEntry> = {}): MaterialJourneyEntry {
  return {
    eventType: 'MATERIAL_REGISTERED',
    stage: 'SOURCE',
    actorWallet: null,
    actorName: null,
    quantity: null,
    timestamp: '2026-09-10T12:00:00',
    txHash: null,
    detail: null,
    ...overrides,
  }
}

function journey(overrides: Partial<MaterialJourney> = {}): MaterialJourney {
  return {
    materialId: 1,
    events: [entry()],
    ...overrides,
  }
}

function renderTimeline(materialId: number | null = 1) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MaterialJourneyTimeline materialId={materialId} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getMaterialJourney.mockReset()
})

afterEach(cleanup)

describe('정렬', () => {
  it('서버가 준 순서를 그대로 보여준다', async () => {
    api.getMaterialJourney.mockResolvedValue(journey({
      events: [
        entry({ eventType: 'MATERIAL_REGISTERED', timestamp: '2026-09-10T12:00:00' }),
        entry({ eventType: 'SOURCE_VERIFIED', timestamp: '2026-09-10T12:06:00' }),
        entry({ eventType: 'MARKETPLACE_LISTED', stage: 'TRANSACTION', timestamp: '2026-09-11T09:00:00' }),
      ],
    }))
    renderTimeline()

    const items = await screen.findAllByRole('listitem')
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('소재 등록'),
      expect.stringContaining('출처 검증'),
      expect.stringContaining('판매 등록'),
    ])
  })
})

describe('행위자·수량·해시 표시', () => {
  it('행위자 이름과 지갑을 함께 보여준다', async () => {
    api.getMaterialJourney.mockResolvedValue(journey({
      events: [entry({ actorName: '한빛섬유', actorWallet: '0x' + '2'.repeat(40) })],
    }))
    renderTimeline()

    expect(await screen.findByText(/한빛섬유/)).toBeInTheDocument()
    expect(screen.getByText(/0x2222…2222/)).toBeInTheDocument()
  })

  it('수량이 있으면 kg으로 보여준다', async () => {
    api.getMaterialJourney.mockResolvedValue(journey({
      events: [entry({ quantity: 5000 })],
    }))
    renderTimeline()

    expect(await screen.findByText('5 kg')).toBeInTheDocument()
  })

  it('온체인 해시가 있으면 표시하고, 없으면 만들어내지 않는다', async () => {
    api.getMaterialJourney.mockResolvedValue(journey({
      events: [
        entry({ eventType: 'SOURCE_VERIFIED', txHash: '0x' + 'b'.repeat(64) }),
        entry({ eventType: 'EVIDENCE_ATTACHED', txHash: null }),
      ],
    }))
    renderTimeline()

    expect(await screen.findByTitle('0x' + 'b'.repeat(64))).toBeInTheDocument()
    expect(screen.queryByText(/tx /)).not.toHaveTextContent('null')
  })
})

describe('세 가지 상태', () => {
  it('조회 중에는 로딩을 보여준다', () => {
    api.getMaterialJourney.mockReturnValue(new Promise(() => {}))
    renderTimeline()

    expect(screen.getByRole('status', { name: '소재 이력을 불러오는 중' })).toBeInTheDocument()
  })

  it('사건이 없으면 EmptyState 다 — 오류가 아니다', async () => {
    api.getMaterialJourney.mockResolvedValue(journey({ events: [] }))
    renderTimeline()

    expect(await screen.findByText('아직 기록된 사건이 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })

  it('실패하면 ErrorMessage 다', async () => {
    api.getMaterialJourney.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '이력을 불러오지 못했습니다.' }),
    )
    renderTimeline()

    expect(await screen.findByText('이력을 불러오지 못했습니다.')).toBeInTheDocument()
  })

  it('materialId가 null이면 요청하지 않는다', () => {
    renderTimeline(null)

    expect(api.getMaterialJourney).not.toHaveBeenCalled()
  })
})
