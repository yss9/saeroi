import { beforeEach, describe, expect, it } from 'vitest'
import { queryClient } from '../services/queryClient.ts'
import { useAuthStore } from './authStore.ts'

const inventoryKey = ['inventory', 'list'] as const

beforeEach(() => {
  localStorage.clear()
  queryClient.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
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

  it('로그아웃할 때 현재 계정의 서버 캐시를 제거한다', () => {
    queryClient.setQueryData(inventoryKey, [{ materialId: 3 }])

    useAuthStore.getState().signOut()

    expect(queryClient.getQueryData(inventoryKey)).toBeUndefined()
  })
})
