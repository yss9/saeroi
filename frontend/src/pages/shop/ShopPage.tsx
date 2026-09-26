import { useLayoutEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { ShopCatalog } from '../../features/shop/ShopCatalog.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import { Header } from '../landing/components/Header.tsx'
import '../landing/landing.css'
import './shop.css'

export function ShopPage() {
  const { pathname, search } = useLocation()
  const previousLocation = useRef({ pathname, search })
  useLayoutEffect(() => {
    const previous = previousLocation.current
    previousLocation.current = { pathname, search }
    if (previous.pathname === pathname && previous.search !== search) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
    }
  }, [pathname, search])
  return (
    <div className="landing-page shop-page">
      <Header isLogoVisible />
      <main><ShopCatalog /></main>
      <Footer />
    </div>
  )
}
