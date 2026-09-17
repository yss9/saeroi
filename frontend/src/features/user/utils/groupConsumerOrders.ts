import type { ConsumerOrderViewStatus, ConsumerRecentOrder } from '../types/consumerMyPage.types.ts'

/**
 * 소비자 주문 목록의 원본 배열은 한 줄이 주문이 아니라 개체(제품)다 — 주문 하나에 제품이
 * 여럿이면 줄이 여럿이다(`ConsumerRecentOrder` 문서 참고). 주문 단위로 큰 박스, 그 안에
 * 제품별 작은 박스를 그리려면 다시 묶어야 한다.
 *
 * 같은 주문의 제품은 원본 배열에서 항상 붙어 있다(`useConsumerMyPage` 가
 * `orders.content.flatMap(order => order.items.map(...))` 로 만들어 order 단위로 이미
 * 연속이다) — 그래서 Map 삽입 순서만으로도 원래 정렬(최신순)이 그대로 유지된다.
 */
export interface ConsumerOrderGroup {
  orderId: number
  orderNumber: string
  orderedAt: string
  status: ConsumerOrderViewStatus
  items: ConsumerRecentOrder[]
}

export function groupConsumerOrders(orders: readonly ConsumerRecentOrder[]): ConsumerOrderGroup[] {
  const groups = new Map<number, ConsumerOrderGroup>()
  for (const item of orders) {
    let group = groups.get(item.orderId)
    if (!group) {
      group = {
        orderId: item.orderId,
        orderNumber: item.orderNumber,
        orderedAt: item.orderedAt,
        status: item.status,
        items: [],
      }
      groups.set(item.orderId, group)
    }
    group.items.push(item)
  }
  return [...groups.values()]
}
