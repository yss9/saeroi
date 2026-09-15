import type { UserRole } from '../../../types/role.types.ts'

/**
 * 로그인 직후 착지하는 곳.
 *
 * 헤더의 사람 버튼(`ROLE_HOME_MENU_ITEMS`)과 다르다 — 그쪽은 "내 계정" 이고, 여기는
 * **방금 로그인한 사람이 하려던 일**이다. 사업자는 대부분 업무를 하러 들어오므로 각
 * 역할의 주요 업무 화면으로 보낸다.
 */
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
