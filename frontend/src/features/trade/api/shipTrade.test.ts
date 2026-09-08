// 발송 요청 payload 정리.
//
// **빈 문자열을 보내면 서버가 500 을 낸다.** `TradeShipping.registerShipment` 가
// `requireOptionalText` 로 값을 검사하는데, null 은 통과시키지만 blank 는
// `IllegalArgumentException` 을 던진다 — 그 예외는 도메인 에러가 아니라서 공통 핸들러가
// 500 으로 내보낸다. 폼의 빈 입력은 `''` 이므로 이 정리가 없으면 송장 없이 발송하는
// 정상 경로가 매번 500 이 된다.
//
// 모킹 경계는 apiClient 다 — 여기서 보는 것이 "무엇을 보내는가" 그 자체이기 때문이다.

import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { shipTrade } from './shipTrade.ts'

vi.mock('../../../services/apiClient.ts', () => ({ apiClient: { patch: vi.fn() } }))

afterEach(() => vi.resetAllMocks())

describe('shipTrade', () => {
  it('빈 값은 필드를 아예 보내지 않는다', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: null })

    await shipTrade(7, { carrier: '', trackingNumber: '' })

    expect(apiClient.patch).toHaveBeenCalledWith('/v1/trades/7/ship', {})
  })

  it('공백만 입력한 값도 보내지 않는다', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: null })

    await shipTrade(7, { carrier: '   ', trackingNumber: '  ' })

    expect(apiClient.patch).toHaveBeenCalledWith('/v1/trades/7/ship', {})
  })

  it('입력한 값은 앞뒤 공백을 지워 보낸다', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: null })

    await shipTrade(7, { carrier: ' CJ대한통운 ', trackingNumber: ' 123456789012 ' })

    expect(apiClient.patch).toHaveBeenCalledWith('/v1/trades/7/ship', {
      carrier: 'CJ대한통운',
      trackingNumber: '123456789012',
    })
  })

  it('택배사만 있는 경우도 보낼 수 있다 — 송장 번호 없는 전달이다', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: null })

    await shipTrade(7, { carrier: 'CJ대한통운' })

    expect(apiClient.patch).toHaveBeenCalledWith('/v1/trades/7/ship', { carrier: 'CJ대한통운' })
  })
})
