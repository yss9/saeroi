import { CaretUp } from '@phosphor-icons/react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { BrandShowcase } from './components/BrandShowcase.tsx'
import { Footer } from './components/Footer.tsx'
import { Header } from './components/Header.tsx'
import { Hero } from './components/Hero.tsx'
import { ShopShowcase } from './components/ShopShowcase.tsx'
import { Story } from './components/Story.tsx'
import { WhySaeroi } from './components/WhySaeroi.tsx'
import './landing.css'

export function LandingPage() {
  const pageRef = useRef<HTMLDivElement>(null)
  const [isHeaderLogoVisible, setIsHeaderLogoVisible] = useState(false)
  const [isBackToTopVisible, setIsBackToTopVisible] = useState(false)
  const revealHeaderLogo = useCallback(() => setIsHeaderLogoVisible(true), [])

  useEffect(() => {
    function updateBackToTopVisibility() {
      const whySection = document.getElementById('why-saeroi')
      const threshold = Math.max(1, (whySection?.offsetTop ?? window.innerHeight) - 120)
      setIsBackToTopVisible(window.scrollY >= threshold)
    }

    updateBackToTopVisibility()
    window.addEventListener('scroll', updateBackToTopVisibility, { passive: true })
    window.addEventListener('resize', updateBackToTopVisibility)

    return () => {
      window.removeEventListener('scroll', updateBackToTopVisibility)
      window.removeEventListener('resize', updateBackToTopVisibility)
    }
  }, [])

  useEffect(() => {
    const page = pageRef.current
    if (!page) return

    const revealTargets = Array.from(page.querySelectorAll<HTMLElement>('[data-reveal]'))
    const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

    if (prefersReducedMotion || !('IntersectionObserver' in window)) {
      revealTargets.forEach((element) => element.classList.add('is-visible'))
      return
    }

    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        entry.target.classList.add('is-visible')
        observer.unobserve(entry.target)
      })
    }, { rootMargin: '0px 0px -12% 0px', threshold: 0.12 })

    revealTargets.forEach((element) => observer.observe(element))
    return () => observer.disconnect()
  }, [])

  return (
    <div className="landing-page" ref={pageRef}>
      <Header isLogoVisible={isHeaderLogoVisible} />
      <main>
        <Hero onLogoReveal={revealHeaderLogo} />
        <WhySaeroi />
        <ShopShowcase />
        <BrandShowcase />
        <Story />
      </main>
      <Footer />
      {isBackToTopVisible ? (
        <button
          className="back-to-top"
          type="button"
          aria-label="페이지 맨 위로 이동"
          onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
        >
          <CaretUp aria-hidden="true" />
        </button>
      ) : null}
    </div>
  )
}
