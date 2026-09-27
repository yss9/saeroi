import { beforeEach, describe, expect, it, vi } from 'vitest'

const chain = vi.hoisted(() => ({ getProcessingChainStatus: vi.fn() }))
vi.mock('./processingContractService.ts', () => chain)

import { chainAlreadyPassed } from './processingChainProgress.ts'

/**
 * 결과 승인이 체인에서 **이미 끝났는가.** 끝났으면 다시 서명하지 않고 서버가 그 거래를 찾게 한다.
 *
 * 번호 크기로 비교하지 않는다 — 반려(8)나 반환 발송(11)은 승인(7)보다 번호가 크지만 승인이
 * 일어난 적 없다. "지났다" 고 잘못 답하면 서명 없이 서버만 부르게 되고, 서버는 체인에서 승인 거래를
 * 찾지 못해 거절한다. 담당자는 서명할 기회도 없이 오류를 본다.
 */
describe('chainAlreadyPassed CONFIRMED', () => {
  beforeEach(() => vi.clearAllMocks())

  it('승인된 작업이면 지났다', async () => {
    chain.getProcessingChainStatus.mockResolvedValue(7)
    await expect(chainAlreadyPassed(31, 'CONFIRMED')).resolves.toBe(true)
  })

  it.each([
    [11, '반환 발송'],
    [8, '결과 반려'],
    [12, '반려 소재 반환'],
  ])('%i(%s)는 번호가 커도 승인이 아니다', async (status) => {
    chain.getProcessingChainStatus.mockResolvedValue(status)
    await expect(chainAlreadyPassed(31, 'CONFIRMED')).resolves.toBe(false)
  })

  it('체인을 못 읽으면 지나지 않은 것으로 본다 — 서명을 막지 않는다', async () => {
    chain.getProcessingChainStatus.mockRejectedValue(new Error('rpc down'))
    await expect(chainAlreadyPassed(31, 'CONFIRMED')).resolves.toBe(false)
  })
})
