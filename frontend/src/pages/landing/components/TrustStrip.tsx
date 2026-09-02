import { QrCode, SealCheck, SuitcaseSimple } from '@phosphor-icons/react'
import { trustItems, type TrustItem } from '../content.ts'

const icons: Record<TrustItem['id'], typeof SealCheck> = {
  'verified-source': SealCheck,
  'material-journey': SuitcaseSimple,
  'qr-tracking': QrCode,
}

export function TrustStrip() {
  return (
    <section id="trust" className="trust-strip" aria-label="SAEROI 제품 신뢰 정보">
      {trustItems.map((item) => {
        const Icon = icons[item.id]

        return (
          <article key={item.id} className="trust-strip__item">
            <span className="trust-strip__icon" aria-hidden="true">
              <Icon />
            </span>
            <div>
              <h2>{item.title}</h2>
              <p>{item.description}</p>
            </div>
          </article>
        )
      })}
    </section>
  )
}
