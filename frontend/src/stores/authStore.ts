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
  isAuthenticated: boolean

  signIn: (user: AuthUser, accessToken: string) => void
  /** 재발급을 시작한 세션과 일치할 때만 Access Token을 교체한다. */
  refreshAccessToken: (
    expectedUserId: number,
    expectedAccessToken: string,
    accessToken: string,
  ) => boolean
  /**
   * 다른 창이 재발급해 온 Access Token 을 받아들인다.
   *
   * `refreshAccessToken` 과 달리 이전 토큰을 대조하지 않는다 — 다른 창이 회전시킨 뒤라
   * 이 창이 들고 있던 값과 다른 것이 정상이기 때문이다. 대신 <b>같은 사용자</b>인지는
   * 확인한다. 그 사이 다른 계정으로 로그인했다면 새 세션을 덮어쓰면 안 된다.
   */
  adoptAccessToken: (expectedUserId: number, accessToken: string) => boolean
  signOut: () => void
  /** 프로필 일부만 갱신 (예: 지갑 연결 직후 walletAddress 반영) */
  patchUser: (patch: Partial<AuthUser>) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      isAuthenticated: false,

      signIn: (user, accessToken) => {
        queryClient.clear()
        set({ user, accessToken, isAuthenticated: true })
      },

      refreshAccessToken: (expectedUserId, expectedAccessToken, accessToken) => {
        const current = get()
        // 재발급 중 사용자가 로그아웃하거나 다른 계정으로 로그인한 경우
        // 늦게 도착한 응답으로 새 세션을 덮어쓰지 않는다.
        if (current.user?.id !== expectedUserId || current.accessToken !== expectedAccessToken) {
          return false
        }
        set({ accessToken, isAuthenticated: true })
        return true
      },

      adoptAccessToken: (expectedUserId, accessToken) => {
        const current = get()
        if (current.user?.id !== expectedUserId) {
          return false
        }
        set({ accessToken, isAuthenticated: true })
        return true
      },

      signOut: () => {
        queryClient.clear()
        set({ user: null, accessToken: null, isAuthenticated: false })
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
      }),
      version: 1,
      // 쿠키 방식 전환 전에 저장된 Refresh Token을 localStorage에서 제거한다.
      migrate: (persistedState) => {
        const state = persistedState as Partial<Pick<AuthState, 'user' | 'accessToken'>>
        return { user: state.user ?? null, accessToken: state.accessToken ?? null }
      },
      onRehydrateStorage: () => (state) => {
        if (!state) return
        const hasCompleteSession = Boolean(state.user && state.accessToken)
        state.isAuthenticated = hasCompleteSession
        if (!hasCompleteSession) {
          state.user = null
          state.accessToken = null
        }
      },
    },
  ),
)

/**
 * React 밖(axios 인터셉터 등)에서 현재 토큰을 읽는다.
 *
 * Refresh Token은 Backend가 HttpOnly 쿠키로 관리하므로 JavaScript에서 읽지 않는다.
 */
export function getAccessToken(): string | null {
  return useAuthStore.getState().accessToken
}
