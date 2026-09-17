import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/hooks/useAuth.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { apiClient } from '../../services/apiClient.ts'
import { isAppError } from '../../utils/errors.ts'
import type { PublicProductModel } from '../product/types/publicProduct.types.ts'
import type { AuthUser } from '../../stores/authStore.ts'

export interface CartItem { product: PublicProductModel; quantity: number; addedPrice: number; priceChanged: boolean; purchasable: boolean }
export interface Cart { items: CartItem[]; totalQuantity: number; totalPrice: number }
export type CartChange = { kind: 'add' | 'quantity'; productModelId: number; quantity: number } | { kind: 'remove'; productModelId: number } | { kind: 'clear' }
export const cartKey = (userId: number | undefined) => ['consumer-cart', userId] as const
const path = '/v1/consumers/me/cart'

export function useCart() {
  const { user, isAuthenticated, role } = useAuth()
  return useQuery({
    queryKey: cartKey(user?.id),
    queryFn: async ({ signal }) => (await apiClient.get<Cart>(path, { signal })).data,
    enabled: isAuthenticated && role === 'CONSUMER',
    staleTime: 30_000,
    retry: false,
  })
}

/** All writes share one lock: every endpoint returns a complete cart snapshot. */
export function useCartChange() {
  const { user } = useAuth()
  const client = useQueryClient()
  const mutationKey = ['cart-change', user?.id]
  const pending = useIsMutating({ mutationKey }) > 0
  const mutation = useMutation({
    mutationKey,
    mutationFn: async ({ change, userId, sessionUser }: { change: CartChange; userId: number; sessionUser: AuthUser }) => {
      const current = useAuthStore.getState()
      if (!current.isAuthenticated || current.user !== sessionUser || current.user.id !== userId || current.user.role !== 'CONSUMER') throw new Error('Consumer session required')
      if (change.kind === 'add') return (await apiClient.post<Cart>(path, { productModelId: change.productModelId, quantity: change.quantity })).data
      if (change.kind === 'quantity') return (await apiClient.patch<Cart>(`${path}/${change.productModelId}`, { quantity: change.quantity })).data
      return (await apiClient.delete<Cart>(change.kind === 'clear' ? path : `${path}/${change.productModelId}`)).data
    },
    onSuccess: async (cart, { userId, sessionUser }) => {
      await client.cancelQueries({ queryKey: cartKey(userId) })
      const current = useAuthStore.getState()
      if (current.user !== sessionUser || !current.isAuthenticated) return
      client.setQueryData(cartKey(userId), cart)
    },
    // POST is cumulative. A blind retry could add the quantity twice.
    retry: false,
  })
  function changeCart(change: CartChange) {
    const session = useAuthStore.getState()
    if (!session.isAuthenticated || session.user?.role !== 'CONSUMER' || client.getMutationCache().find({ mutationKey, status: 'pending' })) return
    mutation.mutate({ change, userId: session.user.id, sessionUser: session.user })
  }
  return { ...mutation, pending, changeCart }
}

export function cartErrorMessage(error: unknown): string {
  if (isAppError(error)) {
    if (error.code === 'CART_CONCURRENT_UPDATE') return '장바구니 변경이 겹쳤습니다. 잠시 후 다시 시도해 주세요.'
    if (error.code === 'CART_QUANTITY_EXCEEDS_STOCK') return '남은 재고보다 많은 수량입니다. 수량을 줄여 다시 시도해 주세요.'
    if (error.code === 'CART_FULL') return '장바구니에는 최대 50개 상품을 담을 수 있습니다. 상품을 제거한 뒤 다시 시도해 주세요.'
    if (error.status === 409) return '재고 또는 장바구니 상태가 변경됐습니다. 확인 후 다시 시도해 주세요.'
    if (error.status === 400) return '수량은 1~99개까지 가능합니다. 장바구니에는 최대 50개 상품을 담을 수 있습니다.'
  }
  return '장바구니 변경에 실패했습니다. 다시 시도해 주세요.'
}
