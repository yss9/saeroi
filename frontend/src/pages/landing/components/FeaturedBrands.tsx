import { ArrowRight } from '@phosphor-icons/react'
import { brands } from '../content.ts'

export function FeaturedBrands() {
  return (
    <section id="featured-brands" className="featured-brands" aria-labelledby="featured-brands-title">
      <div className="section-heading">
        <div>
          <p>FEATURED BRANDS</p>
          <h2 id="featured-brands-title">지속 가능한 가치를 만드는 브랜드</h2>
        </div>
        <a href="#brand-directory">모든 브랜드 보기 <ArrowRight aria-hidden="true" /></a>
      </div>

      <div id="brand-directory" className="featured-brands__grid">
        {brands.map((brand) => (
          <article id={`brand-${brand.id}`} key={brand.id} className="brand-card">
            <a href={`#brand-${brand.id}`} aria-label={`${brand.name} 브랜드 보기`}>
              <img src={brand.image} alt={brand.imageAlt} />
              <h3>{brand.name}</h3>
            </a>
          </article>
        ))}
      </div>
    </section>
  )
}
