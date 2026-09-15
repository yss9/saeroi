import { saeroiValues } from '../content.ts'
import type { CSSProperties } from 'react'

export function WhySaeroi() {
  return (
    <section id="why-saeroi" className="why-saeroi" aria-labelledby="why-saeroi-title">
      <header className="editorial-heading" data-reveal>
        <div>
          <h2 id="why-saeroi-title">WHY SAEROI</h2>
          <p className="editorial-heading__subtitle">Beyond The Product</p>
          <p className="editorial-heading__body">제품 너머의 이야기까지.</p>
        </div>
      </header>

      <div className="why-saeroi__journey">
        {saeroiValues.map((value, index) => (
          <article
            className={`value-story value-story--${value.id}`}
            key={value.id}
            data-reveal
            style={{ '--reveal-delay': `${220 + index * 190}ms` } as CSSProperties}
          >
            <div className="value-story__media">
              <img src={value.image} alt={value.imageAlt} />
            </div>
            <div className="value-story__copy">
              <p>{value.title}</p>
              <h3>{value.koreanTitle}</h3>
              <span>{value.description}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
