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

  it('범위를 나타내는 물결표는 취소선으로 그리지 않는다', () => {
    // "0.6~0.8kg … 6~8kg" 의 두 물결표 사이가 통째로 그어졌다.
    render(<AgentMessageBody content={'바지 한 벌에 약 0.6~0.8kg이 든다고 보면, 10벌에 6~8kg 정도가 필요합니다.'} />)
    expect(document.querySelector('del')).toBeNull()
    expect(document.body.textContent).toContain('0.6~0.8kg이 든다고 보면, 10벌에 6~8kg')
  })

  it('물결표 두 개로 감싸면 여전히 취소선이다', () => {
    render(<AgentMessageBody content={'~~취소된 금액~~ 30,000원'} />)
    expect(document.querySelector('del')?.textContent).toBe('취소된 금액')
  })

  it('원시 HTML 은 실행하지 않는다', () => {
    render(<AgentMessageBody content={'<img src=x onerror="alert(1)"> 안녕하세요'} />)
    expect(document.querySelector('img')).toBeNull()
  })
})
