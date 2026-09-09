import { useQuery } from '@tanstack/react-query'
import { getListing, type OnChainListing } from '../services/marketplaceContractService.ts'

/**
 * 이 판매 글이 <b>체인에서는</b> 어떤 상태인가.
 *
 * <b>왜 필요한가</b> — 판매 취소는 두 번의 쓰기다. 서버 취소(REST)와 체인의
 * {@code cancelListing} 서명. 사용자가 서명을 거부하거나 지갑이 잠겨 있었으면 서버만
 * 취소되고 체인의 잠금은 남는다. 그러면 같은 소재를 다시 팔 수 없다 —
 * {@code createListing} 이 {@code Marketplace: insufficient available balance} 로 막힌다.
 *
 * 판매 글 응답에는 이 사실을 알려 줄 필드가 없다. {@code chainListingId} 는 "체인에
 * 있다" 까지만 말하고 그것이 아직 ACTIVE 인지는 말하지 않는다. 그래서 체인에 직접 묻는다.
 *
 * {@link useChainOrderStatus} 와 같은 이유·같은 모양이다.
 */
export function useChainListingStatus(chainListingId: number | null) {
  return useQuery<OnChainListing>({
    queryKey: ['chain', 'listing', chainListingId],
    queryFn: () => getListing(chainListingId as number),
    enabled: chainListingId !== null,
    staleTime: 15_000,
    // 체인 조회가 실패해도 화면은 살아 있어야 한다. 실패는 "모른다" 로 다루고,
    // 호출부가 그때는 아무 주장도 하지 않는다.
    retry: 1,
  })
}
