import { Button, ErrorMessage } from '../../../components/common/index.ts'
import { useOnChainOrder } from '../hooks/useOnChainOrder.ts'
import { useTradeChainAction } from '../hooks/useTradeChainAction.ts'
import type { Trade } from '../types/trade.types.ts'

/**
 * <b>서버에서는 취소됐는데 체인에는 아직 예약이 남은 거래를 정리한다.</b>
 *
 * 구매 신청은 두 곳의 수량을 깎는다 — 서버의 {@code availableQuantity} 와 체인의
 * {@code remainingQuantity} 다. 취소도 두 번의 쓰기여야 하는데, 서버 취소는 자동으로
 * 일어나도(결제 마감 배치) <b>체인은 당사자 서명 없이 풀 수 없다</b>
 * ({@code Marketplace.cancelOrder} 가 buyer 또는 seller 를 요구한다).
 *
 * 그래서 서버만 풀린 상태가 남는다. 그러면 다음 구매자에게 이런 일이 일어난다.
 *
 *   1. 화면에는 살 수 있는 수량으로 보인다 (서버 기준)
 *   2. 거래는 만들어진다 (서버 통과)
 *   3. 체인 서명에서 막힌다 — 체인의 잔량은 아직 예약에 묶여 있다
 *
 * {@code useTradeOnChain} 이 서명 전에 체인 잔량을 미리 읽어 그 사실을 문구로 알려
 * 주지만, 알려 줄 뿐 풀어 주지는 못한다. 푸는 자리가 여기다.
 *
 * <b>체인 상태를 직접 읽어 판단한다.</b> 서버가 취소됐다고 체인도 취소된 것은 아니고,
 * 반대로 이미 해제 서명을 마쳤을 수도 있다. 넘겨짚으면 이미 푼 사람에게 다시 풀라는
 * 버튼이 보이고, 눌러도 {@code order is not cancellable} 로 되돌려진다.
 */
export function TradeChainReleaseButton({ trade }: { trade: Trade }) {
  const onChainOrder = useOnChainOrder(trade.chainTradeId)
  const chain = useTradeChainAction(trade.tradeId)

  if (trade.chainTradeId === null) return null

  // 서버가 아직 살아 있다고 보는 거래는 건드리지 않는다. 진행 중인 거래의 예약을 풀면
  // 발송·수령 확인이 체인에서 막힌다 — markShipped·confirmReceipt 가 그 주문을 인자로 받는다.
  if (trade.status !== 'CANCELED') return null

  // 체인 상태를 모르면 아무 주장도 하지 않는다. RPC 가 실패했을 수 있고, "남아 있다" 도
  // "정리됐다" 도 거짓일 수 있다.
  const status = onChainOrder.data?.status
  if (status === undefined || status === null) return null

  // 컨트랙트는 CREATED 일 때만 취소를 받는다(Marketplace.sol:281). 그 밖의 상태에는
  // 풀 예약이 없다.
  if (status !== 'CREATED') return null

  return (
    <div className="trade-chain-release">
      <p className="trade-chain-release__note">
        체인에는 이 거래의 예약이 아직 남아 있어, 판매자가 그 수량을 다시 팔 수 없습니다.
      </p>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => {
          if (trade.chainTradeId === null) return
          void chain.cancel(trade.chainTradeId)
        }}
        loading={chain.isBusy}
        disabled={chain.isBusy}
      >
        온체인 예약 해제
      </Button>
      {chain.isRejected && <p className="trade-chain-release__note">서명을 취소했습니다.</p>}
      {chain.error && !chain.isRejected && <ErrorMessage error={chain.error} />}
    </div>
  )
}
