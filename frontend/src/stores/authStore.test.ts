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
    isAuthenticated: false,
  })
})

describe('계정별 서버 캐시', () => {
  it('다른 계정으로 로그인할 때 이전 계정의 보유 소재 캐시를 제거한다', () => {
    queryClient.setQueryData(inventoryKey, [])

    useAuthStore.getState().signIn(
      { id: 2, email: 'branda@test.com', name: 'BrandA', role: 'BRAND', walletAddress: null },
      'brand-token',
    )

    expect(queryClient.getQueryData(inventoryKey)).toBeUndefined()
  })

  it('재발급을 시작한 세션과 일치할 때 Access Token을 교체한다', () => {
    const user = {
      id: 2,
      email: 'branda@test.com',
      name: 'BrandA',
      role: 'BRAND' as const,
      walletAddress: null,
    }
    useAuthStore.getState().signIn(user, 'old-access')

    const refreshed = useAuthStore
      .getState()
      .refreshAccessToken(user.id, 'old-access', 'new-access')

    expect(refreshed).toBe(true)
    expect(useAuthStore.getState()).toMatchObject({
      user,
      accessToken: 'new-access',
      isAuthenticated: true,
    })
  })

  it('다른 세션에서 늦게 도착한 재발급 응답은 반영하지 않는다', () => {
    useAuthStore.getState().signIn(
      { id: 2, email: 'branda@test.com', name: 'BrandA', role: 'BRAND', walletAddress: null },
      'current-access',
    )

    const refreshed = useAuthStore
      .getState()
      .refreshAccessToken(2, 'stale-access', 'stale-access-2')

    expect(refreshed).toBe(false)
    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'current-access',
    })
  })

  it('Refresh Token을 localStorage에 저장하지 않는다', () => {
    useAuthStore.getState().signIn(
      { id: 2, email: 'branda@test.com', name: 'BrandA', role: 'BRAND', walletAddress: null },
      'access-token',
    )

    const persisted = JSON.parse(localStorage.getItem('saeroi.auth') ?? '{}')
    expect(persisted.state).toEqual({
      user: {
        id: 2,
        email: 'branda@test.com',
        name: 'BrandA',
        role: 'BRAND',
        walletAddress: null,
      },
      accessToken: 'access-token',
    })
  })

  it('로그아웃할 때 현재 계정의 서버 캐시를 제거한다', () => {
    queryClient.setQueryData(inventoryKey, [{ materialId: 3 }])

    useAuthStore.getState().signOut()

    expect(queryClient.getQueryData(inventoryKey)).toBeUndefined()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })
})
