// 목록 정렬 드롭다운.
//
// 직접 만든 위젯이라 브라우저가 대신 해 주는 것이 없다 — 열고 닫는 일, 바깥 클릭,
// 스크린리더가 읽을 이름까지 전부 이 컴포넌트의 몫이다.

import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ListingSortBar } from './ListingSortBar.tsx'

afterEach(cleanup)

function renderBar(sort: 'LATEST' | 'PRICE_ASC' = 'LATEST') {
  const onSortChange = vi.fn()
  render(<ListingSortBar sort={sort} onSortChange={onSortChange} />)
  return { onSortChange, user: userEvent.setup() }
}

describe('ListingSortBar', () => {
  // 화면에서 "정렬" 이라는 글자를 지웠다고 스크린리더에서까지 사라지면, 이 버튼이 무엇을
  // 바꾸는지 알 길이 없다.
  it('현재 정렬만 적고 이름은 접근성 이름에 남긴다', () => {
    renderBar()

    const trigger = screen.getByRole('button', { name: '정렬: 최신순' })
    expect(trigger).toHaveTextContent('최신순')
    // 닫혀 있을 때는 선택지를 그리지 않는다.
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('누르면 아래에 선택지를 펴고, 고르면 알리고 닫는다', async () => {
    const { onSortChange, user } = renderBar()

    await user.click(screen.getByRole('button', { name: '정렬: 최신순' }))
    expect(screen.getByRole('listbox')).toBeInTheDocument()

    await user.click(screen.getByRole('option', { name: '가격 낮은순' }))

    expect(onSortChange).toHaveBeenCalledWith('PRICE_ASC')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  // 열어 둔 채 목록을 누르면 메뉴가 그 위에 남는다.
  it('바깥을 누르면 닫힌다', async () => {
    const { user } = renderBar()

    await user.click(screen.getByRole('button', { name: '정렬: 최신순' }))
    await user.click(document.body)

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('지금 고른 정렬을 선택 상태로 알린다', async () => {
    const { user } = renderBar('PRICE_ASC')

    await user.click(screen.getByRole('button', { name: '정렬: 가격 낮은순' }))

    expect(screen.getByRole('option', { name: '가격 낮은순' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })
})
