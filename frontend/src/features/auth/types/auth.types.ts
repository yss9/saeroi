import type { UserRole, UserStatus } from '../../../types/role.types.ts'

/** Backend `users` 테이블에 대응하는 조회 모델. DTO 필드명은 camelCase 기준. */
export interface UserProfile {
  id: number
  email: string
  name: string
  phone: string
  role: UserRole
  status: UserStatus
  createdAt: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  user: UserProfile
}
