// 소재 검색 필터.
//
// 조건을 거는 것보다 **푸는 것**이 어려운 화면이었다. 네 개의 Select 를 하나씩 "전체" 로
// 되돌려야 했고, 무엇을 걸어 뒀는지 잊으면 결과가 왜 적은지도 알 수 없었다.

import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ListingFilterBar } from './ListingFilterBar.tsx'

afterEach(cleanup)

function renderBar(props: Partial<Parameters<typeof ListingFilterBar>[0]> = {}) {
  const onReset = vi.fn()
  render(
    <ListingFilterBar filters={{}} onFiltersChange={vi.fn()} onReset={onReset} {...props} />,
  )
  return { onReset }
}

describe('ListingFilterBar', () => {
  // 조건을 걸어야 버튼이 나타나면, 그 전까지는 되돌리는 방법이 있다는 것을 알 수 없다.
  it('조건이 없어도 초기화 버튼은 자리를 지키고 잠겨 있다', () => {
    renderBar()

    expect(screen.getByRole('button', { name: '조건 초기화' })).toBeDisabled()
  })

  it('조건이 걸려 있으면 초기화 버튼으로 한 번에 되돌린다', async () => {
    const user = userEvent.setup()
    const { onReset } = renderBar({ filters: { grade: 'A' } })

    await user.click(screen.getByRole('button', { name: '조건 초기화' }))

    expect(onReset).toHaveBeenCalledTimes(1)
  })

  // 정렬은 이 상자에 없다. 거르는 조건이 아니라 결과를 보는 방식이라 목록 위로 나갔다.
  it('정렬은 다루지 않는다', () => {
    renderBar({ filters: { grade: 'A' } })

    expect(screen.queryByLabelText('정렬')).not.toBeInTheDocument()
  })
})
