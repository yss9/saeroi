import { Heart } from '@phosphor-icons/react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/hooks/useAuth.ts'
import { useWishlistAction, useWishlistIds } from './useWishlist.ts'

export function WishlistButton({ productId, name, className, text, wishedInList = false, returnPath }: {
  productId: number; name: string; className?: string; text?: string; wishedInList?: boolean; returnPath?: string
}) {
  const { isAuthenticated, role } = useAuth()
  const ids = useWishlistIds()
  const action = useWishlistAction(productId)
  const [hovered, setHovered] = useState(false)
  const [notice, setNotice] = useState('')
  const location = useLocation()
  const navigate = useNavigate()
  const wished = isAuthenticated && role === 'CONSUMER' && (wishedInList || ids.data?.includes(productId) === true)
  function click() {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: returnPath ?? `${location.pathname}${location.search}${location.hash}` } } })
    } else if (role !== 'CONSUMER') {
      setNotice('찜은 소비자 계정에서 이용할 수 있습니다.')
    } else if (!wishedInList && !ids.isSuccess) {
      void ids.refetch()
    } else {
      setNotice('')
      action.change(!wished)
    }
  }
  return <>
    <button type="button" className={className} aria-label={text ?? `${name} ${wished ? '찜 해제' : '찜하기'}`} aria-pressed={wished}
      disabled={action.pending || (isAuthenticated && role === 'CONSUMER' && ids.isPending && !wishedInList)} onClick={click}
      onPointerEnter={() => setHovered(true)} onPointerLeave={() => setHovered(false)} onFocus={() => setHovered(true)} onBlur={() => setHovered(false)}>
      <Heart size={22} weight={wished || hovered ? 'fill' : 'regular'} aria-hidden="true" />{text}
    </button>
    {(action.isError || notice) && <span className="wishlist-notice" role="alert">{notice || '찜 변경에 실패했습니다. 다시 눌러 주세요.'}</span>}
    {ids.isError && role === 'CONSUMER' && !wishedInList && <span className="wishlist-notice" role="alert">찜 상태를 불러오지 못했습니다. <button type="button" onClick={() => void ids.refetch()}>찜 상태 다시 시도</button></span>}
  </>
}
