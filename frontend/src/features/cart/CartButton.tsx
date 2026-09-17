import { ShoppingBag } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/hooks/useAuth.ts'
import { cartErrorMessage, useCartChange } from './useCart.ts'
import './cart-action.css'

export function CartButton({ productId, name, className, text, disabled = false, returnPath }: {
  productId: number; name: string; className?: string; text?: string; disabled?: boolean; returnPath?: string
}) {
  const { isAuthenticated, role } = useAuth()
  const action = useCartChange()
  const navigate = useNavigate()
  const location = useLocation()
  const [notice, setNotice] = useState('')
  function click() {
    if (!isAuthenticated) navigate('/login', { state: { from: { pathname: returnPath ?? `${location.pathname}${location.search}${location.hash}` } } })
    else if (role !== 'CONSUMER') setNotice('장바구니는 소비자 계정에서 이용할 수 있습니다.')
    else { setNotice(''); action.changeCart({ kind: 'add', productModelId: productId, quantity: 1 }) }
  }
  return <>
    <button type="button" className={className} aria-label={text ?? `${name} 장바구니 담기`} disabled={disabled || action.pending} onClick={click}><ShoppingBag size={22} aria-hidden="true" />{text}</button>
    {(notice || action.isError) && <span className="cart-action__notice" role="alert">{notice || cartErrorMessage(action.error)}</span>}
    {action.isSuccess && <span className="cart-action__notice" role="status">장바구니에 담았습니다. <Link to="/cart">장바구니 보기</Link></span>}
  </>
}
