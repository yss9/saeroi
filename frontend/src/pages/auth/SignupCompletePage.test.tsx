import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import type { SignupAccountType } from '../../features/auth/components/SignupCompleteContent.tsx'
import { SignupCompletePage } from './SignupCompletePage.tsx'

function renderPage(accountType?: SignupAccountType) {
  return render(
    <MemoryRouter
      initialEntries={[{
        pathname: '/signup/complete',
        state: accountType ? { accountType } : null,
      }]}
    >
      <SignupCompletePage />
    </MemoryRouter>,
  )
}

afterEach(cleanup)

describe('SignupCompletePage', () => {
  it('renders the personal member completion guidance', () => {
    renderPage('consumer')

    expect(screen.getByRole('heading', { name: '개인회원 가입이 완료되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('개인회원')).toBeInTheDocument()
    expect(screen.queryByText(/지갑 연결은/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '로그인하기' })).toHaveAttribute('href', '/login')
  })

  it('renders the requested wallet guidance only for business members', () => {
    renderPage('business')

    expect(screen.getByRole('heading', { name: '사업자회원 가입이 완료되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('지갑 연결은 로그인 후 마이페이지에서 진행해주세요.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '쇼핑몰 홈으로' })).toHaveAttribute('href', '/')
  })

  it('falls back to a safe generic completion view without navigation state', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '회원 가입이 완료되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('로그인하고 SAEROI의 서비스를 시작해 보세요.')).toBeInTheDocument()
  })
})
