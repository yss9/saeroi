import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AgentMessageBody } from './AgentMessageBody.tsx'

describe('AgentMessageBody', () => {
  it('표를 표로 그린다', () => {
    render(
      <AgentMessageBody
        content={'| 소재 | 단가 |\n|---|---|\n| 폐데님 원단 | 2,000원/kg |'}
      />,
    )
    expect(screen.getByRole('table')).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: '소재' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '폐데님 원단' })).toBeTruthy()
  })

  it('별표를 글자로 남기지 않는다', () => {
    render(<AgentMessageBody content={'**데님 3kg** 이 필요합니다.'} />)
    expect(screen.getByText('데님 3kg').tagName).toBe('STRONG')
    expect(document.body.textContent).not.toContain('**')
  })

  it('원시 HTML 은 실행하지 않는다', () => {
    render(<AgentMessageBody content={'<img src=x onerror="alert(1)"> 안녕하세요'} />)
    expect(document.querySelector('img')).toBeNull()
  })
})
