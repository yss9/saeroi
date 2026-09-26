import { getMyProductOrder } from '../user/api/getMyProductOrders.ts'
import { createAppError, ERROR_KIND, isAppError } from '../../utils/errors.ts'
import type { ConsumerOrderResponse } from '../user/types/consumerOrder.types.ts'
import { approveProductOrderPayment, cancelProductOrder, readyProductOrderPayment } from '../user/api/productOrderPaymentApi.ts'
import { approveGroup, cancelGroup, getGroup, readyGroup, type OrderGroup } from './checkout.ts'

function asCheckout(order: ConsumerOrderResponse): OrderGroup {
  return { orderGroupId: order.orderId, orderGroupCode: order.orderCode, totalPrice: order.totalPrice,
    status: order.status === 'CREATED' ? 'CREATED' : 'PAID', orderedAt: order.orderedAt, paidAt: order.paidAt,
    cancelable: order.status === 'CREATED' || order.status === 'PAID', paymentDeadline: order.paymentDeadline,
    remainingSeconds: order.remainingSeconds, orders: [order] }
}

/** Existing orders only: resuming checkout must never create another order. */
export async function getExistingProductOrder(id: number): Promise<ConsumerOrderResponse> {
  try {
    return await getMyProductOrder(id)
  } catch (error) {
    if (!isAppError(error) || error.status !== 404) throw error
    throw createAppError(ERROR_KIND.API_ERROR, { code: 'CHECKOUT_ORDER_NOT_FOUND', message: '주문 내역에서 이 주문을 찾을 수 없습니다. 주문 내역으로 돌아가 확인해 주세요.' })
  }
}

export function orderCheckout(single: boolean) {
  return {
    get: single ? async (id: number) => asCheckout(await getExistingProductOrder(id)) : getGroup,
    ready: single ? readyProductOrderPayment : readyGroup,
    approve: single ? async (id: number, token: string) => asCheckout(await approveProductOrderPayment(id, token)) : approveGroup,
    cancel: single ? cancelProductOrder : cancelGroup,
    storageKey: single ? 'payment.productOrderId' : 'payment.orderGroupId',
  }
}
