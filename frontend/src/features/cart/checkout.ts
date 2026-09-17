import { apiClient } from '../../services/apiClient.ts'
import { isAppError } from '../../utils/errors.ts'
import type { ConsumerOrderResponse } from '../user/types/consumerOrder.types.ts'
import type { CartItem } from './useCart.ts'

export interface OrderGroup {
  orderGroupId: number
  orderGroupCode: string
  totalPrice: number
  status: 'CREATED' | 'PAID'
  orderedAt: string
  paidAt: string | null
  cancelable: boolean
  orders: ConsumerOrderResponse[]
}
export interface GroupReady { orderGroupId: number; tid: string; redirectUrl: string; totalPrice: number; live: boolean }
const path = '/v1/product-order-groups'
export const getGroup = async (id: number): Promise<OrderGroup> => (await apiClient.get<OrderGroup>(`${path}/${id}`)).data
export const readyGroup = async (id: number): Promise<GroupReady> => (await apiClient.post<GroupReady>(`${path}/${id}/payment/ready`)).data
export const approveGroup = async (id: number, pgToken: string): Promise<OrderGroup> => (await apiClient.post<OrderGroup>(`${path}/${id}/payment/approve`, { pgToken })).data
export const cancelGroup = async (id: number, reason?: string): Promise<void> => { await apiClient.post(`${path}/${id}/cancel`, reason?.trim() ? { reason: reason.trim() } : undefined) }
export const checkoutCart = async (productModelIds: number[], addressId: number, expectedTotalPrice: number): Promise<OrderGroup> =>
  (await apiClient.post<OrderGroup>('/v1/consumers/me/cart/checkout', { productModelIds: [...new Set(productModelIds)], addressId, expectedTotalPrice })).data

export function selectedCart(items: CartItem[], ids: readonly number[]) {
  const selected = items.filter(item => item.purchasable && ids.includes(item.product.productModelId))
  return { selected, quantity: selected.reduce((n, item) => n + item.quantity, 0), price: selected.reduce((n, item) => n + item.product.price * item.quantity, 0) }
}
export function errorLines(error: unknown): Record<string, unknown>[] {
  return isAppError(error) && Array.isArray(error.details) ? error.details.filter((line): line is Record<string, unknown> => typeof line === 'object' && line !== null && !Array.isArray(line)) : []
}
const money = (n: number) => new Intl.NumberFormat('ko-KR').format(n) + '원'
export function checkoutIssues(error: unknown): Record<number, string> {
  if (!isAppError(error)) return {}
  const issues: Record<number, string> = {}
  for (const line of errorLines(error)) {
    if (typeof line.productModelId !== 'number') continue
    if (error.code === 'CART_CHECKOUT_ITEMS_UNAVAILABLE') issues[line.productModelId] = line.reason === 'HIDDEN_CATEGORY' ? '소비자 판매 대상이 아닌 상품입니다. 선택에서 제외해 주세요.' : '판매가 종료된 상품입니다. 선택에서 제외해 주세요.'
    if (error.code === 'CART_CHECKOUT_PRICE_CHANGED' && typeof line.addedPrice === 'number' && typeof line.currentPrice === 'number') issues[line.productModelId] = `담을 때 ${money(line.addedPrice)} → 현재 ${money(line.currentPrice)}. 새 금액을 확인해 주세요.`
    if (error.code === 'PRODUCT_ORDER_ITEMS_OUT_OF_STOCK' && typeof line.requested === 'number' && typeof line.available === 'number') issues[line.productModelId] = `요청 ${line.requested}개 · 남은 재고 ${line.available}개. 수량을 줄이거나 선택에서 제외해 주세요.`
  }
  return issues
}
