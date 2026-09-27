import { id } from 'ethers'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const wallet = vi.hoisted(() => ({ getWalletAddress: vi.fn() }))
const contracts = vi.hoisted(() => ({ hasRole: vi.fn(), availableBalance: vi.fn() }))

vi.mock('../../wallet/services/walletService.ts', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  getWalletAddress: wallet.getWalletAddress,
}))
vi.mock('../../../services/blockchain/contractFactory.ts', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  getReadContract: (name: string) => name === 'materialToken'
    ? { availableBalance: contracts.availableBalance }
    : { hasRole: contracts.hasRole },
}))

import { assertCanRegisterProcessingJob } from './processingRegistrationPreflight.ts'

const BRAND = '0x1111111111111111111111111111111111111111'
const PROCESSOR = '0x2222222222222222222222222222222222222222'
const preparation = { brandWallet: BRAND, processorWallet: PROCESSOR, inputTokenId: 7, inputQuantity: 12_500 }

/*
  가공 작업 등록이 "간헐적으로" 실패하던 이유는 모두 서명 창을 열기 전에 알 수 있는 것이었다 —
  다른 지갑이 선택돼 있거나, 권한이 없거나, 체인에 남은 수량이 모자라거나. 그 상태로 보내면
  ethers 가 estimateGas 원문(execution reverted (unknown custom error) …)을 던지고, 사용자는
  무엇을 해야 할지 모른다. 서명 전에 체인을 읽어 할 일을 문장으로 알려준다.
*/
describe('assertCanRegisterProcessingJob', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    wallet.getWalletAddress.mockResolvedValue(BRAND)
    contracts.hasRole.mockResolvedValue(true)
    contracts.availableBalance.mockResolvedValue(20_000n)
  })

  it('모든 조건이 맞으면 통과한다', async () => {
    await expect(assertCanRegisterProcessingJob(preparation)).resolves.toBeUndefined()
    expect(contracts.hasRole).toHaveBeenCalledWith(id('BRAND_ROLE'), BRAND)
    expect(contracts.hasRole).toHaveBeenCalledWith(id('PROCESSOR_ROLE'), PROCESSOR)
    expect(contracts.availableBalance).toHaveBeenCalledWith(BRAND, 7)
  })

  it('다른 지갑이 선택돼 있으면 브랜드 지갑으로 바꾸라고 한다', async () => {
    wallet.getWalletAddress.mockResolvedValue('0x6d76C605791E873F5d2a769F12a13AbC00B1083C')

    await expect(assertCanRegisterProcessingJob(preparation)).rejects.toThrow(
      'MetaMask 에서 이 브랜드의 지갑(0x1111…1111)을 선택한 뒤 다시 시도해 주세요. 지금 선택된 지갑은 0x6d76…083C 입니다.',
    )
  })

  it('주소의 대소문자는 가리지 않는다', async () => {
    wallet.getWalletAddress.mockResolvedValue(BRAND.toUpperCase().replace('0X', '0x'))

    await expect(assertCanRegisterProcessingJob(preparation)).resolves.toBeUndefined()
  })

  it('브랜드 권한이 없는 지갑이면 그 사실을 말한다', async () => {
    contracts.hasRole.mockImplementation(async (role: string) => role !== id('BRAND_ROLE'))

    await expect(assertCanRegisterProcessingJob(preparation)).rejects.toThrow(
      '이 지갑에는 블록체인에서 가공을 맡길 브랜드 권한이 없습니다. 관리자에게 권한 부여를 요청해 주세요.',
    )
  })

  it('가공업체 지갑에 권한이 없으면 그 사실을 말한다', async () => {
    contracts.hasRole.mockImplementation(async (role: string) => role !== id('PROCESSOR_ROLE'))

    await expect(assertCanRegisterProcessingJob(preparation)).rejects.toThrow(
      '가공업체 지갑에 블록체인 가공 권한이 없어 등록할 수 없습니다. 관리자에게 가공업체 권한 부여를 요청해 주세요.',
    )
  })

  it('체인에 남은 수량이 모자라면 두 수량을 함께 보여준다', async () => {
    contracts.availableBalance.mockResolvedValue(10_000n)

    await expect(assertCanRegisterProcessingJob(preparation)).rejects.toThrow(
      '블록체인에 남은 이 소재의 가용 수량(10,000 g)이 가공 수량(12,500 g)보다 적습니다. 판매·생산에 잠긴 수량이나 아직 체인에 반영되지 않은 거래가 있는지 확인해 주세요.',
    )
  })

  // 읽기 실패로 서명을 막지 않는다 — 조건이 정말 틀렸다면 컨트랙트가 되돌린다.
  it('체인을 읽지 못하면 막지 않고 넘긴다', async () => {
    contracts.hasRole.mockRejectedValue(new Error('network down'))
    contracts.availableBalance.mockRejectedValue(new Error('network down'))

    await expect(assertCanRegisterProcessingJob(preparation)).resolves.toBeUndefined()
  })

  it('지갑이 아직 연결되지 않았으면 지갑 확인은 서명 단계에 맡긴다', async () => {
    wallet.getWalletAddress.mockResolvedValue(null)

    await expect(assertCanRegisterProcessingJob(preparation)).resolves.toBeUndefined()
    expect(contracts.availableBalance).not.toHaveBeenCalled()
  })
})
