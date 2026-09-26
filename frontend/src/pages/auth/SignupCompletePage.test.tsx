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

    expect(screen.getByRole('heading', { name: '가입이 완료되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('개인회원')).toBeInTheDocument()
    expect(screen.queryByText(/지갑 연결은/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '로그인하기' })).toHaveAttribute('href', '/login')
  })

  it('renders the requested wallet guidance only for business members', () => {
    renderPage('business')

    expect(screen.getByRole('heading', { name: '가입이 완료되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('로그인 후 마이페이지에서 지갑을 등록해 주세요. 관리자 승인이 완료되면 파트너 기능을 사용할 수 있습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈으로 이동' })).toHaveAttribute('href', '/')
  })

  it('falls back to a safe generic completion view without navigation state', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '가입이 완료되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('로그인하고 SAEROI를 시작해 보세요.')).toBeInTheDocument()
  })
})
