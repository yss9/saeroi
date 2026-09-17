import type { ContractName } from '../../config/contracts.ts'
import type { CompanyRoleApplication } from '../../features/admin/types/companyRoleApproval.types.ts'
import { createAppError, ERROR_KIND, isAppError } from '../../utils/errors.ts'
import { getWriteContract } from './contractFactory.ts'
import { toWalletError } from '../../features/wallet/services/walletService.ts'

type BusinessRole = CompanyRoleApplication['role']
type RoleFunction = 'SUPPLIER_ROLE' | 'BRAND_ROLE' | 'PROCESSOR_ROLE'

interface RoleTarget {
  contract: ContractName
  roleFunction: RoleFunction
}

const TARGETS: Record<BusinessRole, RoleTarget[]> = {
  SUPPLIER: [{ contract: 'materialToken', roleFunction: 'SUPPLIER_ROLE' }],
  BRAND: [
    { contract: 'materialToken', roleFunction: 'BRAND_ROLE' },
    { contract: 'processingRegistry', roleFunction: 'BRAND_ROLE' },
    { contract: 'productionRegistry', roleFunction: 'BRAND_ROLE' },
  ],
  PROCESSOR: [
    { contract: 'materialToken', roleFunction: 'PROCESSOR_ROLE' },
    { contract: 'processingRegistry', roleFunction: 'PROCESSOR_ROLE' },
  ],
}

/** 관리자가 MetaMask로 직접 각 AccessControl 컨트랙트의 역할을 부여한다. */
export async function grantCompanyRoles(
  role: BusinessRole,
  companyWallet: string,
): Promise<string[]> {
  const hashes: string[] = []

  try {
    for (const target of TARGETS[role]) {
      // 관리자는 사업자 등록 지갑이 아니라 운영 지갑으로 서명하므로 일반 사용자용
      // "로그인 계정 지갑과 같아야 함" 검사는 끈다. 대신 아래에서 온체인 관리자 권한을 확인한다.
      const contract = await getWriteContract(target.contract, { enforceRegisteredWallet: false })
      const signer = contract.runner
      if (!signer || typeof signer !== 'object' || !('getAddress' in signer)) {
        throw createAppError(ERROR_KIND.WALLET_DISCONNECTED)
      }
      const adminAddress = await (signer as { getAddress(): Promise<string> }).getAddress()
      const adminRole = await contract.DEFAULT_ADMIN_ROLE()
      if (!(await contract.hasRole(adminRole, adminAddress))) {
        throw createAppError(ERROR_KIND.UNKNOWN, {
          message: `현재 MetaMask 계정은 ${target.contract} 컨트랙트의 관리자가 아닙니다.`,
        })
      }

      const roleHash = await contract[target.roleFunction]()
      if (await contract.hasRole(roleHash, companyWallet)) continue

      const transaction = await contract.grantRole(roleHash, companyWallet)
      const receipt = await transaction.wait()
      if (!receipt || receipt.status !== 1) {
        throw createAppError(ERROR_KIND.UNKNOWN, { message: '온체인 역할 부여가 실패했습니다.' })
      }
      hashes.push(transaction.hash)
    }
    return hashes
  } catch (error) {
    if (isAppError(error)) throw error
    throw toWalletError(error)
  }
}
