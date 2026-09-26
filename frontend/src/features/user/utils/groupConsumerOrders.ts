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
  orderGroupId?: number | null
  orderGroupCode?: string | null
  orderNumber: string
  orderedAt: string
  status: ConsumerOrderViewStatus
  paymentDeadline: string | null
  remainingSeconds: number | null
  items: ConsumerRecentOrder[]
}

export interface ConsumerOrderBundle {
  key: string
  orderGroupId?: number | null
  orderGroupCode?: string | null
  orders: ConsumerOrderGroup[]
}

export function groupConsumerOrders(orders: readonly ConsumerRecentOrder[]): ConsumerOrderGroup[] {
  const groups = new Map<number, ConsumerOrderGroup>()
  for (const item of orders) {
    let group = groups.get(item.orderId)
    if (!group) {
      group = {
        orderId: item.orderId,
        orderGroupId: item.orderGroupId,
        orderGroupCode: item.orderGroupCode,
        orderNumber: item.orderNumber,
        orderedAt: item.orderedAt,
        status: item.status,
        paymentDeadline: item.paymentDeadline,
        remainingSeconds: item.remainingSeconds,
        items: [],
      }
      groups.set(item.orderId, group)
    }
    group.items.push(item)
  }
  return [...groups.values()]
}

/** 결제는 묶음 단위, 배송은 브랜드 주문 단위로 다룰 수 있도록 주문들을 한 번 더 묶는다. */
export function bundleConsumerOrders(
  groups: readonly ConsumerOrderGroup[],
): ConsumerOrderBundle[] {
  const bundles = new Map<string, ConsumerOrderBundle>()
  for (const group of groups) {
    const key = group.orderGroupId != null ? `group:${group.orderGroupId}` : `order:${group.orderId}`
    let bundle = bundles.get(key)
    if (!bundle) {
      bundle = {
        key,
        orderGroupId: group.orderGroupId,
        orderGroupCode: group.orderGroupCode,
        orders: [],
      }
      bundles.set(key, bundle)
    }
    bundle.orders.push(group)
  }
  return [...bundles.values()]
}
