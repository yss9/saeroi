import { apiClient } from '../../../services/apiClient.ts'
import type { UserStatus } from '../../../types/role.types.ts'
import type { BusinessUserRole } from '../types/companyAccount.types.ts'

/**
 * `GET /v1/companies/me` 응답. Backend `CompanyProfileResponse` 와 1:1 로 대응한다.
 *
 * `representativeName`/`companyPhone` 은 `company_profile` 이 아니라 `users.name`/`users.phone`
 * 에서 온다 — 가입 시 대표자 정보로 채워지기 때문이다(Backend `CompanyProfileService` 문서 참고).
 * `companyName`/`businessNumber` 는 사업자 등록 시 확정되는 식별 정보라 수정 대상이 아니다.
 */
export interface CompanyProfile {
  userId: number
  companyName: string
  businessNumber: string
  representativeName: string
  companyPhone: string
  zipCode: string
  address: string
  addressDetail: string | null
  introduction: string | null
  logoUrl: string | null
  role: BusinessUserRole
  status: UserStatus
  roleApprovalStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
}

/**
 * 인증된 사업자 계정 본인의 업체 정보를 조회한다.
 *
 * 개인(CONSUMER) 계정이나 업체 정보가 없는 계정으로 호출하면 각각
 * `COMPANY_PROFILE_FORBIDDEN`(403)/`COMPANY_PROFILE_NOT_FOUND`(404) 로 실패한다 —
 * 호출부는 `isBusinessUserRole` 로 먼저 걸러 전자를 막고, 후자는 조회 실패로 다룬다.
 */
export async function getCompanyProfile(): Promise<CompanyProfile> {
  const { data } = await apiClient.get<CompanyProfile>('/v1/companies/me')
  return data
}
