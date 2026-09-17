import { gsap } from 'gsap'
import { memo, useLayoutEffect, useRef } from 'react'
import logoMarkup from '../../../assets/saeroi-logo-vector.svg?raw'

// Irregular, overlapping phases: drift → overshoot → exact original shape.
const MOVING_LETTERS = [
  { selector: '#s-motion', x: -155, y: 115, scaleX: .82, scaleY: 1.1, rotation: -3, start: 0, driftX: -48, driftY: -24, peakX: 10, peakY: -6, peakScaleX: 1.04, peakScaleY: .98 },
  { selector: '#a-motion', x: -90, y: -110, scaleX: 1.1, scaleY: .85, rotation: 2.5, start: .035, driftX: -32, driftY: 22, peakX: -6, peakY: 7, peakScaleX: .98, peakScaleY: 1.04 },
  { selector: '#e-motion', x: -25, y: 130, scaleX: .86, scaleY: 1.08, rotation: -2, start: .08, driftX: 30, driftY: 38, peakX: 7, peakY: -8, peakScaleX: 1.04, peakScaleY: .99 },
  { selector: '#r-motion', x: 105, y: -115, scaleX: 1.09, scaleY: .86, rotation: 3, start: .15, driftX: 18, driftY: -34, peakX: -8, peakY: 6, peakScaleX: .98, peakScaleY: 1.03 },
  { selector: '#o-motion', x: 110, y: 100, scaleX: .94, scaleY: 1.06, rotation: 0, start: .2, driftX: 42, driftY: -18, peakX: -6, peakY: -8, peakScaleX: 1.02, peakScaleY: .99 },
  { selector: '#i-motion', x: 135, y: -85, scaleX: .94, scaleY: 1.05, rotation: -1.5, start: .27, driftX: 70, driftY: 12, peakX: 14, peakY: 4, peakScaleX: 1.02, peakScaleY: .99 },
]
const HERO_WORDS = ['WASTE', 'IS', 'NOT', 'THE', 'END.']

interface HeroProps { onLogoReveal: () => void }

// Parent header/scroll state must not replace SVG nodes while GSAP owns them.
const HeroLogo = memo(function HeroLogo() {
  return <div className="hero__logo" role="img" aria-label="SAEROI" dangerouslySetInnerHTML={{ __html: logoMarkup }} />
})

export function Hero({ onLogoReveal }: HeroProps) {
  const heroRef = useRef<HTMLElement>(null)

  useLayoutEffect(() => {
    const hero = heroRef.current
    if (!hero) return
    let spinnerElement: SVGGElement | null = null
    const context = gsap.context(() => {
      const logo = hero.querySelector<HTMLElement>('.hero__logo')
      const letters = MOVING_LETTERS.flatMap((motion) => {
        const element = logo?.querySelector<SVGGraphicsElement>(motion.selector)
        return element ? [{ ...motion, element }] : []
      })
      const oSpinner = logo?.querySelector<SVGGElement>('#o-spinner')
      spinnerElement = oSpinner ?? null
      const tagline = hero.querySelector<HTMLElement>('#hero-title')
      const subcopy = hero.querySelector<HTMLElement>('[data-hero-subcopy]')
      const scrollCue = hero.querySelector<HTMLElement>('[data-hero-scroll]')
      const copy = [tagline, subcopy, scrollCue].filter((element): element is HTMLElement => Boolean(element))

      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
        gsap.set([...letters.map(({ element }) => element), ...copy], { clearProps: 'all', opacity: 1 })
        oSpinner?.removeAttribute('transform')
        onLogoReveal()
        return
      }

      // Measure before transforms: SVG translation uses local, not screen units.
      const keepInViewport = (element: SVGGraphicsElement, x: number, y: number, scaleX: number, scaleY: number) => {
        const bounds = element.getBoundingClientRect()
        const matrix = element.getScreenCTM?.()
        const unitX = Math.abs(matrix?.a ?? 1) || 1
        const unitY = Math.abs(matrix?.d ?? 1) || 1
        const marginX = Math.max(0, scaleX - 1) * bounds.width / 2 + 12
        const marginY = Math.max(0, scaleY - 1) * bounds.height / 2 + 12
        return {
          x: Math.min(Math.max(x, (marginX - bounds.left) / unitX), (window.innerWidth - marginX - bounds.right) / unitX),
          y: Math.min(Math.max(y, (marginY - bounds.top) / unitY), (window.innerHeight - marginY - bounds.bottom) / unitY),
        }
      }
      const motions = letters.map((motion) => ({ ...motion, offset: keepInViewport(motion.element, motion.x, motion.y, motion.scaleX, motion.scaleY) }))
      gsap.set(letters.map(({ element }) => element), { transformBox: 'fill-box', transformOrigin: '50% 50%' })
      const timeline = gsap.timeline()

      motions.forEach(({ element, offset, scaleX, scaleY, rotation, start, driftX, driftY, peakX, peakY, peakScaleX, peakScaleY }) => {
        gsap.set(element, { ...offset, scaleX, scaleY, rotation, opacity: 0 })
        timeline
          .to(element, {
            x: driftX, y: driftY, scaleX: (scaleX + peakScaleX) / 2, scaleY: (scaleY + peakScaleY) / 2,
            rotation: rotation * .4, opacity: 1, duration: .26, ease: 'power1.in',
          }, start)
          .to(element, {
            x: peakX, y: peakY, scaleX: peakScaleX, scaleY: peakScaleY,
            rotation: rotation === 0 ? 0 : -Math.sign(rotation) * .6, duration: .53, ease: 'power3.out',
          }, start + .26)
          .to(element, {
            x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0, duration: .39, ease: 'sine.inOut',
          }, start + .79)
      })

      if (oSpinner && typeof oSpinner.getBBox === 'function') {
        const bounds = oSpinner.getBBox()
        const centerX = bounds.x + bounds.width / 2
        const centerY = bounds.y + bounds.height / 2
        const rotationState = { angle: 0 }
        // Inner group owns one clockwise turn; outer O group owns all deformation.
        timeline.fromTo(rotationState, { angle: 0 }, {
          angle: 360, duration: 1.25, ease: 'power3.out',
          onUpdate: () => oSpinner.setAttribute('transform', `rotate(${rotationState.angle} ${centerX} ${centerY})`),
        }, .2)
      }

      timeline
        .fromTo(tagline, { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: .5, ease: 'power3.out' }, 1.3)
        .call(onLogoReveal, [], 1.45)
        .fromTo(subcopy, { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: .4, ease: 'power3.out' }, 1.4)
        .fromTo(scrollCue, { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: .4, ease: 'power2.out' }, 1.25)
    }, hero)

    return () => { context.revert(); spinnerElement?.removeAttribute('transform') }
  }, [onLogoReveal])

  return (
    <section id="home" className="hero" ref={heroRef} aria-labelledby="hero-title">
      <HeroLogo />
      <div className="hero__message">
        <h1 id="hero-title" aria-label="WASTE IS NOT THE END.">
          {HERO_WORDS.map((word) => <span key={word} data-hero-word aria-hidden="true">{word}</span>)}
        </h1>
        <p data-hero-subcopy>버려진 소재의 다음을 만나는 곳, SAEROI</p>
      </div>
      <a className="hero__scroll" href="#why-saeroi" data-hero-scroll><span>SCROLL</span><i aria-hidden="true" /></a>
    </section>
  )
}
