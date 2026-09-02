// 사용자 Role. Backend `users.role` ENUM 과 문자열이 정확히 일치해야 한다.
// (backend: src/main/resources/schema.sql — ENUM('SUPPLIER','BRAND','PROCESSOR','CONSUMER','ADMIN'))
//
// TS `enum` 을 쓰지 않는다: tsconfig.app.json 의 `erasableSyntaxOnly: true` 가
// 런타임 코드를 만들어내는 enum 을 금지한다. 문자열 유니온 + const 배열로 대신한다.

export const USER_ROLES = ['SUPPLIER', 'BRAND', 'PROCESSOR', 'CONSUMER', 'ADMIN'] as const

export type UserRole = (typeof USER_ROLES)[number]

/** Backend `users.status` ENUM */
export type UserStatus = 'ACTIVE' | 'SUSPENDED'

/** 화면 표시용 한글 라벨. 로직에서 이 문자열로 분기하지 말 것. */
export const ROLE_LABEL: Record<UserRole, string> = {
  SUPPLIER: '공급자',
  BRAND: '브랜드',
  PROCESSOR: '가공업체',
  CONSUMER: '소비자',
  ADMIN: '관리자',
}

export function isUserRole(value: unknown): value is UserRole {
  return typeof value === 'string' && (USER_ROLES as readonly string[]).includes(value)
}
