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

  // **취소된 거래만 막는다.**
  //
  // 예전에는 결제 직후(PAID)까지만 허용했다. 그때는 체인이 뒤처지면 따라잡을 방법이
  // 없어서, 늦게 만든 주문이 배송도 수령도 없는 CREATED 상태로 영구히 남았기 때문이다.
  // 그건 기록이 아니라 오해를 만드는 값이었다.
  //
  // 지금은 {@link TradeChainActionPanel} 이 밀린 발송·수령 서명을 마저 받는다. 늦게
  // 만든 주문도 COMPLETED 까지 갈 수 있으므로 막을 이유가 없어졌다. 컨트랙트도
  // 오프체인 진행 상황을 보지 않는다 — `createOrder` 는 판매 글이 ACTIVE 이고 수량이
  // 남아 있는지만 검사한다(Marketplace.sol:204).
  //
  // 취소된 거래는 여전히 막는다. 일어나지 않은 거래를 체인에 남기는 것이고,
  // `createOrder` 가 판매 글 수량을 잠그므로 아무도 풀 수 없는 잠금이 생긴다.
  const tooLate = trade.status === 'CANCELED'

  // 이미 지나간 단계를 지금 기록하는 것이라, 체인의 시각은 실제 구매 시각이 아니다.
  // 그 차이를 숨기지 않는다.
  const isCatchUp = trade.status !== 'CREATED' && trade.status !== 'PAID'

  // 이 패널은 구매자에게만 렌더된다(TradeDetailPage 가 isBuyer 로 가른다).
  const ready =
    !tooLate && address !== null && chainListingId !== null && trade.chainTradeId === null

  return (
    <OnChainPanel
      title="구매 온체인 기록"
      description={
        isCatchUp
          ? '이 거래는 이미 진행됐지만 온체인 주문이 없습니다. 지금 만들면 밀린 발송·수령 서명을 이어서 할 수 있습니다. 다만 체인에 남는 시각은 실제 구매 시각이 아니라 지금입니다.'
          : '주문이 체인에 기록됩니다. 이 번호가 있어야 발송·수령 확인도 온체인에 남습니다.'
      }
      onChainId={trade.chainTradeId}
      actionable
      ready={ready}
      blockedReason={
        tooLate
          ? '취소된 거래는 온체인에 기록하지 않습니다.'
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
