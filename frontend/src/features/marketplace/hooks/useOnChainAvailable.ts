import { useQuery } from '@tanstack/react-query'
import { getAvailableBalance } from '../services/marketplaceContractService.ts'

/**
 * 체인에서 이 소재의 판매 가능 잔량을 읽는다. gram 정수.
 *
 * **왜 미리 읽는가** — `createListing` 은 잔량이 부족하면 revert 한다
 * (`Marketplace: insufficient available balance`). 그걸 모르고 버튼을 열어 두면 사용자가
 * 지갑 팝업까지 확인하고 나서 실패를 본다. 읽기 호출은 가스도 서명도 필요 없으므로
 * 미리 물어보는 편이 낫다.
 *
 * 잔량이 0 인 대표적인 경우는 **같은 소재를 이미 다른 판매 글이 체인에서 잠근 것**이다.
 * 오프체인 판매 글은 여러 개 만들 수 있는데 체인 잠금은 실제 잔량을 소모하므로,
 * 두 번째 판매 글은 체인에 올릴 수 없다.
 */
export function useOnChainAvailable(owner: string | null, tokenId: number | null) {
  return useQuery({
    queryKey: ['onchain', 'available', owner, tokenId],
    queryFn: () => getAvailableBalance(owner as string, tokenId as number),
    enabled: owner !== null && tokenId !== null,
    // 남이 판매 글을 올리면 바뀌는 값이다. 화면에 머무는 동안 옛 값을 붙들지 않는다.
    staleTime: 10_000,
  })
}
