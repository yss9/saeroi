import { useLayoutEffect } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { PublicProductDetail } from '../../features/product/components/PublicProductDetail.tsx'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import '../landing/landing.css'
import './public-product-detail.css'

export function PublicProductDetailPage() {
  const { productId } = useParams()
  const { pathname } = useLocation()
  const parsed = Number(productId)
  const id = productId && /^\d+$/.test(productId) && Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
  useLayoutEffect(() => { window.scrollTo({ top: 0, left: 0, behavior: 'instant' }) }, [pathname])
  return <div className="landing-page public-product-page"><Header isLogoVisible /><main><PublicProductDetail key={pathname} productId={id} /></main><Footer /></div>
}
