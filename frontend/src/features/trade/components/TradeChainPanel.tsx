import { OnChainPanel } from '../../../components/common/index.ts'
import { useListing } from '../../marketplace/hooks/useListing.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useTradeOnChain } from '../hooks/useTradeOnChain.ts'
import type { Trade } from '../types/trade.types.ts'

/**
 * 거래를 온체인 주문으로 만든다. **구매자에게만 보인다.**
 *
 * **판매자가 먼저 판매 글을 온체인에 등록해야 한다.** 컨트랙트의 `createOrder` 가
 * 온체인 `listingId` 를 인자로 받는데 그것은 판매자 서명으로만 생긴다. 구매자 혼자서는
 * 시작할 수 없는 단계라, 못 누르는 이유를 분명히 적는다 — 버튼만 잠그면 사용자는
 * 자기가 뭘 잘못했다고 생각한다.
 */
export function TradeChainPanel({ trade }: { trade: Trade }) {
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
  const listing = useListing(trade.listingId)
  const chain = useTradeOnChain()

  const chainListingId = listing.data?.chainListingId ?? null

  // **이미 지나간 거래에는 온체인 주문을 걸 수 없다.**
  //
  // 온체인 주문 번호는 발송·수령 확인의 인자다(`markShipped(orderId)` ·
  // `confirmReceipt(orderId)`). 그 단계가 오프체인에서 이미 끝난 뒤에 주문만 만들면,
  // 체인에는 배송도 수령도 없는 CREATED 주문이 영구히 남는다. 그것은 기록이 아니라
  // 오해를 만드는 값이다.
  //
  // 그래서 결제 직후(PAID)까지만 허용한다. 놓친 거래는 되돌릴 수 없고, 그 사실을
  // 감추지 않고 말해 주는 편이 낫다.
  const tooLate = trade.status !== 'CREATED' && trade.status !== 'PAID'

  // 이 패널은 구매자에게만 렌더된다(TradeDetailPage 가 isBuyer 로 가른다).
  const ready =
    !tooLate && address !== null && chainListingId !== null && trade.chainTradeId === null

  return (
    <OnChainPanel
      title="구매 온체인 기록"
      description="주문이 체인에 기록됩니다. 이 번호가 있어야 발송·수령 확인도 온체인에 남습니다."
      onChainId={trade.chainTradeId}
      actionable
      ready={ready}
      blockedReason={
        tooLate
          ? '이 거래는 이미 발송·수령 단계를 지났습니다. 온체인 주문은 결제 직후까지만 만들 수 있어, 이 거래는 오프체인 기록만 남습니다.'
          : address === null
            ? '지갑을 먼저 연결해 주세요.'
            : chainListingId === null
              ? '판매자가 이 판매 글을 온체인에 등록하지 않았습니다. 판매자가 판매글 상세에서 "온체인 등록" 을 먼저 해야 합니다.'
              : undefined
      }
      status={chain.status}
      hash={chain.hash}
      error={chain.error}
      isBusy={chain.isBusy}
      isRejected={chain.isRejected}
      actionLabel="온체인 주문"
      onSign={() => {
        if (chainListingId === null) return
        void chain.order(trade.tradeId, chainListingId, trade.quantity)
      }}
    />
  )
}
