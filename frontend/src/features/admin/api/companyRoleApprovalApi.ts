import { apiClient } from '../../../services/apiClient.ts'
import type { CompanyRoleApplication } from '../types/companyRoleApproval.types.ts'

const BASE = '/v1/admin/company-role-applications'

export async function getCompanyRoleApplications(): Promise<CompanyRoleApplication[]> {
  const { data } = await apiClient.get<CompanyRoleApplication[]>(BASE)
  return data
}

export async function approveCompanyRole(
  userId: number,
  transactionHashes: string[],
): Promise<CompanyRoleApplication> {
  const { data } = await apiClient.post<CompanyRoleApplication>(`${BASE}/${userId}/approve`, {
    transactionHashes,
  })
  return data
}

export async function rejectCompanyRole(userId: number): Promise<CompanyRoleApplication> {
  const { data } = await apiClient.post<CompanyRoleApplication>(`${BASE}/${userId}/reject`)
  return data
}
