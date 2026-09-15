// 주문 현황.
//
// **표의 질문은 "지금 몇 건이 어디까지 왔나" 다.** 그래서 강조는 자리가 아니라 건수를
// 따라야 한다 — 예전에는 CSS 가 세 번째 칸(배송 중)을 무조건 칠했고, 실제 주문이 붙자
// 0 건인 칸이 파랗고 1 건인 칸은 회색이 되어 표가 정확히 반대로 읽혔다.

import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
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

/** 강조된 칸의 이름들. CSS 클래스가 그 상태를 들고 있다. */
function highlighted(): string[] {
  return within(screen.getByRole('list'))
    .getAllByRole('listitem')
    .filter((item) => item.classList.contains('is-active'))
    .map((item) => item.querySelector('strong')?.textContent ?? '')
}

afterEach(cleanup)

describe('ConsumerOrderSummary', () => {
  // 이 테스트가 이 파일의 존재 이유다.
  it('주문이 있는 칸만 강조한다', () => {
    render(<ConsumerOrderSummary stages={stages([0, 1, 0, 0])} />)

    expect(highlighted()).toEqual(['상품 준비'])
  })

  it('여러 칸에 걸쳐 있으면 그 칸을 모두 강조한다', () => {
    render(<ConsumerOrderSummary stages={stages([0, 2, 1, 0])} />)

    expect(highlighted()).toEqual(['상품 준비', '배송 중'])
  })

  it('건수를 그대로 보여준다', () => {
    render(<ConsumerOrderSummary stages={stages([0, 1, 0, 3])} />)

    const items = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(items).toHaveLength(4)
    expect(within(items[3]).getByText('3')).toBeInTheDocument()
  })

  // 0 이 네 칸 늘어선 표는 처음 온 사람에게 무엇을 세는 것인지조차 알려주지 않는다.
  it('한 건도 없으면 그리지 않는다', () => {
    const { container } = render(<ConsumerOrderSummary stages={stages([0, 0, 0, 0])} />)

    expect(container).toBeEmptyDOMElement()
  })
})
