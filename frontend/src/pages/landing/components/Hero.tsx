import { gsap } from 'gsap'
import { useLayoutEffect, useRef } from 'react'
import logoMarkup from '../../../assets/saeroi-logo-vector.svg?raw'

const MOVING_LETTERS = [
  {
    selector: '#s-motion', x: -82, y: 82, rotation: -4, scale: 0.86,
    start: 0, duration: 1.28, settleX: 3, settleY: -7, settleRotation: 0.35,
  },
  {
    selector: '#a-motion', x: -48, y: -86, rotation: 3, scale: 0.84,
    start: 0.07, duration: 1.18, settleX: -2, settleY: 6, settleRotation: -0.3,
  },
  {
    selector: '#e-motion', x: -16, y: 90, rotation: -3, scale: 0.92,
    start: 0.12, duration: 1.06, settleX: 2, settleY: -7, settleRotation: 0.3,
  },
  {
    selector: '#r-motion', x: 58, y: -82, rotation: 4, scale: 0.87,
    start: 0.17, duration: 1.22, settleX: -3, settleY: 6, settleRotation: -0.35,
  },
  {
    selector: '#i-motion', x: 88, y: -68, rotation: -3, scale: 0.88,
    start: 0.3, duration: 1.1, settleX: -2, settleY: 5, settleRotation: 0.3,
  },
]
const HERO_WORDS = ['WASTE', 'IS', 'NOT', 'THE', 'END.']

interface HeroProps {
  onLogoReveal: () => void
}

export function Hero({ onLogoReveal }: HeroProps) {
  const heroRef = useRef<HTMLElement>(null)

  useLayoutEffect(() => {
    const hero = heroRef.current
    if (!hero) return

    const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    let spinnerElement: SVGGElement | null = null
    const context = gsap.context(() => {
      const logo = hero.querySelector<HTMLElement>('.hero__logo')
      const letters = MOVING_LETTERS
        .map(({ selector, ...motion }) => ({
          element: logo?.querySelector<SVGElement>(selector),
          ...motion,
        }))
        .filter(
          (letter): letter is {
            element: SVGElement
            x: number
            y: number
            rotation: number
            scale: number
            start: number
            duration: number
            settleX: number
            settleY: number
            settleRotation: number
          } =>
            Boolean(letter.element),
        )
      const oMotion = logo?.querySelector<SVGGElement>('#o-motion')
      const oSpinner = logo?.querySelector<SVGGElement>('#o-spinner')
      spinnerElement = oSpinner ?? null
      const heroWords = Array.from(hero.querySelectorAll<HTMLElement>('[data-hero-word]'))
      const subcopy = hero.querySelector<HTMLElement>('[data-hero-subcopy]')
      const scrollCue = hero.querySelector<HTMLElement>('[data-hero-scroll]')
      const copy = [...heroWords, subcopy, scrollCue].filter(
        (element): element is HTMLElement => Boolean(element),
      )

      if (prefersReducedMotion) {
        gsap.set([...letters.map(({ element }) => element), oMotion, ...copy], {
          clearProps: 'all',
          opacity: 1,
        })
        oSpinner?.removeAttribute('transform')
        onLogoReveal()
        return
      }

      const movingElements = [
        ...letters.map(({ element }) => element),
        ...(oMotion ? [oMotion] : []),
      ]

      gsap.set(movingElements, {
        transformBox: 'fill-box',
        transformOrigin: '50% 50%',
      })

      const timeline = gsap.timeline()
      const keepInViewport = (element: SVGElement, x: number, y: number) => {
        const bounds = element.getBoundingClientRect()
        const padding = 12

        return {
          x: Math.min(Math.max(x, padding - bounds.left), window.innerWidth - padding - bounds.right),
          y: Math.min(Math.max(y, padding - bounds.top), window.innerHeight - padding - bounds.bottom),
        }
      }

      letters.forEach(({
        element,
        x,
        y,
        rotation,
        scale,
        start,
        duration,
        settleX,
        settleY,
        settleRotation,
      }) => {
        const rippleDuration = 0.28
        const settleDuration = 0.2
        const startOffset = keepInViewport(element, x, y)

        timeline.fromTo(
          element,
          {
            opacity: 0,
            x: startOffset.x,
            y: startOffset.y,
            rotation,
            scale,
            filter: 'blur(4px)',
          },
          {
            keyframes: [
              {
                opacity: 1,
                x: settleX * 2.5,
                y: settleY * 2.2,
                rotation: settleRotation * 1.8,
                scale: 1.025,
                filter: 'blur(0px)',
                duration: duration - rippleDuration - settleDuration,
                ease: 'power2.out',
              },
              {
                x: settleX * -1.3,
                y: settleY * -1.2,
                rotation: settleRotation * -1,
                scale: 0.992,
                duration: rippleDuration,
                ease: 'sine.inOut',
              },
              {
                x: 0,
                y: 0,
                rotation: 0,
                scale: 1,
                duration: settleDuration,
                ease: 'power2.out',
              },
            ],
          },
          start,
        )
      })

      if (oMotion) {
        const startOffset = keepInViewport(oMotion, 70, 80)

        timeline.fromTo(
          oMotion,
          {
            opacity: 0,
            x: startOffset.x,
            y: startOffset.y,
            scale: 0.9,
            filter: 'blur(4px)',
          },
          {
            keyframes: [
              {
                opacity: 1,
                x: -6,
                y: -15,
                scale: 1.02,
                filter: 'blur(0px)',
                duration: 0.87,
                ease: 'power2.out',
              },
              {
                x: 4,
                y: 8,
                scale: 0.993,
                duration: 0.28,
                ease: 'sine.inOut',
              },
              {
                x: 0,
                y: 0,
                scale: 1,
                duration: 0.2,
                ease: 'power2.out',
              },
            ],
          },
          0.18,
        )
      }

      if (oSpinner && typeof oSpinner.getBBox === 'function') {
        const bounds = oSpinner.getBBox()
        const centerX = bounds.x + bounds.width / 2
        const centerY = bounds.y + bounds.height / 2
        const rotationState = { angle: 0 }

        timeline.fromTo(
          rotationState,
          { angle: 0 },
          {
            angle: 360,
            duration: 2.05,
            ease: 'power3.out',
            onUpdate: () => {
              oSpinner.setAttribute(
                'transform',
                `rotate(${rotationState.angle} ${centerX} ${centerY})`,
              )
            },
          },
          0.18,
        )
      }

      timeline
        .call(onLogoReveal, [], 2.24)
        .fromTo(
          heroWords,
          { opacity: 0, y: 14, scale: 0.94, filter: 'blur(3px)' },
          {
            keyframes: [
              {
                opacity: 1,
                y: -3,
                scale: 1.025,
                filter: 'blur(0px)',
                duration: 0.34,
                ease: 'power2.out',
              },
              {
                y: 1.5,
                scale: 0.995,
                duration: 0.12,
                ease: 'sine.inOut',
              },
              {
                y: 0,
                scale: 1,
                duration: 0.12,
                ease: 'power2.out',
              },
            ],
            stagger: 0.055,
          },
          1.34,
        )
        .fromTo(
          subcopy,
          { opacity: 0, y: 12 },
          { opacity: 1, y: 0, duration: 0.5, ease: 'power3.out' },
          2.36,
        )
        .fromTo(
          scrollCue,
          { opacity: 0, y: 12 },
          { opacity: 1, y: 0, duration: 0.48, ease: 'power2.out' },
          2.68,
        )
    }, hero)

    return () => {
      context.revert()
      spinnerElement?.removeAttribute('transform')
    }
  }, [onLogoReveal])

  return (
    <section id="home" className="hero" ref={heroRef} aria-labelledby="hero-title">
      <div
        className="hero__logo"
        role="img"
        aria-label="SAEROI"
        dangerouslySetInnerHTML={{ __html: logoMarkup }}
      />

      <div className="hero__message">
        <h1 id="hero-title" aria-label="WASTE IS NOT THE END.">
          {HERO_WORDS.map((word) => (
            <span key={word} data-hero-word aria-hidden="true">{word}</span>
          ))}
        </h1>
        <p data-hero-subcopy>버려진 소재의 다음을 만나는 곳, SAEROI</p>
      </div>

      <a className="hero__scroll" href="#why-saeroi" data-hero-scroll>
        <span>SCROLL</span>
        <i aria-hidden="true" />
      </a>
    </section>
  )
}
