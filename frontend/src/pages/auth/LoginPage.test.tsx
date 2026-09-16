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

  // 사업자가 로그인 화면을 만나는 경우는 대개 세션이 끊긴 때다. 보던 화면으로 되돌리면
  // 방금 실패한 요청을 다시 부르고, 사용자는 어디에 있었는지보다 무엇을 할 차례인지를
  // 먼저 봐야 한다.
  it('사업자는 보던 화면이 있어도 회사 정보로 보낸다', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {
      accessToken: 'token',
      user: { id: 7, email: 'brand@test.com', name: '브랜드', phone: '010', role: 'BRAND', status: 'ACTIVE', createdAt: '2026-09-09T00:00:00' },
    } })
    const user = userEvent.setup()
    render(<AppProviders><MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/brand/orders' } } }]}><Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/company/account" element={<h1>회사 정보 화면</h1>} />
      <Route path="/brand/orders" element={<h1>제품 발송 화면</h1>} />
    </Routes></MemoryRouter></AppProviders>)

    await user.type(screen.getByLabelText('이메일'), 'brand@test.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '회사 정보 화면' })).toBeInTheDocument()
  })

  // 랜딩에서 "쇼핑" 을 눌렀다가 로그인으로 넘어온 경우다. 되돌리지 않으면 사려던 물건을
  // 다시 찾아야 한다.
  it('소비자는 보던 화면으로 되돌린다', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {
      accessToken: 'token',
      user: { id: 5, email: 'c@test.com', name: '고객', phone: '010', role: 'CONSUMER', status: 'ACTIVE', createdAt: '2026-09-09T00:00:00' },
    } })
    const user = userEvent.setup()
    render(<AppProviders><MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/products' } } }]}><Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/mypage" element={<h1>마이페이지 화면</h1>} />
      <Route path="/products" element={<h1>쇼핑 화면</h1>} />
    </Routes></MemoryRouter></AppProviders>)

    await user.type(screen.getByLabelText('이메일'), 'c@test.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '쇼핑 화면' })).toBeInTheDocument()
  })

  it('직접 로그인한 Brand를 회사 정보로 보낸다', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {
      accessToken: 'token',
      user: { id: 7, email: 'brand@test.com', name: '브랜드', phone: '010', role: 'BRAND', status: 'ACTIVE', createdAt: '2026-09-09T00:00:00' },
    } })
    const user = userEvent.setup()
    render(<AppProviders><MemoryRouter initialEntries={['/login']}><Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/company/account" element={<h1>회사 정보 화면</h1>} />
    </Routes></MemoryRouter></AppProviders>)

    await user.type(screen.getByLabelText('이메일'), 'brand@test.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '회사 정보 화면' })).toBeInTheDocument()
  })
})
