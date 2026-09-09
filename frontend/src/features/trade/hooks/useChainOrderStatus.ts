import { useQuery } from '@tanstack/react-query'
import { getOrder, type OnChainOrder } from '../../marketplace/services/marketplaceContractService.ts'

/**
 * 이 거래가 <b>체인에서는</b> 어디까지 왔는가.
 *
 * <b>왜 필요한가</b> — 서버 상태와 체인 상태는 따로 논다. 발송·수령은 두 번의 쓰기이고
 * (서버 API 하나, 지갑 서명 하나) 둘 중 하나만 성공할 수 있다. 실제로 그렇게 됐다:
 * 주문 #1 은 오프체인으로는 진행됐는데 체인은 {@code CREATED} 에 멈춰 있다.
 *
 * 거래 응답에는 이 사실을 알려 줄 필드가 없다 — {@code chainTradeId} 는 "주문이 체인에
 * 있다" 까지만 말하고 그 주문이 어느 단계인지는 말하지 않는다. 그래서 체인에 직접 묻는다.
 *
 * <b>서버 캐시가 아니라 체인이 정본이다.</b> 그래서 무효화 대상이 아니라 짧은
 * {@code staleTime} 을 두고 다시 읽는다 — 상대방이 서명하면 우리 서버를 거치지 않고
 * 값이 바뀌기 때문이다.
 */
export function useChainOrderStatus(chainTradeId: number | null) {
  return useQuery<OnChainOrder>({
    // 체인 주문 번호로 키를 잡는다. 거래 id 로 잡으면 같은 체인 주문을 보는 두 화면이
    // 서로 다른 캐시를 갖는다.
    queryKey: ['chain', 'order', chainTradeId],
    queryFn: () => getOrder(chainTradeId as number),
    enabled: chainTradeId !== null,
    staleTime: 15_000,
    // 체인 조회가 실패해도 화면은 살아 있어야 한다. 실패는 "모른다" 로 다루고,
    // 호출부가 그때는 버튼을 감춘다 — 모르는 채로 서명시키면 revert 만 본다.
    retry: 1,
  })
}
