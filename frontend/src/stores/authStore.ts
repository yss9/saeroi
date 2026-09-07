// 인증 전역 상태.
//
// 여기에는 "로그인한 사람이 누구인가" 만 둔다. 서버에서 조회하는 데이터
// (내 소재 목록, 주문 내역 등)는 절대 여기에 복제하지 않는다 — 그건 TanStack Query 담당이다.

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
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
  signOut: () => void
  /** 프로필 일부만 갱신 (예: 지갑 연결 직후 walletAddress 반영) */
  patchUser: (patch: Partial<AuthUser>) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      isAuthenticated: false,

      signIn: (user, accessToken) => set({ user, accessToken, isAuthenticated: true }),

      signOut: () => set({ user: null, accessToken: null, isAuthenticated: false }),

      patchUser: (patch) =>
        set((state) => (state.user ? { user: { ...state.user, ...patch } } : state)),
    }),
    {
      name: 'saeroi.auth',
      // isAuthenticated 는 저장하지 않는다 — user/token 에서 파생되는 값이라
      // 따로 저장하면 셋이 어긋날 수 있다. 복원 후 아래에서 다시 계산한다.
      partialize: (state) => ({ user: state.user, accessToken: state.accessToken }),
      onRehydrateStorage: () => (state) => {
        if (state) state.isAuthenticated = Boolean(state.accessToken && state.user)
      },
    },
  ),
)

/**
 * React 밖(axios 인터셉터 등)에서 현재 토큰을 읽는다.
 *
 * TODO(보안): 지금은 localStorage 에 토큰을 둔다. XSS 에 노출되는 방식이므로,
 * Backend 가 httpOnly 쿠키 + Refresh Token 을 지원하면 그쪽으로 옮긴다.
 * (backend schema 에 refresh_token 테이블이 이미 잡혀 있다.)
 */
export function getAccessToken(): string | null {
  return useAuthStore.getState().accessToken
}
