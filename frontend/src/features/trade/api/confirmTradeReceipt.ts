import { apiClient } from '../../../services/apiClient.ts'
import type { TradeReceiptResult } from '../types/trade.types.ts'

/**
 * 구매자 수령 확인 — `POST /v1/trades/{tradeId}/receive/confirm`
 *
 * **소유권이 실제로 넘어가는 유일한 자리다.** 서버가 한 트랜잭션에서 네 테이블을 움직인다 —
 * 거래 상태(`SHIPPED` → `RECEIVED` → `COMPLETED`), 배송 완료 시각, 양쪽 보유량,
 * 그리고 잠금 소비. 그래서 응답으로 이전 수량과 양쪽 잔량이 함께 온다.
 *
 * 바디가 없다. 구매자는 JWT 에서 얻고 (`BRAND` 역할 + 실제 buyer 대조), 수량은 거래에
 * 이미 확정돼 있어 화면이 보낼 값이 없다. 부분 수령은 지원하지 않는다.
 *
 * TODO(온체인 연동): 정책상 이 시점에 구매자가 `Marketplace.confirmReceipt(orderId)` 를
 * 서명해야 하고 래퍼도 이미 있다 (`features/marketplace/services/marketplaceContractService.ts`).
 * 지금 붙이지 않은 이유는 두 가지다 — 서버가 `txHash` 를 받을 자리가 없고
 * (`blockchain_transaction` 매핑 부재), `Trade.chainTradeId` 를 채우는 경로도 아직 없어서
 * 서명할 `orderId` 자체가 없다. 둘이 준비되면 이 함수 앞단에
 * `useBlockchainTransaction` 을 두고 CONFIRMED 이후 이 요청을 보낸다.
 *
 * ⚠️ 되돌릴 수 없다.
 */
export async function confirmTradeReceipt(tradeId: number): Promise<TradeReceiptResult> {
  const { data } = await apiClient.post<TradeReceiptResult>(`/v1/trades/${tradeId}/receive/confirm`)
  return data
}
