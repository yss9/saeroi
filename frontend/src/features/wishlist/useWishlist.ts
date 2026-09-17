import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/hooks/useAuth.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { apiClient } from '../../services/apiClient.ts'
import type { PageResponse } from '../../types/api.types.ts'
import type { PublicProductModel } from '../product/types/publicProduct.types.ts'

export interface WishlistItem { product: PublicProductModel; wishedAt: string; purchasable: boolean }
export const wishlistKeys = {
  ids: (userId: number | undefined) => ['wishlist', userId, 'ids'] as const,
  lists: (userId: number | undefined) => ['wishlist', userId, 'list'] as const,
}
const path = '/v1/consumers/me/wishlist'

export function useWishlistIds() {
  const { user, isAuthenticated, role } = useAuth()
  return useQuery({
    queryKey: wishlistKeys.ids(user?.id),
    queryFn: async ({ signal }) => (await apiClient.get<number[]>(`${path}/product-ids`, { signal })).data,
    enabled: isAuthenticated && role === 'CONSUMER',
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
  })
}

export function useWishlistList(page: number) {
  const { user, isAuthenticated, role } = useAuth()
  return useQuery({
    queryKey: [...wishlistKeys.lists(user?.id), page],
    queryFn: async ({ signal }) => (await apiClient.get<PageResponse<WishlistItem>>(path, { params: { page, size: 12 }, signal })).data,
    enabled: isAuthenticated && role === 'CONSUMER',
    retry: false,
  })
}

export function useWishlistAction(productId: number) {
  const client = useQueryClient()
  const { user } = useAuth()
  const userId = user?.id
  const sessionToken = useAuthStore.getState().accessToken
  const key = ['wishlist-action', userId, productId]
  const pending = useIsMutating({ mutationKey: key }) > 0
  const mutation = useMutation({
    mutationKey: key,
    mutationFn: async (add: boolean) => {
      if (add) await apiClient.post(path, { productModelId: productId })
      else await apiClient.delete(`${path}/${productId}`)
    },
    onSuccess: async (_data, add) => {
      // Ignore a late response after logout or a different login session.
      const session = useAuthStore.getState()
      if (session.user?.id !== userId || !session.isAuthenticated) return
      if (session.accessToken !== sessionToken) {
        void client.invalidateQueries({ queryKey: wishlistKeys.ids(userId) })
        void client.invalidateQueries({ queryKey: wishlistKeys.lists(userId) })
        return
      }
      await client.cancelQueries({ queryKey: wishlistKeys.ids(userId) })
      if (useAuthStore.getState().user?.id !== userId || useAuthStore.getState().accessToken !== sessionToken) return
      if (client.getQueryData(wishlistKeys.ids(userId)) !== undefined) {
        client.setQueryData<number[]>(wishlistKeys.ids(userId), (ids = []) => add ? [...new Set([...ids, productId])] : ids.filter((id) => id !== productId))
      } else {
        void client.invalidateQueries({ queryKey: wishlistKeys.ids(userId) })
      }
      void client.invalidateQueries({ queryKey: wishlistKeys.lists(userId) })
    },
    retry: false,
  })
  function change(add: boolean) {
    if (client.getMutationCache().find({ mutationKey: key, status: 'pending' })) return
    mutation.mutate(add)
  }
  return { ...mutation, change, pending }
}
