import { ArrowRight } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { homepageBrands } from '../content.ts'

export function BrandShowcase() {
  const [activeBrandId, setActiveBrandId] = useState(homepageBrands[0].id)
  const activeBrand = homepageBrands.find((brand) => brand.id === activeBrandId) ?? homepageBrands[0]

  return (
    <section id="brands" className="brand-showcase" aria-labelledby="brands-title">
      <header className="editorial-heading" data-reveal>
        <div>
          <h2 id="brands-title">BRANDS</h2>
          <p className="editorial-heading__subtitle">Meet The Makers</p>
        </div>
        <Link className="text-link" to="/brands">
          EXPLORE BRANDS <ArrowRight aria-hidden="true" />
        </Link>
      </header>

      <div id="brand-list" className="brand-showcase__layout" data-reveal>
        <ul className="brand-showcase__list" aria-label="입점 브랜드">
          {homepageBrands.map((brand) => {
            const isActive = brand.id === activeBrand.id
            return (
              <li key={brand.id}>
                <button
                  type="button"
                  className={isActive ? 'is-active' : undefined}
                  aria-pressed={isActive}
                  onPointerEnter={() => setActiveBrandId(brand.id)}
                  onFocus={() => setActiveBrandId(brand.id)}
                  onClick={() => setActiveBrandId(brand.id)}
                >
                  <span>{brand.name}</span>
                  <ArrowRight aria-hidden="true" />
                </button>
              </li>
            )
          })}
        </ul>

        <article className="brand-showcase__feature" aria-live="polite">
          <div className="brand-showcase__image">
            <img
              key={activeBrand.id}
              src={activeBrand.image}
              alt={activeBrand.imageAlt}
              loading="lazy"
              decoding="async"
              // 이미지를 못 받으면 깨진 아이콘 대신 배경만 남긴다.
              onError={(event) => { event.currentTarget.style.visibility = 'hidden' }}
            />
          </div>
          <div>
            <p>{activeBrand.tagline}</p>
            <span>{activeBrand.description}</span>
          </div>
        </article>
      </div>
    </section>
  )
}
