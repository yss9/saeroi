import type { UserStatus } from '../../../types/role.types.ts'

export const BUSINESS_ROLES = ['SUPPLIER', 'BRAND', 'PROCESSOR'] as const
export type BusinessUserRole = (typeof BUSINESS_ROLES)[number]

export const BUSINESS_ROLE_LABEL: Record<BusinessUserRole, string> = {
  SUPPLIER: '공급업체',
  BRAND: '브랜드',
  PROCESSOR: '가공업체',
}

export interface CompanyAccountData {
  companyName: string
  email: string
  role: BusinessUserRole
  businessNumber: string | null
  representativeName: string | null
  companyPhone: string | null
  companyAddress: string | null
  status: UserStatus | null
}

export function isBusinessUserRole(role: string): role is BusinessUserRole {
  return (BUSINESS_ROLES as readonly string[]).includes(role)
}
