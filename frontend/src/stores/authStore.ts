// 인증 전역 상태.
//
// 여기에는 "로그인한 사람이 누구인가" 만 둔다. 서버에서 조회하는 데이터
// (내 소재 목록, 주문 내역 등)는 절대 여기에 복제하지 않는다 — 그건 TanStack Query 담당이다.

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { queryClient } from '../services/queryClient.ts'
import type { UserRole } from '../types/role.types.ts'

export interface AuthUser {
  id: number
  email: string
  name: string
  role: UserRole
  /** 지갑을 연결·검증한 사용자만 값이 있다. */
  walletAddress: string | null
}

interface AuthState {
  user: AuthUser | null
  /** Backend 가 발급한 JWT Access Token */
  accessToken: string | null
  /** Access Token 재발급에 사용하는 1회성 회전 토큰 */
  refreshToken: string | null
  isAuthenticated: boolean

  signIn: (user: AuthUser, accessToken: string, refreshToken: string) => void
  /** 현재 세션과 일치할 때만 두 토큰을 원자적으로 교체한다. */
  rotateTokens: (
    expectedRefreshToken: string,
    accessToken: string,
    refreshToken: string,
  ) => boolean
  signOut: () => void
  /** 프로필 일부만 갱신 (예: 지갑 연결 직후 walletAddress 반영) */
  patchUser: (patch: Partial<AuthUser>) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,

      signIn: (user, accessToken, refreshToken) => {
        queryClient.clear()
        set({ user, accessToken, refreshToken, isAuthenticated: true })
      },

      rotateTokens: (expectedRefreshToken, accessToken, refreshToken) => {
        const current = get()
        // 재발급 중 사용자가 로그아웃하거나 다른 계정으로 로그인한 경우
        // 늦게 도착한 응답으로 새 세션을 덮어쓰지 않는다.
        if (!current.user || current.refreshToken !== expectedRefreshToken) return false
        set({ accessToken, refreshToken, isAuthenticated: true })
        return true
      },

      signOut: () => {
        queryClient.clear()
        set({ user: null, accessToken: null, refreshToken: null, isAuthenticated: false })
      },

      patchUser: (patch) =>
        set((state) => (state.user ? { user: { ...state.user, ...patch } } : state)),
    }),
    {
      name: 'saeroi.auth',
      // isAuthenticated 는 저장하지 않는다 — user/token 에서 파생되는 값이라
      // 따로 저장하면 셋이 어긋날 수 있다. 복원 후 아래에서 다시 계산한다.
      partialize: (state) => ({
        user: state.user,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
      }),
      onRehydrateStorage: () => (state) => {
        if (!state) return
        const hasCompleteSession = Boolean(state.user && state.accessToken && state.refreshToken)
        state.isAuthenticated = hasCompleteSession
        // Refresh Token 도입 전 localStorage 에 남은 불완전한 세션은 폐기한다.
        if (!hasCompleteSession) {
          state.user = null
          state.accessToken = null
          state.refreshToken = null
        }
      },
    },
  ),
)

/**
 * React 밖(axios 인터셉터 등)에서 현재 토큰을 읽는다.
 *
 * TODO(보안): 지금은 Backend 계약에 따라 두 토큰을 localStorage 에 둔다.
 * XSS 에 노출되는 방식이므로 향후 Refresh Token 을 httpOnly 쿠키로 옮기는 것이 필요하다.
 */
export function getAccessToken(): string | null {
  return useAuthStore.getState().accessToken
}

export function getRefreshToken(): string | null {
  return useAuthStore.getState().refreshToken
}
