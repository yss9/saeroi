import { useMutation } from '@tanstack/react-query'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { AppError } from '../../../utils/errors.ts'
import { login } from '../api/authApi.ts'
import type { LoginRequest, LoginResponse } from '../types/auth.types.ts'

/**
 * 이메일·비밀번호 로그인.
 *
 * 성공 시 authStore 를 채우는 것까지가 이 훅의 책임이다. 화면 이동은 호출부가
 * 결정한다 — 로그인 후 어디로 갈지는 사용자가 어디서 왔느냐에 따라 다르기 때문이다.
 */
export function useLogin() {
  const signIn = useAuthStore((state) => state.signIn)

  return useMutation<LoginResponse, AppError, LoginRequest>({
    mutationFn: login,
    onSuccess: ({ user, accessToken, refreshToken }) => {
      signIn(
        {
          id: user.id,
          email: user.email,
          name: user.name,
          role: user.role,
          // 지갑 주소는 별도 조회/연결 흐름에서 채운다.
          walletAddress: null,
        },
        accessToken,
        refreshToken,
      )
    },
  })
}
