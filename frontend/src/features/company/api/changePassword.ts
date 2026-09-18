import { apiClient } from '../../../services/apiClient.ts'

/**
 * `PUT /v1/users/me/password` 요청.
 *
 * ⚠️ **아직 서버에 없는 경로다.** 화면을 먼저 만들었고, 백엔드에 다음 계약으로 요청해
 * 두었다 — 그때까지 저장을 누르면 404 가 난다.
 *
 * | 값 | 뜻 |
 * | --- | --- |
 * | `currentPassword` | 지금 쓰는 비밀번호. 본인 확인이다 — 이것 없이 바꾸게 두면 잠깐 자리를 비운 사이 누구나 계정을 가져간다 |
 * | `newPassword` | 새 비밀번호. 규칙은 가입과 같다 |
 *
 * 서버가 구분해 주어야 하는 실패가 둘 있다.
 *
 * - 현재 비밀번호가 틀림 → 그 칸만 고치면 되는 일이라 별도 코드가 필요하다
 * - 비밀번호가 없는 계정(지갑·소셜 가입) → 바꿀 것이 없다는 뜻이라 다른 안내를 해야 한다
 *
 * 보유자를 파라미터로 받지 않는다. 서버가 JWT `sub` 로 판단한다 — 아이디를 받으면 남의
 * 비밀번호를 바꿀 수 있다 (`updateCompanyProfile` 과 같은 이유).
 */
export interface PasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

export async function changePassword(request: PasswordChangeRequest): Promise<void> {
  await apiClient.put('/v1/users/me/password', request)
}
