// Role 가드 동작 검증.
//
// 가드는 "보안" 이 아니라 "잘못된 화면으로 들어가지 않게 하는 UX 장치" 이므로,
// 검증할 것은 통과 / 권한 없음(403) / 미로그인(401) / 판정 보류 네 가지다.
// 특히 **미로그인과 권한없음이 서로 다른 곳으로 가는지** 가 핵심이다 — 둘을 같은
// 곳으로 보내면 이미 로그인한 사용자가 로그인 화면을 다시 보게 된다.
//
// createMemoryRouter(데이터 라우터) 대신 MemoryRouter 를 쓰는 이유:
// 데이터 라우터는 리다이렉트 시 내부적으로 Request 를 만드는데, jsdom 환경에서는
// AbortSignal 구현이 undici 와 맞지 않아 실패한다. 가드는 렌더 결과만 확인하면
// 충분하므로 loader 가 없는 컴포넌트 라우터로 검증한다.

import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { UserRole } from '../../../types/role.types.ts'
import { FORBIDDEN_PATH, RoleGuard } from './RoleGuard.tsx'

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

/** 리다이렉트가 넘긴 history state 를 화면에 드러내 단언할 수 있게 한다. */
function StateProbe({ label }: { label: string }) {
  const location = useLocation()
  return (
    <p>
      {label}:{JSON.stringify(location.state)}
    </p>
  )
}

function renderGuardedRoute(allow: readonly UserRole[], redirectTo?: string) {
  return render(
    <MemoryRouter initialEntries={['/supplier']}>
      <Routes>
        <Route element={<RoleGuard allow={allow} redirectTo={redirectTo} />}>
          <Route path="/supplier" element={<p>공급자 전용 화면</p>} />
        </Route>
        <Route path="/" element={<p>홈</p>} />
        <Route path="/login" element={<StateProbe label="로그인 화면" />} />
        <Route path={FORBIDDEN_PATH} element={<StateProbe label="권한 없음 화면" />} />
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

  // allow 는 목록이다. 하나만 비교하는 구현으로 되돌아가면 이 테스트가 잡는다.
  it.each<UserRole>(['BRAND', 'ADMIN'])('여러 Role 을 허용하면 %s 도 통과시킨다', (role) => {
    signInAs(role)
    renderGuardedRoute(['BRAND', 'ADMIN'])
    expect(screen.getByText('공급자 전용 화면')).toBeInTheDocument()
  })

  it('허용 목록에 없는 Role 은 여러 Role 을 허용해도 막는다', () => {
    signInAs('CONSUMER')
    renderGuardedRoute(['BRAND', 'ADMIN'])
    expect(screen.getByText(/권한 없음 화면/)).toBeInTheDocument()
  })

  // 403 성격. 로그인 화면으로 보내면 "로그인했는데 또 로그인" 이 되고, LoginPage 가
  // state.from 으로 되돌려 보내면서 같은 자리를 무한히 오간다.
  it('로그인은 했지만 Role 이 다르면 권한 없음 화면으로 보낸다 (로그인 화면이 아니다)', () => {
    signInAs('CONSUMER')
    renderGuardedRoute(['SUPPLIER'])
    expect(screen.getByText(/권한 없음 화면/)).toBeInTheDocument()
    expect(screen.queryByText(/로그인 화면/)).not.toBeInTheDocument()
  })

  it('권한 없음 안내에 들어가려던 경로와 허용 Role 을 넘긴다', () => {
    signInAs('CONSUMER')
    renderGuardedRoute(['SUPPLIER', 'BRAND'])
    expect(screen.getByText(/권한 없음 화면/)).toHaveTextContent('"pathname":"/supplier"')
    expect(screen.getByText(/권한 없음 화면/)).toHaveTextContent('"allow":["SUPPLIER","BRAND"]')
  })

  it('redirectTo 를 주면 그곳으로 보낸다', () => {
    signInAs('CONSUMER')
    renderGuardedRoute(['SUPPLIER'], '/')
    expect(screen.getByText('홈')).toBeInTheDocument()
  })

  // 401 성격. RoleGuard 를 ProtectedRoute 없이 단독으로 쓴 경우의 방어.
  // 로그인 화면으로 보내야 하며, 권한 없음으로 보내면 로그인할 방법이 사라진다.
  it('로그인하지 않았으면 로그인 화면으로 보내고 원래 위치를 넘긴다', () => {
    renderGuardedRoute(['SUPPLIER'])
    expect(screen.getByText(/로그인 화면/)).toBeInTheDocument()
    expect(screen.getByText(/로그인 화면/)).toHaveTextContent('"pathname":"/supplier"')
    expect(screen.queryByText(/권한 없음 화면/)).not.toBeInTheDocument()
  })

  // 인증은 됐는데 Role 을 아직 모르는 상태(서버 조회로 바뀌면 생긴다).
  // 이때 403 으로 보내면 사용자는 복구할 방법이 없다 — 판정을 미뤄야 한다.
  it('Role 을 아직 모르면 판정을 미루고 로딩을 보여 준다', () => {
    useAuthStore.setState({ user: null, accessToken: 'test-token', isAuthenticated: true })
    renderGuardedRoute(['SUPPLIER'])
    expect(screen.getByRole('status')).toHaveTextContent('권한을 확인하는 중')
    expect(screen.queryByText(/권한 없음 화면/)).not.toBeInTheDocument()
  })

  // 새로고침 직후 403 이 번쩍이지 않는지. authStore 는 zustand persist +
  // localStorage 라 복원이 동기적이고, 첫 렌더 시점에 이미 Role 이 채워져 있다.
  // 저장 방식을 비동기(IndexedDB 등)로 바꾸면 이 테스트가 먼저 깨진다.
  it('persist 복원 직후 첫 렌더에서 바로 통과한다 (403 이 번쩍이지 않는다)', () => {
    // 순서 주의: persist 는 setState 를 감싸 저장까지 하므로, 먼저 비워 두고
    // 그 다음에 저장소를 채워야 한다. 반대로 하면 초기화가 저장값을 덮어쓴다.
    useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
    localStorage.setItem(
      'saeroi.auth',
      JSON.stringify({
        state: {
          user: { id: 1, email: 'a@b.c', name: '테스터', role: 'SUPPLIER', walletAddress: null },
          accessToken: 'test-token',
          refreshToken: 'refresh-token',
        },
        version: 0,
      }),
    )

    // 동기 스토리지이므로 await 없이도 이 호출이 끝나면 상태가 채워져 있어야 한다.
    void useAuthStore.persist.rehydrate()

    renderGuardedRoute(['SUPPLIER'])
    expect(screen.getByText('공급자 전용 화면')).toBeInTheDocument()
  })
})
