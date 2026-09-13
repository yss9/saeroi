import { beforeEach, describe, expect, it } from 'vitest'
import { queryClient } from '../services/queryClient.ts'
import { useAuthStore } from './authStore.ts'

const inventoryKey = ['inventory', 'list'] as const

beforeEach(() => {
  localStorage.clear()
  queryClient.clear()
  useAuthStore.setState({
    user: null,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
  })
})

describe('계정별 서버 캐시', () => {
  it('다른 계정으로 로그인할 때 이전 계정의 보유 소재 캐시를 제거한다', () => {
    queryClient.setQueryData(inventoryKey, [])

    useAuthStore.getState().signIn(
      { id: 2, email: 'branda@test.com', name: 'BrandA', role: 'BRAND', walletAddress: null },
      'brand-token',
      'brand-refresh-token',
    )

    expect(queryClient.getQueryData(inventoryKey)).toBeUndefined()
  })

  it('현재 세션의 Refresh Token과 일치할 때 두 토큰을 함께 교체한다', () => {
    const user = {
      id: 2,
      email: 'branda@test.com',
      name: 'BrandA',
      role: 'BRAND' as const,
      walletAddress: null,
    }
    useAuthStore.getState().signIn(user, 'old-access', 'old-refresh')

    const rotated = useAuthStore
      .getState()
      .rotateTokens('old-refresh', 'new-access', 'new-refresh')

    expect(rotated).toBe(true)
    expect(useAuthStore.getState()).toMatchObject({
      user,
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
      isAuthenticated: true,
    })
  })

  it('다른 세션에서 늦게 도착한 재발급 응답은 반영하지 않는다', () => {
    useAuthStore.getState().signIn(
      { id: 2, email: 'branda@test.com', name: 'BrandA', role: 'BRAND', walletAddress: null },
      'current-access',
      'current-refresh',
    )

    const rotated = useAuthStore
      .getState()
      .rotateTokens('stale-refresh', 'stale-access-2', 'stale-refresh-2')

    expect(rotated).toBe(false)
    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'current-access',
      refreshToken: 'current-refresh',
    })
  })

  it('로그아웃할 때 현재 계정의 서버 캐시를 제거한다', () => {
    queryClient.setQueryData(inventoryKey, [{ materialId: 3 }])

    useAuthStore.getState().signOut()

    expect(queryClient.getQueryData(inventoryKey)).toBeUndefined()
    expect(useAuthStore.getState().refreshToken).toBeNull()
  })
})
