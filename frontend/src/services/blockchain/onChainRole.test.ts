import { id as keccakUtf8 } from 'ethers'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { isAppError } from '../../utils/errors.ts'
import { requireOnChainRole } from './onChainRole.ts'

/**
 * <b>앱의 역할과 온체인 역할은 다른 것이다.</b>
 *
 * 앱의 역할은 회원가입으로 정해지지만 온체인 역할은 지갑 주소마다 관리자가 따로 부여한다.
 * BRAND 로 가입한 사용자의 지갑에 {@code BRAND_ROLE} 이 없으면 구매 서명이
 * {@code Marketplace: buyer role required} 로 되돌려지는데, 사용자는 자기가 무엇을
 * 잘못했는지 알 수 없고 사실 잘못한 것도 없다 — 관리자가 해 줘야 하는 일이다.
 */
const mocks = vi.hoisted(() => ({
  granted: true,
  hasRole: vi.fn(),
  throwOnRead: false,
}))

vi.mock('./contractFactory.ts', () => ({
  getReadContract: () => {
    if (mocks.throwOnRead) throw new Error('RPC down')
    return { hasRole: mocks.hasRole }
  },
}))

vi.mock('../../features/wallet/services/walletService.ts', () => ({
  getSigner: () =>
    Promise.resolve({
      getAddress: () => Promise.resolve('0xc62aF11C2f7F5fbB39253e6ef7bDdd65BAda06Dd'),
    }),
}))

beforeEach(() => {
  mocks.granted = true
  mocks.throwOnRead = false
  mocks.hasRole.mockImplementation(() => Promise.resolve(mocks.granted))
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('requireOnChainRole', () => {
  it('권한이 있으면 통과시킨다', async () => {
    mocks.granted = true

    await expect(requireOnChainRole('BRAND_ROLE')).resolves.toBeUndefined()
  })

  it('권한이 없으면 서명 전에 사람이 읽을 수 있는 말로 막는다', async () => {
    mocks.granted = false

    const error = await requireOnChainRole('BRAND_ROLE').catch((e: unknown) => e)

    expect(isAppError(error)).toBe(true)
    // 사용자가 고칠 수 있는 일이 아니라는 것과, 누구에게 요청해야 하는지가 있어야 한다.
    expect(isAppError(error) && error.message).toMatch(/브랜드 권한이 없습니다/)
    expect(isAppError(error) && error.message).toMatch(/관리자|담당자/)
  })

  it('확인할 수 없으면 막지 않는다', async () => {
    // RPC 실패로 서명을 막으면, 정작 권한이 있는 사용자가 우리 쪽 사정으로 막힌다.
    // 판단은 컨트랙트에 맡긴다 — 그쪽이 정본이고 최악이라도 지금과 같은 revert 다.
    mocks.throwOnRead = true

    await expect(requireOnChainRole('SUPPLIER_ROLE')).resolves.toBeUndefined()
  })

  it('배포된 컨트랙트와 같은 역할 해시를 쓴다', async () => {
    // Solidity 의 keccak256("SUPPLIER_ROLE") 과 같아야 한다. 어긋나면 hasRole 이
    // 존재하지 않는 역할을 물어보게 되어 **항상 false** 가 되고, 권한이 있는 모두가 막힌다.
    // 값은 2026-09-09 에 SSAFY Besu 의 MaterialToken 에서 직접 읽어 확인했다.
    mocks.granted = true
    await requireOnChainRole('SUPPLIER_ROLE')

    expect(mocks.hasRole).toHaveBeenCalledWith(
      '0xd1ae8bbdabd60d63e418b84f5ad6f9cba90092c9816d7724d85f0d4e4bea2c60',
      '0xc62aF11C2f7F5fbB39253e6ef7bDdd65BAda06Dd',
    )
    expect(keccakUtf8('SUPPLIER_ROLE')).toBe(
      '0xd1ae8bbdabd60d63e418b84f5ad6f9cba90092c9816d7724d85f0d4e4bea2c60',
    )
    expect(keccakUtf8('BRAND_ROLE')).toBe(
      '0xd52b9622fe3ce468afb95246111fe2ff28de74ab22e427623dca2ad2172e5f59',
    )
    expect(keccakUtf8('PROCESSOR_ROLE')).toBe(
      '0xe61decff6e4a5c6b5a3d3cbd28f882e595173563b49353ce5f31dba2de7f05ee',
    )
  })
})
