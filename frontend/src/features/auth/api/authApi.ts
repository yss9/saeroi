// auth 도메인의 HTTP 호출.
//
// 이 계층의 규칙:
//   - apiClient 만 사용한다 (axios 를 직접 import 하지 않는다)
//   - React 를 모른다 (훅·컴포넌트 import 금지)
//   - 응답 봉투는 apiClient 인터셉터가 이미 벗겼으므로 알맹이 타입만 다룬다
//
// 이메일 로그인 계약은 Backend EmailAuthController 와 함께 관리한다.
// apiClient 의 baseURL 이 /api 이므로 아래 경로는 실제로 /api/v1/auth/login 을 호출한다.

import { apiClient } from '../../../services/apiClient.ts'
import type { LoginRequest, LoginResponse } from '../types/auth.types.ts'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/v1/auth/login', payload, {
    skipAuth: true,
    skipTokenRefresh: true,
  })
  return data
}
