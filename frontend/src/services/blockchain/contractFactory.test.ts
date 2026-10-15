import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/authStore.ts'
import { ERROR_KIND, isAppError } from '../../utils/errors.ts'
import { getWriteContract } from './contractFactory.ts'

/**
 * <b>쓰기 서명은 두 가지를 먼저 확인해야 한다 — 어느 체인인가, 어느 계정인가.</b>
 *
 * 둘 다 화면이 아니라 여기서 본다. 패널마다 두면 새 패널이 생길 때 빠지고, 빠진 것은
 * 되돌릴 수 없는 트랜잭션으로 드러난다.
 *
 * 계정 검사가 필요한 이유 — MetaMask 는 계정을 여러 개 들고 있고 사용자는 아무 때나
 * 바꾼다. 등록 지갑이 아닌 계정으로 서명하면 체인에는 다른 주소가 남는데, 서버는 거래
 * 시점의 지갑을 스냅샷으로 들고 있어 두 기록이 서로 다른 사람을 가리키게 된다. 그 뒤
 * {@code markShipped}·{@code confirmReceipt} 는 컨트랙트가 order.seller/buyer 를
 * 대조하다 되돌린다. 소재 발행이라면 토큰이 엉뚱한 지갑으로 가고 되돌릴 수 없다.
 */
const mocks = vi.hoisted(() => ({
  chainIdHex: '0x79f5' as string | null,
  signerAddress: '0xc62aF11C2f7F5fbB39253e6ef7bDdd65BAda06Dd',
  networkChainId: 31221n,
}))

vi.mock('../../config/env.ts', () => ({
  env: {
    get besuChainIdHex() {
      return mocks.chainIdHex
    },
  },
}))

vi.mock('../../config/contracts.ts', () => ({
  contracts: { marketplace: '0x53Fc91Be37720Fcb051004B7aab73639CA266853' },
  abis: { marketplace: ['function cancelOrder(uint256 orderId)'] },
}))

vi.mock('../../features/wallet/services/walletService.ts', () => ({
  getBrowserProvider: () => ({
    getNetwork: () => Promise.resolve({ chainId: mocks.networkChainId }),
  }),
  getSigner: () =>
    Promise.resolve({
      getAddress: () => Promise.resolve(mocks.signerAddress),
      provider: null,
    }),
}))

function setRegisteredWallet(walletAddress: string | null) {
  useAuthStore.setState({
    user: { id: 1, name: '브랜드', email: 'b@saeroi.test', role: 'BRAND', walletAddress },
    accessToken: 'token',
    isAuthenticated: true,
  })
}

beforeEach(() => {
  mocks.chainIdHex = '0x79f5'
  mocks.networkChainId = 31221n
  mocks.signerAddress = '0xc62aF11C2f7F5fbB39253e6ef7bDdd65BAda06Dd'
})

afterEach(() => {
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

describe('getWriteContract', () => {
  it('등록 지갑과 다른 계정으로는 서명시키지 않는다', async () => {
    setRegisteredWallet('0x3cdA1c35B5F84c71C3Bc2A6c1A3C3aFC5D1C5F04')

    const error = await getWriteContract('marketplace').catch((e: unknown) => e)

    expect(isAppError(error)).toBe(true)
    expect(isAppError(error) && error.message).toMatch(/등록된 지갑/)
  })

  it('대소문자만 다른 같은 주소는 같은 것으로 본다', async () => {
    // EIP-55 체크섬 표기와 소문자 표기가 섞여 돌아다닌다. 문자열 비교로 두면
    // 같은 지갑인데 서명이 막힌다.
    setRegisteredWallet(mocks.signerAddress.toLowerCase())

    await expect(getWriteContract('marketplace')).resolves.toBeDefined()
  })

  it('등록된 지갑이 없으면 막지 않는다', async () => {
    // 지갑 등록 전이면 비교할 대상이 없다. 여기서 막으면 등록 자체를 할 수 없다.
    setRegisteredWallet(null)

    await expect(getWriteContract('marketplace')).resolves.toBeDefined()
  })

  it('다른 네트워크에서는 서명시키지 않는다', async () => {
    // 헤더의 네트워크 경고만 믿으면, 경고를 못 본 사용자가 다른 체인의 같은 주소로
    // 서명할 수 있다.
    mocks.networkChainId = 1n
    setRegisteredWallet(mocks.signerAddress)

    const error = await getWriteContract('marketplace').catch((e: unknown) => e)

    expect(isAppError(error) && error.kind).toBe(ERROR_KIND.WRONG_NETWORK)
  })
})
