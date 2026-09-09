import { cleanup, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/authStore.ts'
import type { UserRole } from '../../types/role.types.ts'
import { BUSINESS_MENU_ITEMS } from './navigationMenu.ts'
import { useVisibleMenu } from './useVisibleMenu.ts'

function signInAs(role: UserRole) {
  useAuthStore
    .getState()
    .signIn({ id: 1, email: 'a@b.c', name: '테스터', role, walletAddress: null }, 'test-token')
}

beforeEach(() => {
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  localStorage.clear()
})

describe('useVisibleMenu', () => {
  it('로그인한 역할이 들어갈 수 있는 항목만 남긴다', () => {
    signInAs('PROCESSOR')
    const { result } = renderHook(() => useVisibleMenu(BUSINESS_MENU_ITEMS))

    expect(result.current.items.map((item) => item.to)).toEqual([
      '/company/account', '/inventory', '/processor/profile', '/processor/jobs', '/trades',
    ])
    expect(result.current.isResolving).toBe(false)
  })

  it('미인증이면 아무 항목도 남지 않는다', () => {
    const { result } = renderHook(() => useVisibleMenu(BUSINESS_MENU_ITEMS))

    expect(result.current.items).toEqual([])
    expect(result.current.isResolving).toBe(false)
  })

  // 지금 authStore 는 동기 복원이라 이 상태가 나오지 않지만, 역할을 /auth/me 로
  // 비동기 조회하게 되면 이 구간에서 메뉴가 번쩍인다. 미리 판정을 보류한다.
  it('인증됐지만 역할을 모르면 판정을 보류하고 항목을 비운다', () => {
    // user 가 없는 채로 isAuthenticated 만 true — role === null 인 상태
    useAuthStore.setState({ user: null, accessToken: 'test-token', isAuthenticated: true })
    const { result } = renderHook(() => useVisibleMenu(BUSINESS_MENU_ITEMS))

    expect(result.current.isResolving).toBe(true)
    expect(result.current.items).toEqual([])
  })
})
