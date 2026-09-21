import { useParams } from 'react-router-dom'
import { PublicProductDetail } from '../../features/product/components/PublicProductDetail.tsx'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import '../landing/landing.css'
import './public-product-detail.css'

export function PublicProductDetailPage() {
  const { productId } = useParams()
  const parsed = Number(productId)
  const id = productId && /^\d+$/.test(productId) && Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
  return <div className="landing-page public-product-page"><Header isLogoVisible /><main><PublicProductDetail key={productId} productId={id} /></main><Footer /></div>
}
