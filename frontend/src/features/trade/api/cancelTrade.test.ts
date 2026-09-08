// 거래 취소 payload.
//
// 사유는 선택이고 **없으면 바디를 아예 보내지 않는다.** 서버가 빈 문자열을 null 로
// 정규화하므로 보내도 결과는 같지만, 요청 모양이 "사유 없는 취소" 를 그대로 말해야 한다.
// 기존 호출부(usePayment.cancel)가 인자 없이 부르고 있어 그 경로가 깨지지 않는 것도
// 여기서 고정한다.

import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { cancelTrade } from './paymentApi.ts'

vi.mock('../../../services/apiClient.ts', () => ({ apiClient: { post: vi.fn() } }))

afterEach(() => vi.resetAllMocks())

describe('cancelTrade', () => {
  it('사유가 없으면 바디를 보내지 않는다', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: null })

    await cancelTrade(11)

    expect(apiClient.post).toHaveBeenCalledWith('/v1/trades/11/cancel', undefined)
  })

  it('공백만 입력한 사유도 보내지 않는다', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: null })

    await cancelTrade(11, '   ')

    expect(apiClient.post).toHaveBeenCalledWith('/v1/trades/11/cancel', undefined)
  })

  it('사유를 적으면 앞뒤 공백을 지워 보낸다', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: null })

    await cancelTrade(11, '  재고 부족으로 발송이 어렵습니다  ')

    expect(apiClient.post).toHaveBeenCalledWith('/v1/trades/11/cancel', {
      reason: '재고 부족으로 발송이 어렵습니다',
    })
  })
})
