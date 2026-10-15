import type { UserRole } from '../../../types/role.types.ts'

export type CompanyRoleApprovalStatus = 'WAITING_WALLET' | 'PENDING' | 'APPROVED' | 'REJECTED'

export interface CompanyRoleApplication {
  userId: number
  companyName: string
  businessNumber: string
  representativeName: string
  email: string
  role: Extract<UserRole, 'SUPPLIER' | 'BRAND' | 'PROCESSOR'>
  walletAddress: string | null
  walletVerified: boolean
  status: CompanyRoleApprovalStatus
  approvedWallet: string | null
  transactionHashes: string[]
  reviewedAt: string | null
}
