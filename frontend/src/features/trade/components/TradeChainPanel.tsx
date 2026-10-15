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

  // 이 패널은 구매자에게만 렌더된다(TradeDetail 이 isBuyer 로 가른다).
  const ready =
    !tooLate && address !== null && chainListingId !== null && trade.chainTradeId === null

  // **이미 기록된 거래에는 아무것도 그리지 않는다.**
  //
  // 구매 신청(`PurchaseForm`)이 거래를 만들면서 곧바로 `createOrder` 까지 서명한다.
  // 그래서 상세에 닿는 거래는 대부분 이미 번호를 갖고 있고, 그때 이 패널에 남는 것은
  // 제목·설명·번호뿐이다 — 버튼은 `!done` 이라야 그려진다. 결제하러 온 사람에게는
  // 누를 것도 할 일도 없는 칸이 결제 수단 위를 차지하고 있었다.
  //
  // <b>없앨 수는 없다.</b> 구매 때 서명이 거절·실패했으면 `chainTradeId` 가 비고, 이
  // 패널이 그것을 되살리는 유일한 수단이다. 되살리지 못하면 발송·수령도 체인에 남지
  // 않아 판매자 화면의 「이력 기록」이 영영 '미기록' 이 된다.
  //
  // 그래서 판매 글의 온체인 칸과 같은 규칙을 쓴다(`ListingChainPanel`) —
  // <b>평소에는 조용하고, 빠졌을 때만 소리를 낸다.</b> 방금 이 화면에서 서명해 해시가
  // 떠 있는 동안은 남겨 둔다. 결과를 확인하지 못한 채 칸이 사라지면 성공했는지 알 수 없다.
  //
  // 상세 페이지와 목록 팝업이 같은 `TradeDetail` 을 쓰므로 여기 한 곳이면 둘 다 잡힌다.
  if (trade.chainTradeId !== null && chain.hash === null) return null

  return (
    <OnChainPanel
      title="거래 이력 기록"
      description="주문 정보를 안전하게 저장해 발송과 수령 이력을 이어서 확인할 수 있습니다."
      onChainId={trade.chainTradeId}
      actionable
      ready={ready}
      blockedReason={
        tooLate
          ? '이 거래는 이미 발송·수령 단계를 지나 추가 이력을 저장할 수 없습니다.'
          : address === null
            ? '지갑을 먼저 연결해 주세요.'
            : chainListingId === null
              ? '판매 이력 기록이 완료되지 않았습니다. 판매자에게 기록 완료를 요청해 주세요.'
              : undefined
      }
      status={chain.status}
      hash={chain.hash}
      error={chain.error}
      isBusy={chain.isBusy}
      isRejected={chain.isRejected}
      actionLabel="거래 기록 완료하기"
      onSign={() => {
        if (chainListingId === null) return
        void chain.order(trade.tradeId, chainListingId, trade.quantity)
      }}
    />
  )
}
