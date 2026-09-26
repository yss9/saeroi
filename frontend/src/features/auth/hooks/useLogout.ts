import { useMutation } from '@tanstack/react-query'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { AppError } from '../../../utils/errors.ts'
import { logout } from '../api/authApi.ts'

/** 서버의 Refresh Token 무효화가 성공한 뒤에만 로컬 인증 상태를 제거한다. */
export function useLogout() {
  const clearSession = useAuthStore((state) => state.signOut)

  return useMutation<void, AppError>({
    mutationFn: logout,
    onSuccess: clearSession,
  })
}
