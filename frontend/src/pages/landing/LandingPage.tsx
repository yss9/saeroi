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
// 터치 스와이프로 한 칸 넘기는 데 필요한 최소 세로 이동 거리(px).
const SWIPE_THRESHOLD = 50

// 다른 페이지에서 `/#shop` 처럼 해시로 돌아오는 기존 링크(찜/장바구니 로그인 복귀,
// About 페이지의 "EXPLORE BRANDS", 마이페이지 머리말의 "브랜드"/"스토리" 등)를 위한
// 정지 지점 매핑. `#about` 은 푸터(id="about")다 — 스토리 섹션은 `#story`.
const HASH_STEP: Record<string, number> = {
  '#why-saeroi': 1,
  '#shop': 3,
  '#brands': 4,
  '#story': 5,
  '#about': 6,
}

function clampStep(step: number) {
  return Math.min(TOTAL_STEPS - 1, Math.max(0, step))
}

/** 입력 중인 요소(텍스트 필드·셀렉트·contenteditable)에서 온 키 입력은 페이지 넘김에 쓰지 않는다. */
function isEditableTarget(target: EventTarget | null) {
  return target instanceof Element
    && target.closest('input, textarea, select, [contenteditable=""], [contenteditable="true"]') !== null
}

/**
 * 푸터 패널은 한 화면보다 길 수 있어 자체 스크롤(overflow-y: auto)을 가진다.
 * 그 안에서 진행 방향으로 아직 스크롤할 여지가 남아 있으면 페이지를 넘기지 않고
 * 푸터가 스스로 스크롤하게 둔다.
 */
function canScrollFooter(target: EventTarget | null, direction: 1 | -1) {
  if (!(target instanceof Element)) return false
  const footer = target.closest<HTMLElement>('.landing-panel--footer')
  if (!footer) return false
  const remaining = footer.scrollHeight - footer.clientHeight
  if (remaining <= 0) return false
  return direction > 0 ? footer.scrollTop < remaining - 1 : footer.scrollTop > 0
}

export function LandingPage() {
  const location = useLocation()
  const pageRef = useRef<HTMLDivElement>(null)
  const viewportRef = useRef<HTMLDivElement>(null)
  const [isHeaderLogoVisible, setIsHeaderLogoVisible] = useState(false)
  const [step, setStep] = useState(() => HASH_STEP[location.hash] ?? 0)
  // 휠·키보드·터치 리스너가 stale 클로저 대신 항상 현재 정지 지점을 동기적으로 읽도록
  // state 를 ref 에 비춘다. 이동은 전부 moveTo 로 해서 둘이 어긋나지 않게 한다.
  const stepRef = useRef(step)
  const moveTo = useCallback((next: number) => {
    const target = clampStep(next)
    stepRef.current = target
    setStep(target)
  }, [])
  const revealHeaderLogo = useCallback(() => setIsHeaderLogoVisible(true), [])
  const goToTop = useCallback(() => moveTo(0), [moveTo])
  const goToNext = useCallback(() => moveTo(stepRef.current + 1), [moveTo])

  // 마운트 이후 해시만 바뀐 경우(`/#brands` → `/#story` 등)에도 해당 정지 지점으로 옮긴다.
  // 렌더 중 상태 조정이므로 ref 는 만지지 않고(아래 effect 가 따라잡는다) state 만 바꾼다.
  const [syncedHash, setSyncedHash] = useState(location.hash)
  if (syncedHash !== location.hash) {
    setSyncedHash(location.hash)
    const target = HASH_STEP[location.hash]
    if (target !== undefined) setStep(clampStep(target))
  }

  useEffect(() => {
    stepRef.current = step
  }, [step])

  useEffect(() => {
    const viewport = viewportRef.current
    if (!viewport) return

    let locked = false
    let unlockTimer: number | undefined
    let touchStartY: number | null = null

    // 다음 정지 지점은 setState 업데이터 안이 아니라 ref 에서 동기적으로 계산하고,
    // 잠금을 먼저 건 뒤 setStep 한다 — 업데이터가 나중에 실행되면 `moved` 가 아직
    // false 라 잠금이 걸리지 않아 휠 한 번에 여러 칸이 넘어가던 경쟁을 막는다.
    function goTo(next: number) {
      if (locked) return
      const target = clampStep(next)
      if (target === stepRef.current) return
      locked = true
      unlockTimer = window.setTimeout(() => { locked = false }, WHEEL_LOCK_MS)
      moveTo(target)
    }

    function handleWheel(event: WheelEvent) {
      const direction: 1 | -1 = event.deltaY > 0 ? 1 : -1
      if (canScrollFooter(event.target, direction)) return
      event.preventDefault()
      if (Math.abs(event.deltaY) < WHEEL_THRESHOLD) return
      goTo(stepRef.current + direction)
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return
      if (isEditableTarget(event.target)) return

      let next: number
      switch (event.key) {
        case 'ArrowDown':
        case 'PageDown':
          next = stepRef.current + 1
          break
        case ' ':
          // 버튼·링크에 포커스가 있으면 스페이스는 그 요소를 누르는 키다.
          if (event.target instanceof Element && event.target.closest('button, a, [role="button"]')) return
          next = stepRef.current + 1
          break
        case 'ArrowUp':
        case 'PageUp':
          next = stepRef.current - 1
          break
        case 'Home':
          next = 0
          break
        case 'End':
          next = TOTAL_STEPS - 1
          break
        default:
          return
      }
      event.preventDefault()
      goTo(next)
    }

    function handleTouchStart(event: TouchEvent) {
      touchStartY = event.changedTouches[0]?.clientY ?? null
    }

    function handleTouchEnd(event: TouchEvent) {
      const startY = touchStartY
      touchStartY = null
      const endY = event.changedTouches[0]?.clientY
      if (startY === null || endY === undefined) return

      const delta = startY - endY
      if (Math.abs(delta) < SWIPE_THRESHOLD) return
      const direction: 1 | -1 = delta > 0 ? 1 : -1
      if (canScrollFooter(event.target, direction)) return
      goTo(stepRef.current + direction)
    }

    viewport.addEventListener('wheel', handleWheel, { passive: false })
    viewport.addEventListener('touchstart', handleTouchStart, { passive: true })
    viewport.addEventListener('touchend', handleTouchEnd, { passive: true })
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      viewport.removeEventListener('wheel', handleWheel)
      viewport.removeEventListener('touchstart', handleTouchStart)
      viewport.removeEventListener('touchend', handleTouchEnd)
      window.removeEventListener('keydown', handleKeyDown)
      window.clearTimeout(unlockTimer)
    }
  }, [moveTo])

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
    <div className="landing-page landing-page--paginated" ref={pageRef}>
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
