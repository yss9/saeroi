// auth 도메인의 HTTP 호출.
//
// 이 계층의 규칙:
//   - apiClient 만 사용한다 (axios 를 직접 import 하지 않는다)
//   - React 를 모른다 (훅·컴포넌트 import 금지)
//   - 응답 봉투는 apiClient 인터셉터가 이미 벗겼으므로 알맹이 타입만 다룬다
//
// TODO(6번/User·Wallet 담당): 아래 경로는 Backend 구현 전 가정값이다.
// 실제 컨트롤러가 나오면 경로와 DTO 를 맞춘다.
//
// ⚠️ 지갑 로그인 경로는 walletAuthApi.ts 에 따로 있고 이미 '/v1' 로 확정됐다.
// 여기만 무버전인 것은 실수가 아니라 아직 컨트롤러가 없기 때문이다 —
// "통일" 하려고 한쪽만 고치지 말 것. 자세한 이유는 walletAuthApi.ts 주석 참고.

import { apiClient } from '../../../services/apiClient.ts'
import type { LoginRequest, LoginResponse, UserProfile } from '../types/auth.types.ts'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/login', payload)
  return data
}

export async function fetchMe(): Promise<UserProfile> {
  const { data } = await apiClient.get<UserProfile>('/auth/me')
  return data
}
