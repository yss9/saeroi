import type { UserRole } from '../../../types/role.types.ts'

/**
 * 로그인 직후 착지하는 곳.
 *
 * **사업자는 회사 정보로 보낸다.** 업무 화면 하나를 골라 보내면 그 역할이 하는 여러 일 중
 * 하나를 임의로 앞세우는 셈이고(브랜드는 소재를 사고, 가공을 맡기고, 제품을 판다), 사이드바가
 * 이미 그 전부를 들고 있다. 계정 화면은 어느 일을 하러 왔든 같은 출발점이다.
 */
const ROLE_HOME_PATH: Record<UserRole, string> = {
  SUPPLIER: '/company/account',
  BRAND: '/company/account',
  PROCESSOR: '/company/account',
  CONSUMER: '/mypage',
  ADMIN: '/admin',
}

export function getRoleHomePath(role: UserRole) {
  return ROLE_HOME_PATH[role]
}
