import { useMutation } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { changePassword, type PasswordChangeRequest } from '../api/changePassword.ts'

/**
 * 비밀번호 변경.
 *
 * **무효화할 캐시가 없다.** 비밀번호는 어떤 조회에도 담기지 않는다 — 바뀌었다는 사실을
 * 화면에 알리는 것이 전부다.
 *
 * 성공한 뒤 로그아웃시키지 않는다. 지금 이 기기의 토큰은 그대로 유효하고, 다른 기기의
 * 세션을 끊을지는 서버가 정할 일이다(리프레시 토큰 무효화). 화면이 임의로 로그아웃시키면
 * 방금 비밀번호를 바꾼 사람이 이유도 모른 채 로그인 화면으로 튕긴다.
 */
export function useChangePassword() {
  return useMutation<void, AppError, PasswordChangeRequest>({ mutationFn: changePassword })
}
