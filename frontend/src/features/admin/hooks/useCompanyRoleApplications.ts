import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createAppError, ERROR_KIND, type AppError } from '../../../utils/errors.ts'
import { grantCompanyRoles } from '../../../services/blockchain/adminRoleApproval.ts'
import { approveCompanyRole, getCompanyRoleApplications, rejectCompanyRole } from '../api/companyRoleApprovalApi.ts'
import type { CompanyRoleApplication } from '../types/companyRoleApproval.types.ts'

const key = ['admin', 'company-role-applications'] as const

export function useCompanyRoleApplications() {
  return useQuery<CompanyRoleApplication[], AppError>({ queryKey: key, queryFn: getCompanyRoleApplications })
}

function removeCachedApplication(
  current: CompanyRoleApplication[] = [],
  userId: number,
) {
  return current.filter((application) => application.userId !== userId)
}

export function useApproveCompanyRole() {
  const client = useQueryClient()
  return useMutation<CompanyRoleApplication, AppError, CompanyRoleApplication>({
    mutationFn: async (application) => {
      if (!application.walletAddress) {
        throw createAppError(ERROR_KIND.WALLET_DISCONNECTED, {
          message: '검증된 사업자 지갑이 없습니다.',
        })
      }
      const hashes = await grantCompanyRoles(application.role, application.walletAddress)
      return approveCompanyRole(application.userId, hashes)
    },
    onSuccess: (updated) => {
      client.setQueryData<CompanyRoleApplication[]>(key, (current) => removeCachedApplication(current, updated.userId))
    },
  })
}

export function useRejectCompanyRole() {
  const client = useQueryClient()
  return useMutation<CompanyRoleApplication, AppError, number>({
    mutationFn: rejectCompanyRole,
    onSuccess: (updated) => {
      client.setQueryData<CompanyRoleApplication[]>(key, (current) => removeCachedApplication(current, updated.userId))
    },
  })
}
