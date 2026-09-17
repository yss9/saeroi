import { createContext, useCallback, useContext, useMemo, useReducer, type ReactNode } from 'react'

export interface ShopContextValue {
  cartCount: number
  addToCart: (productId: string) => void
}

type ShopState = {
  cartCount: number
}

type ShopAction =
  | { type: 'add-to-cart'; productId: string }

const initialState: ShopState = { cartCount: 0 }

function shopReducer(state: ShopState, action: ShopAction): ShopState {
  switch (action.type) {
    case 'add-to-cart':
      return { ...state, cartCount: state.cartCount + 1 }
  }
}

const ShopContext = createContext<ShopContextValue | undefined>(undefined)

export function ShopProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(shopReducer, initialState)

  const addToCart = useCallback((productId: string) => {
    dispatch({ type: 'add-to-cart', productId })
  }, [])

  const value = useMemo<ShopContextValue>(() => ({
    cartCount: state.cartCount,
    addToCart,
  }), [state, addToCart])

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>
}

// The provider and hook intentionally share this public single-file interface.
// eslint-disable-next-line react-refresh/only-export-components
export function useShop(): ShopContextValue {
  const context = useContext(ShopContext)
  if (!context) throw new Error('useShop must be used within ShopProvider')
  return context
}
