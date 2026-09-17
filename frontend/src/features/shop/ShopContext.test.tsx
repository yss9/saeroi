import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { ShopProvider, useShop } from './ShopContext.tsx'

function Probe() {
  const shop = useShop()
  return (
    <>
      <output aria-label="장바구니 개수">{shop.cartCount}</output>
      <button onClick={() => shop.addToCart('31')}>담기</button>
    </>
  )
}

describe('ShopProvider', () => {
  afterEach(() => { cleanup(); useAuthStore.getState().signOut(); vi.restoreAllMocks() })
  it('exposes the server item count and writes to the shared server cart', async () => {
    useAuthStore.getState().signIn({ id: 1, email: 't@test.com', name: '고객', role: 'CONSUMER', walletAddress: null }, 'token')
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { items: [], totalQuantity: 0, totalPrice: 0 } } as AxiosResponse)
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { items: [{ product: { productModelId: 31 }, quantity: 2 }], totalQuantity: 2, totalPrice: 1000 } } as AxiosResponse)
    const user = userEvent.setup()
    render(<QueryClientProvider client={new QueryClient()}><ShopProvider><Probe /></ShopProvider></QueryClientProvider>)

    expect(screen.getByLabelText('장바구니 개수')).toHaveTextContent('0')

    await user.click(screen.getByRole('button', { name: '담기' }))

    await waitFor(() => expect(screen.getByLabelText('장바구니 개수')).toHaveTextContent('1'))
    expect(apiClient.post).toHaveBeenCalledWith('/v1/consumers/me/cart', { productModelId: 31, quantity: 1 })
  })
})
