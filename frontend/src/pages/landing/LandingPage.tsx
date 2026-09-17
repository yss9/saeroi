import { CaretUp } from '@phosphor-icons/react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { BrandShowcase } from './components/BrandShowcase.tsx'
import { Footer } from './components/Footer.tsx'
import { Header } from './components/Header.tsx'
import { Hero } from './components/Hero.tsx'
import { ShopShowcase } from './components/ShopShowcase.tsx'
import { Story } from './components/Story.tsx'
import { WhySaeroi } from './components/WhySaeroi.tsx'
import './landing.css'

// 6개(+ 하단 Footer)의 "휠 한 번 = 화면 한 칸" 정지 지점.
// WHY SAEROI 는 물리적으로 한 패널이지만, 이미지는 고정한 채 설명 텍스트만 켜는
// 두 번째 정지 지점을 하나 더 가진다 — 그래서 STEP_PANEL 에서만 패널이 겹친다.
const STEP_PANEL = [0, 1, 1, 2, 3, 4, 5] as const
const TOTAL_STEPS = STEP_PANEL.length
const WHEEL_LOCK_MS = 900
const WHEEL_THRESHOLD = 12

// 다른 페이지에서 `/#shop` 처럼 해시로 돌아오는 기존 링크(찜/장바구니 로그인 복귀,
// About 페이지의 "EXPLORE BRANDS" 등)를 위한 초기 정지 지점 매핑.
const HASH_STEP: Record<string, number> = {
  '#why-saeroi': 1,
  '#shop': 3,
  '#brands': 4,
  '#about': 6,
}

export function LandingPage() {
  const location = useLocation()
  const pageRef = useRef<HTMLDivElement>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  const [isHeaderLogoVisible, setIsHeaderLogoVisible] = useState(false)
  const [step, setStep] = useState(() => HASH_STEP[location.hash] ?? 0)
  const revealHeaderLogo = useCallback(() => setIsHeaderLogoVisible(true), [])
  const goToTop = useCallback(() => setStep(0), [])
  const goToNext = useCallback(
    () => setStep((current) => Math.min(TOTAL_STEPS - 1, current + 1)),
    [],
  )

  useEffect(() => {
    const viewport = viewportRef.current
    if (!viewport) return

    let locked = false
    let unlockTimer: number | undefined

    function handleWheel(event: WheelEvent) {
      event.preventDefault()
      if (locked || Math.abs(event.deltaY) < WHEEL_THRESHOLD) return

      const direction = event.deltaY > 0 ? 1 : -1
      let moved = false
      setStep((current) => {
        const next = Math.min(TOTAL_STEPS - 1, Math.max(0, current + direction))
        moved = next !== current
        return next
      })

      if (!moved) return
      locked = true
      unlockTimer = window.setTimeout(() => { locked = false }, WHEEL_LOCK_MS)
    }

    viewport.addEventListener('wheel', handleWheel, { passive: false })
    return () => {
      viewport.removeEventListener('wheel', handleWheel)
      window.clearTimeout(unlockTimer)
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

    // 위/아래 어느 방향으로 다시 지나가도 매번 다시 재생되도록 토글한다 — 휠로
    // 같은 화면을 여러 번 오갈 때마다 이미지가 다시 순서대로 나타난다.
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => entry.target.classList.toggle('is-visible', entry.isIntersecting))
    }, { root: viewportRef.current, rootMargin: '0px 0px -12% 0px', threshold: 0.12 })

    revealTargets.forEach((element) => observer.observe(element))
    return () => observer.disconnect()
  }, [])

  const activePanel = STEP_PANEL[step]
  const isPastHero = step > 0

  return (
    <div className="landing-page" ref={pageRef}>
      <Header isLogoVisible={isHeaderLogoVisible} forceScrolled={isPastHero} />
      <div className="landing-viewport" ref={viewportRef}>
        <main
          className="landing-track"
          style={{ transform: `translateY(-${activePanel * 100}dvh)` }}
        >
          <div className="landing-panel"><Hero onLogoReveal={revealHeaderLogo} onScrollNext={goToNext} /></div>
          <div className="landing-panel"><WhySaeroi showCaptions={step === 2} /></div>
          <div className="landing-panel"><ShopShowcase /></div>
          <div className="landing-panel"><BrandShowcase /></div>
          <div className="landing-panel"><Story /></div>
          <div className="landing-panel landing-panel--footer"><Footer /></div>
        </main>
      </div>
      {isPastHero ? (
        <button
          className="back-to-top"
          type="button"
          aria-label="페이지 맨 위로 이동"
          onClick={goToTop}
        >
          <CaretUp aria-hidden="true" />
        </button>
      ) : null}
    </div>
  )
}
