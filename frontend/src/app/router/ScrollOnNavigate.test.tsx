import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ScrollOnNavigate } from './ScrollOnNavigate.tsx'

function NavigationFixture() {
  const navigate = useNavigate()
  return (
    <>
      <button type="button" onClick={() => navigate('/next')}>다른 경로</button>
      <button type="button" onClick={() => navigate('?action=cancel')}>쿼리 변경</button>
      <button type="button" onClick={() => navigate('/next#anchor')}>해시 경로</button>
      <button type="button" onClick={() => navigate('#anchor')}>해시만 변경</button>
      <button
        type="button"
        onClick={() => navigate('/canonical', {
          replace: true,
          state: { skipGlobalScrollReset: true },
        })}
      >
        교체 이동
      </button>
      <div id="anchor">이동 위치</div>
    </>
  )
}

function show() {
  render(
    <MemoryRouter initialEntries={['/current']}>
      <Routes>
        <Route element={<ScrollOnNavigate />}>
          <Route path="*" element={<NavigationFixture />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(HTMLElement.prototype, 'scrollIntoView').mockImplementation(() => undefined)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('ScrollOnNavigate', () => {
  it('다른 pathname으로 이동하면 맨 위로 한 번 이동한다', () => {
    show()
    vi.mocked(window.scrollTo).mockClear()

    fireEvent.click(screen.getByRole('button', { name: '다른 경로' }))

    expect(window.scrollTo).toHaveBeenCalledOnce()
    expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' })
  })

  it('쿼리스트링만 바뀌면 움직이지 않는다', () => {
    show()
    vi.mocked(window.scrollTo).mockClear()

    fireEvent.click(screen.getByRole('button', { name: '쿼리 변경' }))

    expect(window.scrollTo).not.toHaveBeenCalled()
    expect(HTMLElement.prototype.scrollIntoView).not.toHaveBeenCalled()
  })

  it('해시가 있는 다른 경로는 해당 요소로 이동한다', () => {
    show()
    vi.mocked(window.scrollTo).mockClear()

    fireEvent.click(screen.getByRole('button', { name: '해시 경로' }))

    expect(window.scrollTo).not.toHaveBeenCalled()
    expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalledOnce()
    expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalledWith({ behavior: 'auto', block: 'start' })
  })

  it('같은 pathname에서 해시만 바뀌면 전역 스크롤을 실행하지 않는다', () => {
    show()
    vi.mocked(window.scrollTo).mockClear()

    fireEvent.click(screen.getByRole('button', { name: '해시만 변경' }))

    expect(window.scrollTo).not.toHaveBeenCalled()
    expect(HTMLElement.prototype.scrollIntoView).not.toHaveBeenCalled()
  })

  it('이미 상단을 처리한 정규 경로 교체 이동은 다시 스크롤하지 않는다', () => {
    show()
    vi.mocked(window.scrollTo).mockClear()

    fireEvent.click(screen.getByRole('button', { name: '교체 이동' }))

    expect(window.scrollTo).not.toHaveBeenCalled()
  })
})
