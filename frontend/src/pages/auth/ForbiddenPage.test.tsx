import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/authStore.ts'
import type { UserRole } from '../../types/role.types.ts'
import { ForbiddenPage } from './ForbiddenPage.tsx'

function renderPage(state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/403', state: state ?? null }]}>
      <ForbiddenPage />
    </MemoryRouter>,
  )
}

function signInAs(role: UserRole) {
  useAuthStore
    .getState()
    .signIn(
      { id: 1, email: 'a@b.c', name: '테스터', role, walletAddress: null },
      'test-token',
      'refresh-token',
    )
}

afterEach(() => {
  cleanup()
  useAuthStore.getState().signOut()
  localStorage.clear()
})

describe('ForbiddenPage', () => {
  it('들어가려던 경로와 허용 Role, 현재 Role 을 함께 설명한다', () => {
    signInAs('CONSUMER')
    renderPage({ from: { pathname: '/admin' }, allow: ['ADMIN'] })

    expect(screen.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
    const empty = screen.getByText(/다른 역할을 위한 화면입니다/)
    expect(empty).toHaveTextContent('/admin')
    expect(empty).toHaveTextContent('접근 가능 역할: 관리자.')
    expect(empty).toHaveTextContent('현재 로그인한 역할: 소비자.')
  })

  // 이 화면에 온 사용자는 이미 로그인해 있다. 로그인 유도를 넣으면
  // "로그인했는데 또 로그인" 이 되고 원래 위치로 되돌려 보내며 무한 왕복이 된다.
  it('로그인으로 유도하지 않고 홈으로 나가는 길만 준다', () => {
    signInAs('CONSUMER')
    renderPage({ from: { pathname: '/admin' }, allow: ['ADMIN'] })

    expect(screen.getByRole('link', { name: '홈으로 돌아가기' })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('link', { name: /로그인/ })).not.toBeInTheDocument()
  })

  // history state 는 사용자가 조작할 수 있다. 주소창으로 직접 들어와도,
  // 모르는 Role 문자열이 섞여 있어도 화면이 깨지지 않아야 한다.
  it('state 없이 직접 들어와도 안내를 보여 준다', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
    expect(screen.getByText('다른 역할을 위한 화면입니다.')).toBeInTheDocument()
  })

  it('알 수 없는 Role 문자열은 라벨로 만들지 않는다', () => {
    signInAs('CONSUMER')
    renderPage({ from: { pathname: '/admin' }, allow: ['ADMIN', 'ROOT'] })

    const empty = screen.getByText(/다른 역할을 위한 화면입니다/)
    expect(empty).toHaveTextContent('접근 가능 역할: 관리자.')
    expect(empty).not.toHaveTextContent('ROOT')
  })
})
