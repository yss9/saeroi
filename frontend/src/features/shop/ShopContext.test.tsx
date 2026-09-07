import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ShopProvider, useShop } from './ShopContext.tsx'

function Probe() {
  const shop = useShop()
  return (
    <>
      <output aria-label="장바구니 개수">{shop.cartCount}</output>
      <output aria-label="찜 상태">{String(shop.wishlist.has('denim-bag'))}</output>
      <button onClick={() => shop.toggleWishlist('denim-bag')}>찜</button>
      <button onClick={() => shop.addToCart('denim-bag')}>담기</button>
    </>
  )
}

describe('ShopProvider', () => {
  it('starts from the reference cart state and shares updates with consumers', async () => {
    const user = userEvent.setup()
    render(<ShopProvider><Probe /></ShopProvider>)

    expect(screen.getByLabelText('장바구니 개수')).toHaveTextContent('2')

    await user.click(screen.getByRole('button', { name: '찜' }))
    await user.click(screen.getByRole('button', { name: '담기' }))

    expect(screen.getByLabelText('찜 상태')).toHaveTextContent('true')
    expect(screen.getByLabelText('장바구니 개수')).toHaveTextContent('3')
  })
})
