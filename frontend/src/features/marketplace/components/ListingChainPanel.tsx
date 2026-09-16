import { OnChainPanel } from '../../../components/common/index.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { useMaterial } from '../../material/hooks/useMaterial.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useListingOnChain } from '../hooks/useListingOnChain.ts'
import { useOnChainAvailable } from '../hooks/useOnChainAvailable.ts'
import type { Listing } from '../types/marketplace.types.ts'

/**
 * 판매 글을 온체인에도 등록한다. **판매자 본인에게만 보인다.**
 *
 * ⚠️ 컨트랙트의 `createListing(materialId, quantity)` 에서 `materialId` 는 우리 DB 의 id 가
 * 아니라 **온체인 `tokenId`** 다. 컨트랙트는 우리 테이블을 모른다. 그래서 소재가 아직
 * 발행되지 않았으면 부를 인자 자체가 없고, 그 경우 버튼 대신 이유를 보여준다.
 */
export function ListingChainPanel({ listing }: { listing: Listing }) {
  const { user } = useAuth()
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
  const material = useMaterial(listing.materialId)
  const chain = useListingOnChain()

  // 판매자만 서명할 수 있다.
  const actionable = user?.id === listing.sellerId
  const tokenId = material.data?.tokenId ?? null

  // 체인 잔량을 미리 읽는다. 부족하면 createListing 이 revert 하는데, 그걸 모르고 버튼을
  // 열어 두면 사용자가 지갑 팝업을 확인하고 나서 실패를 본다.
  const available = useOnChainAvailable(actionable ? address : null, tokenId)
  const shortOnChain =
    available.data !== undefined && available.data < BigInt(listing.totalQuantity)

  const ready =
    actionable &&
    address !== null &&
    tokenId !== null &&
    !shortOnChain &&
    listing.chainListingId === null

  // **구매자에게는 그리지 않는다.** 누를 수 없는 패널이고, 온체인 주문이 가능한지는
  // 구매 폼이 알아서 처리한다 — 사는 사람이 판단에 쓰지 않는 상자가 소재 정보와 구매
  // 사이를 가로막고 있었다.
  if (!actionable) return null

  return (
    <OnChainPanel
      title="판매 온체인 등록"
      description="판매 수량이 체인에서도 잠깁니다. 등록해야 구매자가 온체인으로 주문할 수 있습니다."
      onChainId={listing.chainListingId}
      actionable={actionable}
      ready={ready}
      blockedReason={
        address === null
          ? '지갑을 먼저 연결해 주세요.'
          : tokenId === null
            ? '이 소재가 아직 온체인에 발행되지 않았습니다. 소재 상세에서 발행을 먼저 진행해 주세요.'
            : undefined
      }
      status={chain.status}
      hash={chain.hash}
      error={chain.error}
      isBusy={chain.isBusy}
      isRejected={chain.isRejected}
      actionLabel="온체인 등록"
      onSign={() => {
        if (tokenId === null) return
        void chain.register(listing.listingId, tokenId, listing.totalQuantity)
      }}
    />
  )
}
