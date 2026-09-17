import { createContext, useContext, type ReactNode } from 'react'
import { useCart, useCartChange } from '../cart/useCart.ts'

export interface ShopContextValue { cartCount: number; addToCart: (productId: string) => void }
const ShopContext = createContext<ShopContextValue | undefined>(undefined)

/** Compatibility facade over server cart data; no local cart state remains. */
export function ShopProvider({ children }: { children: ReactNode }) {
  const cart = useCart()
  const action = useCartChange()
  const value: ShopContextValue = {
    cartCount: cart.data?.items?.length ?? 0,
    addToCart: (productId) => action.changeCart({ kind: 'add', productModelId: Number(productId), quantity: 1 }),
  }
  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useShop(): ShopContextValue {
  const value = useContext(ShopContext)
  if (!value) throw new Error('useShop must be used within ShopProvider')
  return value
}
