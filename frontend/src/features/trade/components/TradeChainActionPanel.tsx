import { Button, ErrorMessage, TransactionHash } from '../../../components/common/index.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useTradeChainAction } from '../hooks/useTradeChainAction.ts'
import type { Trade } from '../types/trade.types.ts'
import '../../../components/common/on-chain-panel.css'

export interface TradeChainActionPanelProps {
  trade: Trade
  /** 이 화면을 보는 사람이 이 거래에서 어느 쪽인가. 역할(Role)이 아니라 위치다. */
  viewer: 'SELLER' | 'BUYER'
}

/**
 * 발송·수령 확인의 온체인 서명.
 *
 * **앞 단계들과 다르다.** 새 번호를 채번하지 않고 이미 있는 주문의 상태만 바꾸므로,
 * 서버에 알릴 것이 없다. 그래서 온체인 기록 패널이 아니라 서명 버튼 하나다.
 *
 * **`confirmReceipt` 가 소유권이 실제로 옮겨가는 자리다** — 컨트랙트가 ERC1155 잔액을
 * 판매자에서 구매자로 이동시킨다. 오프체인 수령 확인이 DB 보유량을 옮기는 것과 짝이다.
 * 둘은 별개의 트랜잭션이라 한쪽만 성공할 수 있고, 그래서 버튼을 나눠 두었다.
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

  // 온체인 주문이 없으면 바꿀 상태도 없다. 그 단계를 먼저 끝내야 한다.
  if (trade.chainTradeId === null) return null

  const isShipStep = viewer === 'SELLER' && trade.status === 'PAID'
  const isReceiveStep = viewer === 'BUYER' && trade.status === 'SHIPPED'
  if (!isShipStep && !isReceiveStep) return null

  return (
    <section className="on-chain-panel">
      <header className="on-chain-panel__header">
        <h3>{isShipStep ? '발송 온체인 기록' : '수령 확인 온체인 기록'}</h3>
        <span className="on-chain-panel__badge">주문 #{trade.chainTradeId}</span>
      </header>

      <p className="on-chain-panel__lead">
        {isShipStep
          ? '발송 사실을 체인에 남깁니다. 잠금은 유지됩니다 — 배송 중 재판매를 막습니다.'
          : '수령을 체인에 확인합니다. 이 서명으로 소재 소유권이 실제로 이전됩니다.'}
      </p>

      <div className="on-chain-panel__actions">
        <Button
          onClick={() => {
            if (trade.chainTradeId === null) return
            void (isShipStep ? action.ship(trade.chainTradeId) : action.receive(trade.chainTradeId))
          }}
          disabled={address === null || action.isBusy}
          loading={action.isBusy}
        >
          {isShipStep ? '온체인 발송' : '온체인 수령 확인'}
        </Button>
        {address === null && <span className="on-chain-panel__hint">지갑을 먼저 연결해 주세요.</span>}
        {action.status === 'SIGNING' && <span className="on-chain-panel__hint">지갑에서 서명해 주세요</span>}
        {action.status === 'PENDING' && <span className="on-chain-panel__hint">블록 확정을 기다리는 중…</span>}
        {action.status === 'CONFIRMED' && <span className="on-chain-panel__hint">체인에 기록됐습니다.</span>}
      </div>

      {action.isRejected && (
        <p className="on-chain-panel__hint">서명을 취소했습니다. 다시 시도할 수 있습니다.</p>
      )}
      {action.error && !action.isRejected && <ErrorMessage error={action.error} />}

      {action.hash && <TransactionHash hash={action.hash} />}
    </section>
  )
}
