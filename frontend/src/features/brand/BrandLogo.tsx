import { useState } from 'react'
import { publicProductImageUrl } from '../product/api/publicProductImageUrl.ts'
import type { PublicBrand } from './publicBrands.ts'

export function BrandLogo({ brand }: { brand: PublicBrand }) {
  const src = brand.logoUrl ? publicProductImageUrl(brand.logoUrl) : null
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const initials = Array.from(brand.brandName.trim()).slice(0, 2).join('').toUpperCase()
  const tone = Array.from(brand.brandName).reduce((hash, character) => (hash * 31 + character.codePointAt(0)!) >>> 0, 0) % 4
  return <div className={`brand-logo brand-logo--${tone}`}>
    {src && failedSrc !== src ? <img src={src} alt={`${brand.brandName} 로고`} loading="lazy" onError={() => setFailedSrc(src)} /> : <span className="brand-logo__fallback" role="img" aria-label={`${brand.brandName} 로고 대체 이미지`}>{initials || 'B'}</span>}
  </div>
}
