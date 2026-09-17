import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { approveCompanyRole, getCompanyRoleApplications, rejectCompanyRole } from '../api/companyRoleApprovalApi.ts'
import type { CompanyRoleApplication } from '../types/companyRoleApproval.types.ts'

const key = ['admin', 'company-role-applications'] as const

export function useCompanyRoleApplications() {
  return useQuery<CompanyRoleApplication[], AppError>({ queryKey: key, queryFn: getCompanyRoleApplications })
}

function useReviewCompanyRole(review: (userId: number) => Promise<CompanyRoleApplication>) {
  const client = useQueryClient()
  return useMutation<CompanyRoleApplication, AppError, number>({
    mutationFn: review,
    onSuccess: (updated) => {
      client.setQueryData<CompanyRoleApplication[]>(key, (current = []) =>
        current.map((application) => application.userId === updated.userId ? updated : application),
      )
    },
  })
}

export function useApproveCompanyRole() { return useReviewCompanyRole(approveCompanyRole) }
export function useRejectCompanyRole() { return useReviewCompanyRole(rejectCompanyRole) }
