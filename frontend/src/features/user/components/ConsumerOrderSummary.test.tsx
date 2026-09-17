// 주문 현황.
//
// **표의 질문은 "지금 몇 건이 어디까지 왔나" 다.** 칸을 누르면 그 상태로 주문 목록이
// 필터링된다(2026-09-17) — 배경은 모든 칸이 같은 색이고, 지금 필터링 중인 칸만 표시가
// 다르다. "건수가 있는 칸만 배경을 칠한다"던 예전 규칙은 이 개편으로 없어졌다.

import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ConsumerOrderStageSummary } from '../types/consumerMyPage.types.ts'
import { ConsumerOrderSummary } from './ConsumerOrderSummary.tsx'

function stages(counts: [number, number, number, number]): ConsumerOrderStageSummary[] {
  return [
    { stage: 'PAYMENT_PENDING', count: counts[0] },
    { stage: 'PREPARING', count: counts[1] },
    { stage: 'SHIPPING', count: counts[2] },
    { stage: 'DELIVERED', count: counts[3] },
  ]
}

afterEach(cleanup)

describe('ConsumerOrderSummary', () => {
  it('건수를 그대로 보여준다', () => {
    render(
      <ConsumerOrderSummary
        stages={stages([0, 1, 0, 3])}
        selectedStage={null}
        onSelectStage={() => {}}
      />,
    )

    const items = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(items).toHaveLength(4)
    expect(within(items[3]).getByText('3')).toBeInTheDocument()
  })

  // 0 이 네 칸 늘어선 표는 처음 온 사람에게 무엇을 세는 것인지조차 알려주지 않는다.
  it('한 건도 없으면 그리지 않는다', () => {
    const { container } = render(
      <ConsumerOrderSummary
        stages={stages([0, 0, 0, 0])}
        selectedStage={null}
        onSelectStage={() => {}}
      />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('칸을 누르면 그 상태를 선택으로 알린다', () => {
    const onSelectStage = vi.fn()
    render(
      <ConsumerOrderSummary
        stages={stages([0, 1, 0, 3])}
        selectedStage={null}
        onSelectStage={onSelectStage}
      />,
    )

    screen.getByRole('button', { name: /상품 준비/ }).click()

    expect(onSelectStage).toHaveBeenCalledWith('PREPARING')
  })

  it('이미 선택된 칸을 다시 누르면 선택을 해제한다', () => {
    const onSelectStage = vi.fn()
    render(
      <ConsumerOrderSummary
        stages={stages([0, 1, 0, 3])}
        selectedStage="PREPARING"
        onSelectStage={onSelectStage}
      />,
    )

    screen.getByRole('button', { name: /상품 준비/ }).click()

    expect(onSelectStage).toHaveBeenCalledWith(null)
  })

  it('선택된 칸을 표시한다', () => {
    render(
      <ConsumerOrderSummary
        stages={stages([0, 1, 0, 3])}
        selectedStage="PREPARING"
        onSelectStage={() => {}}
      />,
    )

    const items = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(items[1].classList.contains('is-selected')).toBe(true)
    expect(items[0].classList.contains('is-selected')).toBe(false)
  })
})
