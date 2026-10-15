import { useQuery } from '@tanstack/react-query'
import { getOrder } from '../../marketplace/services/marketplaceContractService.ts'

/**
 * 이 거래의 온체인 주문 상태.
 *
 * <b>서버 상태로는 알 수 없는 것을 묻는다.</b> 서버가 거래를 취소해도 체인의 주문은
 * 그대로 `CREATED` 로 남는다 — `cancelOrder` 가 당사자 서명을 요구해 서버가 대신할 수
 * 없기 때문이다. 그래서 "체인에 아직 예약이 남아 있는가" 는 체인에 직접 물어야 한다.
 *
 * <b>서버 상태로 넘겨짚지 않는다.</b> 거래가 취소됐다고 체인도 취소된 것은 아니고,
 * 반대로 구매자가 이미 해제 서명을 했을 수도 있다. 넘겨짚으면 이미 푼 사람에게 다시
 * 풀라는 버튼을 보여주고, 그 버튼은 눌러도 `order is not cancellable` 로 되돌려진다.
 */
export function useOnChainOrder(chainTradeId: number | null) {
  return useQuery({
    // <b>접두사를 `['chain','order']` 로 맞춘다.</b> `useTradeChainAction` 이 서명 확정 뒤
    // 그 접두사로 무효화한다 — 다른 이름을 쓰면 해제 서명을 마쳐도 이 화면이 옛 상태를
    // 붙들어, 이미 푼 예약에 「해제」 버튼이 계속 남는다.
    queryKey: ['chain', 'order', chainTradeId],
    queryFn: () => getOrder(chainTradeId as number),
    enabled: chainTradeId !== null,
    // 내가 서명하지 않는 한 바뀌지 않는 값이다. 상대가 먼저 풀 수도 있어 영원히 붙들지는
    // 않되, 화면에 머무는 동안 계속 다시 묻지도 않는다.
    staleTime: 30_000,
  })
}
