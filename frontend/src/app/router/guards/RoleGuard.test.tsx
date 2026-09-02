// Role 가드 동작 검증.
//
// 가드는 "보안" 이 아니라 "잘못된 화면으로 들어가지 않게 하는 UX 장치" 이므로,
// 검증할 것은 딱 세 가지다: 통과 / 권한 없음 / 미로그인.
//
// createMemoryRouter(데이터 라우터) 대신 MemoryRouter 를 쓰는 이유:
// 데이터 라우터는 리다이렉트 시 내부적으로 Request 를 만드는데, jsdom 환경에서는
// AbortSignal 구현이 undici 와 맞지 않아 실패한다. 가드는 렌더 결과만 확인하면
// 충분하므로 loader 가 없는 컴포넌트 라우터로 검증한다.

import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { UserRole } from '../../../types/role.types.ts'
import { RoleGuard } from './RoleGuard.tsx'

function signInAs(role: UserRole) {
  useAuthStore
    .getState()
    .signIn({ id: 1, email: 'a@b.c', name: '테스터', role, walletAddress: null }, 'test-token')
}

afterEach(() => {
  cleanup()
  useAuthStore.getState().signOut()
})

function renderGuardedRoute(allow: readonly UserRole[]) {
  return render(
    <MemoryRouter initialEntries={['/supplier']}>
      <Routes>
        <Route element={<RoleGuard allow={allow} />}>
          <Route path="/supplier" element={<p>공급자 전용 화면</p>} />
        </Route>
        <Route path="/" element={<p>홈</p>} />
        <Route path="/login" element={<p>로그인 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RoleGuard', () => {
  it('허용된 Role 이면 통과시킨다', () => {
    signInAs('SUPPLIER')
    renderGuardedRoute(['SUPPLIER'])
    expect(screen.getByText('공급자 전용 화면')).toBeInTheDocument()
  })

  it('로그인은 했지만 Role 이 다르면 홈으로 보낸다', () => {
    signInAs('CONSUMER')
    renderGuardedRoute(['SUPPLIER'])
    expect(screen.getByText('홈')).toBeInTheDocument()
  })

  // RoleGuard 를 ProtectedRoute 없이 단독으로 쓴 경우의 방어. 로그인 화면으로 보내야
  // 하며, 홈으로 보내면 사용자는 "왜 안 되는지" 알 수 없다.
  it('로그인하지 않았으면 로그인 화면으로 보낸다', () => {
    renderGuardedRoute(['SUPPLIER'])
    expect(screen.getByText('로그인 화면')).toBeInTheDocument()
  })
})
