import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConsumerSignupForm } from './ConsumerSignupForm.tsx'

afterEach(cleanup)

describe('ConsumerSignupForm', () => {
  it('validates required fields without submitting incomplete values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<ConsumerSignupForm onSubmit={onSubmit} />)

    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('이름을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('이메일을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('휴대전화 번호를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('이용약관에 동의해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('개인정보 수집 및 이용에 동의해 주세요.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('passes validated values through the API integration boundary', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<ConsumerSignupForm onSubmit={onSubmit} />)

    await user.type(screen.getByLabelText('이름'), '김새로')
    await user.type(screen.getByLabelText('휴대전화 번호'), '01012345678')
    expect(screen.getByLabelText('휴대전화 번호')).toHaveValue('010-1234-5678')
    await user.type(screen.getByLabelText('이메일'), 'consumer@saeroi.test')
    await user.type(screen.getByLabelText('비밀번호'), 'Saeroi!123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'Saeroi!123')
    await user.click(screen.getByLabelText('[필수] 이용약관 동의'))
    await user.click(screen.getByLabelText('[필수] 개인정보 수집 및 이용 동의'))
    await user.click(screen.getByLabelText('[선택] 마케팅 및 이벤트 소식 수신 동의'))
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        name: '김새로',
        email: 'consumer@saeroi.test',
        phone: '010-1234-5678',
        password: 'Saeroi!123',
        agreeTerms: true,
        agreePrivacy: true,
        agreeMarketing: true,
      })
    })
  })

  it('can reveal and hide the password without changing its value', async () => {
    const user = userEvent.setup()
    render(<ConsumerSignupForm onSubmit={vi.fn()} />)
    const password = screen.getByLabelText('비밀번호')

    await user.type(password, 'Saeroi!123')
    await user.click(screen.getByRole('button', { name: '비밀번호 보기' }))
    expect(password).toHaveAttribute('type', 'text')
    expect(password).toHaveValue('Saeroi!123')

    await user.click(screen.getByRole('button', { name: '비밀번호 숨기기' }))
    expect(password).toHaveAttribute('type', 'password')
  })
})
