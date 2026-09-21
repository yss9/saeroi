import { useLayoutEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { ProductSearch } from '../../features/shop/ProductSearch.tsx'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import '../landing/landing.css'
import '../shop/shop.css'
import './search.css'

export function SearchPage() {
  const { pathname, search } = useLocation()
  const previousLocation = useRef({ pathname, search })
  useLayoutEffect(() => {
    const previous = previousLocation.current
    previousLocation.current = { pathname, search }
    if (previous.pathname === pathname && previous.search !== search) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
    }
  }, [pathname, search])
  return <div className="landing-page shop-page search-page"><Header isLogoVisible /><main><ProductSearch /></main><Footer /></div>
}
