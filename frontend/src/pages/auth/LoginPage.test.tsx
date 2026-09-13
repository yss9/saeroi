import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '../../app/providers/AppProviders.tsx'
import { apiClient } from '../../services/apiClient.ts'
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

  it('직접 로그인한 Brand를 기본 가공 요청 화면으로 보낸다', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {
      accessToken: 'token',
      user: { id: 7, email: 'brand@test.com', name: '브랜드', phone: '010', role: 'BRAND', status: 'ACTIVE', createdAt: '2026-09-09T00:00:00' },
    } })
    const user = userEvent.setup()
    render(<AppProviders><MemoryRouter initialEntries={['/login']}><Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/brand/processing" element={<h1>가공 요청 업무 화면</h1>} />
    </Routes></MemoryRouter></AppProviders>)

    await user.type(screen.getByLabelText('이메일'), 'brand@test.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '가공 요청 업무 화면' })).toBeInTheDocument()
  })
})
