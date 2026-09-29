import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AgentLauncher } from './AgentLauncher.tsx'

afterEach(() => {
  vi.restoreAllMocks()
  cleanup()
})

describe('AgentLauncher', () => {
  // 좁은 화면에서는 머리말이 글자를 감추고 아이콘만 남긴다. 그때도 무엇을 하는 버튼인지 읽혀야 한다.
  it('머리말 버튼은 글자를 감춰도 이름과 새 창으로 열린다는 사실을 말한다', () => {
    render(<AgentLauncher label="AI 어시스턴트로 소재 찾기" variant="header" />)

    const button = screen.getByRole('button', { name: 'AI 어시스턴트로 소재 찾기' })
    expect(button).toHaveClass('agent-header-button')
    expect(button).not.toHaveClass('ui-button')
    expect(button).toHaveAttribute('title', 'AI 어시스턴트로 소재 찾기 (새 창)')
    // 캐릭터 그림은 꾸밈이다. 이름은 aria-label 이 말하므로 화면 읽기에 두 번 읽히지 않게 한다.
    expect(button.querySelector('img')).toHaveAttribute('alt', '')
  })

  it('누르면 이름 있는 창으로 대화창을 연다', () => {
    const focus = vi.fn()
    const open = vi.spyOn(window, 'open').mockReturnValue({ focus } as unknown as Window)
    render(<AgentLauncher label="AI 어시스턴트로 소재 찾기" variant="header" />)

    fireEvent.click(screen.getByRole('button', { name: 'AI 어시스턴트로 소재 찾기' }))

    expect(open).toHaveBeenCalledWith(expect.stringMatching(/\/agent$/), 'saeroi-agent', expect.any(String))
    expect(focus).toHaveBeenCalled()
  })

  it('기본은 제자리에 놓이는 보조 버튼이다', () => {
    render(<AgentLauncher />)

    expect(screen.getByRole('button', { name: 'AI 어시스턴트' })).toHaveClass('ui-button', 'agent-launcher')
  })
})
