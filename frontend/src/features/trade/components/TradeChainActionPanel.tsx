import { Button, ErrorMessage, TransactionHash } from '../../../components/common/index.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useChainOrderStatus } from '../hooks/useChainOrderStatus.ts'
import { useTradeChainAction } from '../hooks/useTradeChainAction.ts'
import type { Trade } from '../types/trade.types.ts'
import '../../../components/common/on-chain-panel.css'

export interface TradeChainActionPanelProps {
  trade: Trade
  /** 이 화면을 보는 사람이 이 거래에서 어느 쪽인가. 역할(Role)이 아니라 위치다. */
  viewer: 'SELLER' | 'BUYER'
}

/**
 * <b>밀린 온체인 서명을 마저 끝내는 자리.</b> 평소에는 나오지 않는다.
 *
 * 발송·수령의 체인 서명은 각자의 패널({@code ShipmentPanel} · {@code ReceiptPanel})이
 * 서버 성공 직후에 이어서 띄운다. 그래서 정상 경로에서는 이 패널이 할 일이 없다 —
 * 예전처럼 항상 그리면 같은 동작의 버튼이 화면에 두 개 있게 되고, 사용자는 어느 쪽이
 * 진짜인지 알 수 없다. 실제로 "온체인 확인 버튼이 남아 있다" 는 제보가 그것이다.
 *
 * <b>그래도 지우면 안 되는 이유</b> — 서버 쓰기와 체인 서명은 별개의 트랜잭션이라
 * 한쪽만 성공할 수 있다. 서명을 취소했거나, 지갑이 잠겨 있었거나, 네트워크가 끊겼으면
 * 서버는 넘어갔는데 체인은 그대로다. 주문 #1 이 실제로 그 상태다. 그때 다시 서명할
 * 곳이 없으면 두 기록은 영영 어긋난다.
 *
 * 그래서 <b>체인이 서버보다 뒤처져 있을 때만</b> 나온다.
 */
export function TradeChainActionPanel({ trade, viewer }: TradeChainActionPanelProps) {
  // **스토어가 아니라 useWallet 을 쓴다.**
  //
  // walletStore 는 useWallet() 을 부르는 컴포넌트가 화면에 떠 있을 때만 채워진다. 그런
  // 컴포넌트는 지갑 화면(WalletButton · WalletRegistrationPanel)에만 있고 이 화면에는
  // 없다. 그래서 **메타마스크에 지갑이 연결돼 있어도 address 가 null 이라 서명 버튼이
  // 영영 잠긴다.** 스토어는 메모리에만 있어 새로고침하면 더 확실히 빈다.
  //
  // useWallet() 은 페이지 로드 때 팝업 없이 기존 승인 계정을 복원하고, 그 과정에서
  // 스토어도 채운다. 2026-09-08 데모에서 이 문제로 막혀 한 번 고쳤던 자리다.
  const wallet = useWallet()
  const address = wallet.address
  const action = useTradeChainAction(trade.tradeId)
  const chainOrder = useChainOrderStatus(trade.chainTradeId)

  // 온체인 주문이 없으면 바꿀 상태도 없다. 그 단계를 먼저 끝내야 한다.
  if (trade.chainTradeId === null) return null

  // 체인 상태를 모르면 아무것도 주장하지 않는다. "밀렸다" 도 "끝났다" 도 거짓일 수 있고,
  // 모르는 채로 서명시키면 사용자는 revert 메시지만 보게 된다.
  const chainStatus = chainOrder.data?.status
  if (chainStatus === undefined || chainStatus === null) return null

  // 이 거래가 서버 기준으로 어디까지 왔는가.
  const offChainShipped =
    trade.status === 'SHIPPED' || trade.status === 'RECEIVED' || trade.status === 'COMPLETED'
  const offChainReceived = trade.status === 'RECEIVED' || trade.status === 'COMPLETED'

  const chainShipped = chainStatus !== 'CREATED'
  const chainReceived = chainStatus === 'RECEIVED' || chainStatus === 'COMPLETED'

  // 발송 서명이 밀렸다. 판매자만 할 수 있다 (컨트랙트가 seller 를 검사한다).
  const shipPending = offChainShipped && !chainShipped
  // 수령 서명이 밀렸다. 구매자만 할 수 있다.
  const receivePending = offChainReceived && !chainReceived

  // **취소 서명이 밀렸다.**
  //
  // 서버에서 취소된 거래는 판매 글에 수량을 되돌려 놓는다. 체인은 cancelOrder 를
  // 서명해야만 되돌아오므로, 서명을 놓치면 체인의 remainingQuantity 만 작게 남는다.
  // 그 뒤 다음 구매자가 화면에 보이는 잔량을 전부 사면 서버는 통과하고 체인만
  // 되돌려진다 — "10kg 남았는데 10kg 사면 부족" 이 그것이다.
  //
  // 컨트랙트는 주문이 CREATED 일 때만 취소를 받고, 구매자와 판매자 모두 서명할 수
  // 있다(Marketplace.sol:279). 그래서 먼저 보는 쪽이 정리하면 된다.
  const cancelPending = trade.status === 'CANCELED' && chainStatus === 'CREATED'

  if (!shipPending && !receivePending && !cancelPending) return null

  // **컨트랙트가 순서를 강제한다** — confirmReceipt 는 주문이 SHIPPED 일 때만 통과한다
  // (Marketplace.sol:256). 그래서 발송 서명이 밀린 동안에는 구매자가 수령을 서명할
  // 방법이 없다. 버튼만 잠그면 "왜 안 되는지" 를 알 수 없으므로 이유를 적는다.
  const blockedByShipping = receivePending && !chainShipped

  const isSellerTurn = shipPending && viewer === 'SELLER'
  const isBuyerTurn = receivePending && !blockedByShipping && viewer === 'BUYER'
  // 취소는 양쪽 다 서명할 수 있으므로 보고 있는 사람이 곧 서명자다.
  const isCancelTurn = cancelPending

  const title = cancelPending
    ? '온체인 주문 취소가 밀렸습니다'
    : shipPending
      ? '온체인 발송 기록이 밀렸습니다'
      : '온체인 수령 기록이 밀렸습니다'

  return (
    <section className="on-chain-panel">
      <header className="on-chain-panel__header">
        <h3>{title}</h3>
        <span className="on-chain-panel__badge">주문 #{trade.chainTradeId}</span>
      </header>

      <p className="on-chain-panel__lead">
        {cancelPending
          ? '이 거래는 취소됐지만 체인의 주문은 아직 남아 있습니다. 그동안 이 수량은 체인에서 잠긴 채라, 판매 글에서 그만큼을 다시 팔 수 없습니다. 지금 서명하면 되돌아갑니다.'
          : '서비스 기록은 다음 단계로 넘어갔는데 체인에는 아직 남지 않았습니다. 서명이 취소됐거나 지갑이 연결돼 있지 않았을 때 이렇게 됩니다. 지금 마저 서명하면 두 기록이 맞춰집니다.'}
      </p>

      {blockedByShipping && (
        <p className="on-chain-panel__blocked" role="status">
          판매자의 온체인 발송이 먼저 기록돼야 수령을 서명할 수 있습니다. 컨트랙트가
          그 순서를 검사합니다. 판매자에게 발송 서명을 요청해 주세요.
        </p>
      )}

      {(isSellerTurn || isBuyerTurn || isCancelTurn) && (
        <>
          <div className="on-chain-panel__actions">
            <Button
              onClick={() => {
                if (trade.chainTradeId === null) return
                if (isCancelTurn) {
                  void action.cancel(trade.chainTradeId)
                  return
                }
                void (isSellerTurn
                  ? action.ship(trade.chainTradeId)
                  : action.receive(trade.chainTradeId))
              }}
              disabled={address === null || action.isBusy}
              loading={action.isBusy}
            >
              {isCancelTurn
                ? '온체인 주문 취소'
                : isSellerTurn
                  ? '온체인 발송 마저 기록'
                  : '온체인 수령 마저 기록'}
            </Button>
            {address === null && (
              <span className="on-chain-panel__hint">지갑을 먼저 연결해 주세요.</span>
            )}
            {action.status === 'SIGNING' && (
              <span className="on-chain-panel__hint">지갑에서 서명해 주세요</span>
            )}
            {action.status === 'PENDING' && (
              <span className="on-chain-panel__hint">블록 확정을 기다리는 중…</span>
            )}
            {action.status === 'CONFIRMED' && (
              <span className="on-chain-panel__hint">체인에 기록됐습니다.</span>
            )}
          </div>

          {action.isRejected && (
            <p className="on-chain-panel__hint">서명을 취소했습니다. 다시 시도할 수 있습니다.</p>
          )}
          {action.error && !action.isRejected && <ErrorMessage error={action.error} />}
        </>
      )}

      {/* 내 차례가 아니면 상대가 서명해야 한다는 사실만 알린다. */}
      {!isSellerTurn && !isBuyerTurn && !isCancelTurn && !blockedByShipping && (
        <p className="on-chain-panel__hint">
          {shipPending ? '판매자' : '구매자'}가 서명해야 하는 단계입니다.
        </p>
      )}

      {action.hash && <TransactionHash hash={action.hash} />}
    </section>
  )
}
