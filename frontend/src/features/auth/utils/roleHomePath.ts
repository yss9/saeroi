import type { UserRole } from '../../../types/role.types.ts'

const ROLE_HOME_PATH: Record<UserRole, string> = {
  SUPPLIER: '/supplier/listings',
  BRAND: '/brand/processing',
  PROCESSOR: '/processor/jobs',
  CONSUMER: '/mypage',
  ADMIN: '/admin',
}

export function getRoleHomePath(role: UserRole) {
  return ROLE_HOME_PATH[role]
}
