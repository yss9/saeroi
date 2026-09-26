import { Button, ErrorMessage } from '../../../components/common/index.ts'
import { useChainListingStatus } from '../hooks/useChainListingStatus.ts'
import { useListingChainAction } from '../hooks/useListingChainAction.ts'
import type { Listing } from '../types/marketplace.types.ts'

/**
 * <b>서버에서는 내렸는데 체인에는 아직 걸려 있는 판매 글을 정리한다.</b>
 *
 * 판매 취소는 두 번의 쓰기다 — 서버 취소와 체인의 {@code cancelListing} 서명. 서명을
 * 거부했거나 지갑이 잠겨 있었으면 서버만 취소되고 체인의 잠금은 남는다. 그 잠금은
 * 소재 잔액을 묶고 있어서 <b>같은 소재를 다시 팔 수 없게 만든다</b> —
 * {@code Marketplace: insufficient available balance} 가 그것이다.
 *
 * 실제로 그렇게 남은 판매 글이 있다(판매글 #1 이 tokenId 4 의 잔액 12000 전부를 잠근 채
 * ACTIVE 다). 그래서 정상 취소 경로와 별개로, 밀린 서명을 나중에라도 받을 자리가 필요하다.
 *
 * 체인이 이미 정리돼 있으면 아무것도 그리지 않는다. 남아 있을 때만 나온다.
 */
export function ListingChainCancelButton({ listing }: { listing: Listing }) {
  const chainStatus = useChainListingStatus(listing.chainListingId)
  const chain = useListingChainAction()

  if (listing.chainListingId === null) return null

  // 체인 상태를 모르면 아무 주장도 하지 않는다. RPC 가 실패했을 수 있고, "정리되지
  // 않았다" 도 "정리됐다" 도 거짓일 수 있다.
  const status = chainStatus.data?.status
  if (status === undefined || status === null) return null

  // 컨트랙트는 ACTIVE 또는 SOLD_OUT 일 때만 취소를 받는다(Marketplace.sol:183).
  // 이미 CANCELED 면 풀 잠금이 없다.
  if (status === 'CANCELED') return null

  return (
    <div className="listing-chain-cancel">
      <p className="listing-chain-cancel__note">
        이 판매 글에 예약된 수량이 아직 남아 있어 같은 소재를 다시 팔 수 없습니다.
      </p>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => {
          if (listing.chainListingId === null) return
          void chain.cancel(listing.chainListingId)
        }}
        loading={chain.isBusy}
        disabled={chain.isBusy}
      >
        판매 가능 수량 복구
      </Button>
      {chain.isRejected && <p className="listing-chain-cancel__note">요청을 취소했습니다.</p>}
      {chain.error && !chain.isRejected && <ErrorMessage error={chain.error} />}
    </div>
  )
}
