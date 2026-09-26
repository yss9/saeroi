import { beforeEach, describe, expect, it, vi } from 'vitest'
import { grantCompanyRoles } from './adminRoleApproval.ts'

const factory = vi.hoisted(() => ({ getWriteContract: vi.fn() }))
vi.mock('./contractFactory.ts', () => factory)

function contract(
  roleFunction: 'BRAND_ROLE' | 'PROCESSOR_ROLE' | 'SUPPLIER_ROLE',
  hash: string,
  companyAlreadyHasRole = false,
) {
  const hasRole = vi.fn()
    .mockResolvedValueOnce(true)
    .mockResolvedValueOnce(companyAlreadyHasRole)
  const grantRole = vi.fn().mockResolvedValue({
    hash,
    wait: vi.fn().mockResolvedValue({ status: 1 }),
  })
  return {
    runner: { getAddress: vi.fn().mockResolvedValue('0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa') },
    DEFAULT_ADMIN_ROLE: vi.fn().mockResolvedValue('0xadmin'),
    [roleFunction]: vi.fn().mockResolvedValue(`0x${roleFunction}`),
    hasRole,
    grantRole,
  }
}

describe('grantCompanyRoles', () => {
  beforeEach(() => vi.clearAllMocks())

  it('BRAND 역할을 세 컨트랙트에 관리자 MetaMask로 부여한다', async () => {
    const material = contract('BRAND_ROLE', '0x1')
    const processing = contract('BRAND_ROLE', '0x2')
    const production = contract('BRAND_ROLE', '0x3')
    factory.getWriteContract
      .mockResolvedValueOnce(material)
      .mockResolvedValueOnce(processing)
      .mockResolvedValueOnce(production)

    const wallet = '0x1111111111111111111111111111111111111111'
    await expect(grantCompanyRoles('BRAND', wallet)).resolves.toEqual(['0x1', '0x2', '0x3'])
    expect(factory.getWriteContract).toHaveBeenNthCalledWith(
      1, 'materialToken', { enforceRegisteredWallet: false },
    )
    expect(factory.getWriteContract).toHaveBeenNthCalledWith(
      2, 'processingRegistry', { enforceRegisteredWallet: false },
    )
    expect(factory.getWriteContract).toHaveBeenNthCalledWith(
      3, 'productionRegistry', { enforceRegisteredWallet: false },
    )
    expect(material.grantRole).toHaveBeenCalledWith('0xBRAND_ROLE', wallet)
  })

  it('재시도할 때 이미 부여된 역할은 다시 전송하지 않는다', async () => {
    const material = contract('SUPPLIER_ROLE', '0x1', true)
    factory.getWriteContract.mockResolvedValue(material)

    await expect(grantCompanyRoles(
      'SUPPLIER', '0x1111111111111111111111111111111111111111',
    )).resolves.toEqual([])
    expect(material.grantRole).not.toHaveBeenCalled()
  })

  it('현재 MetaMask 계정에 컨트랙트 관리자 권한이 없으면 중단한다', async () => {
    const material = contract('PROCESSOR_ROLE', '0x1')
    material.hasRole.mockReset().mockResolvedValue(false)
    factory.getWriteContract.mockResolvedValue(material)

    await expect(grantCompanyRoles(
      'PROCESSOR', '0x1111111111111111111111111111111111111111',
    )).rejects.toMatchObject({ message: expect.stringContaining('관리자가 아닙니다') })
    expect(material.grantRole).not.toHaveBeenCalled()
  })
})
