import { apiClient } from '../../../services/apiClient.ts'
import type { CreateTradeRequest, Trade } from '../types/trade.types.ts'

/**
 * 구매 요청 (Brand Market).
 *
 * 성공하면 그 수량은 **즉시 판매글에서 빠진다** — 다른 구매자가 같은 수량을 살 수 없다
 * (정책 07.2). 아직 결제 전이며 상태는 CREATED 다.
 *
 * 결제 전인데 수량을 먼저 줄이는 이유는, 결제창에 머무는 동안 다른 사람이 사가면
 * 결제를 마치고 돌아온 사용자에게 없는 물건을 팔게 되기 때문이다. 그래서 **이탈한 거래를
 * 반드시 취소로 정리해야 한다** — 안 하면 그만큼의 판매 수량이 영구히 묶인다.
 *
 * 수량 검증은 서버가 행 잠금으로 한다. 화면 검증은 사용자에게 빨리 알려주기 위한 것일 뿐,
 * 동시 구매를 막지 못한다.
 */
export async function createTrade(payload: CreateTradeRequest): Promise<Trade> {
  const { data } = await apiClient.post<Trade>('/v1/trades', payload)
  return data
}
