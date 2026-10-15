import { useTrades } from '../../trade/hooks/useTrades.ts'

export interface SellerTradeSummary {
  all: number
  toShip: number
  shipping: number
  done: number
  isPending: boolean
}

/**
 * 판매 거래 탭에 붙일 건수.
 *
 * <b>상태별로 따로 묻는다.</b> `GET /v1/trades` 는 상태를 하나만 받으므로 한 번의 조회로는
 * 탭 네 개를 채울 수 없다. 건수만 필요하니 `size: 1` 로 받아 `totalElements` 만 읽고
 * 목록 내용은 버린다.
 *
 * <b>눌러 보기 전에 몇 건인지 알아야 한다.</b> 이 화면에 오는 이유는 "오늘 뭘 보내야 하나"
 * 하나인데, 탭에 숫자가 없으면 발송할 게 있는지를 탭을 눌러야만 알 수 있었다.
 *
 * 훅을 헬퍼 함수로 감싸지 않는다 — 중첩 함수 안의 훅 호출은 규칙 위반이고, 네 번을
 * 그대로 적는 편이 어느 상태를 세는지도 읽힌다.
 */
export function useSellerTradeSummary(): SellerTradeSummary {
  const all = useTrades({ side: 'SELL', size: 1 })
  // PAID = 구매자가 돈을 냈고 아직 안 보낸 것. 판매자에게는 이것이 "발송 필요" 다.
  const toShip = useTrades({ side: 'SELL', status: 'PAID', size: 1 })
  const shipping = useTrades({ side: 'SELL', status: 'SHIPPED', size: 1 })
  const done = useTrades({ side: 'SELL', status: 'COMPLETED', size: 1 })

  return {
    all: all.data?.totalElements ?? 0,
    toShip: toShip.data?.totalElements ?? 0,
    shipping: shipping.data?.totalElements ?? 0,
    done: done.data?.totalElements ?? 0,
    isPending: all.isPending || toShip.isPending || shipping.isPending || done.isPending,
  }
}
