import { describe, expect, it } from 'vitest'
import { pinnedToPanel } from './pinnedCards.ts'
import type { PendingAction } from '../types/agent.types.ts'

/**
 * 떠 있는 카드를 어디에 그리는가.
 *
 * 결제 카드 세 장이 대화에 쌓였다가 모델이 계속 말하면서 위로 밀려, 담당자가 도로 스크롤해야
 * 했다 — 챗봇이 "울 원단의 결제 카드가 여전히 떠 있습니다" 라고 담당자를 위로 안내할 만큼
 * 흔한 일이었다. 그래서 넓은 화면에서는 따로 스크롤되는 오른쪽 패널에 고정한다.
 *
 * **한 곳에만 그려야 한다.** 양쪽에 그리면 같은 작업에 버튼이 둘이 되고, 서명 카드는 내부
 * 상태를 들고 있어 어느 쪽을 눌렀는지에 따라 다르게 굴러간다.
 */
const card = (over: Partial<PendingAction> = {}): PendingAction =>
  ({
    id: 1,
    messageId: 10,
    actionType: 'PAYMENT',
    toolName: 'purchase_material',
    summary: '깨끗한 데님 6kg 구매 (30,000원)',
    status: 'PENDING',
    stepTotal: 1,
    stepDone: 0,
    ...over,
  }) as PendingAction

describe('pinnedToPanel', () => {
  it('넓은 화면에서는 오른쪽 패널이 그린다', () => {
    expect(pinnedToPanel(card(), true)).toBe(true)
  })

  it('좁은 화면에서는 대화 안에 남는다', () => {
    // 780px 이하에서는 패널이 대화 **아래**로 내려간다. 거기 두면 대화 전체를 지나쳐야
    // 카드에 닿아 지금보다 나빠진다.
    expect(pinnedToPanel(card(), false)).toBe(false)
  })

  it('생산 온체인 기록도 넓은 화면에서는 패널이 그린다', () => {
    // 이것만 대화에 남겼더니 서명 카드 중 이것만 떠내려갔다. 패널 안에서는 줄여 그린다.
    expect(pinnedToPanel(card({ toolName: 'record_production_chain' }), true)).toBe(true)
  })

  it('처리가 끝난 카드는 고정하지 않는다', () => {
    // 끝난 카드는 대화에 한 줄 흔적으로 남는다. 패널에 쌓아 두면 할 일처럼 보인다.
    expect(pinnedToPanel(card({ status: 'COMPLETED' }), true)).toBe(false)
    expect(pinnedToPanel(card({ status: 'EXPIRED' }), true)).toBe(false)
  })
})
