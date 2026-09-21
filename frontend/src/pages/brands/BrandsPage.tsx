import { useLayoutEffect, useRef } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { BrandCatalog, BrandDetail } from '../../features/brand/BrandCatalog.tsx'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import '../landing/landing.css'
import '../shop/shop.css'
import './brands.css'

export function BrandsPage() {
  const { brandId } = useParams()
  const { pathname, search } = useLocation()
  const previousLocation = useRef({ pathname, search })
  const parsed = Number(brandId)
  const id = brandId && /^\d+$/.test(brandId) && Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
  useLayoutEffect(() => {
    const previous = previousLocation.current
    previousLocation.current = { pathname, search }
    if (previous.pathname === pathname && previous.search !== search) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
    }
  }, [pathname, search])
  return <div className="landing-page shop-page brands-page"><Header isLogoVisible /><main>{brandId === undefined ? <BrandCatalog /> : <BrandDetail key={pathname} brandId={id} />}</main><Footer /></div>
}
