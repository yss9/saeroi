import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AppProviders } from '../../app/providers/AppProviders.tsx'
import { LoginPage } from './LoginPage.tsx'

function renderPage() {
  return render(
    <AppProviders>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AppProviders>,
  )
}

afterEach(cleanup)

describe('LoginPage', () => {
  it('renders the standard account login entry', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '다시 만나 반가워요' })).toBeInTheDocument()
    expect(screen.getByLabelText('이메일')).toHaveAttribute('type', 'email')
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('link', { name: '회원가입' })).toHaveAttribute('href', '/signup')
    expect(screen.queryByText('지갑으로 로그인')).not.toBeInTheDocument()
  })

  it('shows required field messages before submitting an empty form', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('이메일을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호를 입력해 주세요.')).toBeInTheDocument()
  })
})
