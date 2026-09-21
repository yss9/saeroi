import { ArrowRight, CaretUp, Check } from '@phosphor-icons/react'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import logoUrl from '../../assets/saeroi-logo-vector.svg'
import { siteAssetUrl } from '../../utils/siteAssetUrl.ts'
import { Footer } from '../landing/components/Footer.tsx'
import { Header } from '../landing/components/Header.tsx'
import '../landing/landing.css'
import './about.css'
import { adjacentScrollStop } from '../../utils/scrollStops.ts'

const connectionWords = [
  { label: 'MATERIALS', color: '#216a9f' },
  { label: 'BRANDS', color: '#ff9b69' },
  { label: 'PEOPLE', color: '#174b70' },
]

const journeySteps = [
  {
    number: '01',
    label: 'MATERIAL',
    copy: <>버려진 원단과 재고 소재가<br />다시 활용할 수 있는 자원이 됩니다.</>,
    image: siteAssetUrl('/images/landing/journey/trace-material.webp'),
    alt: '재사용을 기다리는 폐섬유와 데드스톡 소재',
  },
  {
    number: '02',
    label: 'PROCESS',
    copy: <>검증된 소재는 가공과 생산 과정을 거쳐<br />새로운 형태를 만듭니다.</>,
    image: siteAssetUrl('/images/landing/journey/transform-process.webp'),
    alt: '회수 소재를 새로운 제품으로 만드는 봉제 과정',
  },
  {
    number: '03',
    label: 'PRODUCT',
    copy: <>소재의 이전 여정을 품은 채<br />새로운 제품으로 다시 태어납니다.</>,
    image: siteAssetUrl('/images/landing/product-work-jacket.webp'),
    alt: '완성된 업사이클 데님 워크 재킷',
  },
]

const traceSteps = ['MATERIAL', 'VERIFIED', 'PROCESS', 'PRODUCT', 'QR PASSPORT']

export function AboutPage() {
  const pageRef = useRef<HTMLDivElement>(null)
  const [isBackToTopVisible, setIsBackToTopVisible] = useState(false)

  useEffect(() => {
    const page = pageRef.current
    if (!page || !window.matchMedia || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    // 휠은 한 제스처당 콘텐츠 정지점 하나만 이동. 터치·키보드·스크롤바는 기본 동작 유지.
    const previousScrollBehavior = document.documentElement.style.scrollBehavior
    document.documentElement.style.scrollBehavior = 'auto'
    let moving = false
    let lastWheelAt = 0
    let scrollTween: gsap.core.Tween | undefined

    function documentTop(element: HTMLElement) {
      let top = 0
      let node: HTMLElement | null = element
      while (node) {
        top += node.offsetTop
        node = node.offsetParent as HTMLElement | null
      }
      return top
    }

    function getStops() {
      const header = page!.querySelector<HTMLElement>('.site-header')?.offsetHeight ?? 78
      const maximum = Math.max(0, document.documentElement.scrollHeight - window.innerHeight)
      const anchors = Array.from(page!.querySelectorAll<HTMLElement>('[data-about-stop], .site-footer'))
      const stops = [0, maximum]
      // 화면보다 긴 이미지/문구도 건너뛰지 않도록 읽을 수 있는 내부 정지점 확보.
      // 헤더보다 낮은 아주 짧은 뷰포트에서는 0 이하가 되어 아래 for 가 끝나지 않으므로 건너뛴다.
      const visibleHeight = window.innerHeight - header - 48

      anchors.forEach((element) => {
        const top = Math.max(0, documentTop(element) - header - 24)
        stops.push(top)
        if (visibleHeight <= 0) return
        for (let offset = visibleHeight; offset < element.offsetHeight - visibleHeight * 0.3; offset += visibleHeight) {
          stops.push(top + offset)
        }
      })

      const connection = page!.querySelector<HTMLElement>('.about-connection')
      if (connection) {
        const top = documentTop(connection)
        const travel = Math.max(0, connection.offsetHeight - window.innerHeight)
        stops.push(top + travel * 0.2, top + travel * 0.55, top + travel * 0.9)
      }

      return [...new Set(stops.map((stop) => Math.min(maximum, Math.round(stop))))]
        .sort((a, b) => a - b)
        .reduce<number[]>((result, stop) => {
          // 같은 데스크톱 행의 문구와 이미지는 하나의 이동 지점으로 취급.
          if (!result.length || stop - result[result.length - 1] > 160) result.push(stop)
          return result
        }, [])
    }

    // 정지점은 레이아웃이 바뀔 때(리사이즈·이미지 로드)만 다시 계산한다 — 휠 이벤트마다
    // 전체 DOM 을 훑고 offsetTop 을 읽으면 그때마다 강제 레이아웃이 걸린다.
    let stops: number[] | undefined
    function invalidateStops() { stops = undefined }
    const images = Array.from(page.querySelectorAll('img'))
    window.addEventListener('resize', invalidateStops)
    images.forEach((image) => image.addEventListener('load', invalidateStops))

    function onWheel(event: WheelEvent) {
      if (event.ctrlKey || event.shiftKey || Math.abs(event.deltaX) > Math.abs(event.deltaY) || Math.abs(event.deltaY) < 2) return
      if ((event.target as Element)?.closest('input, textarea, select, [role="dialog"], .site-header__account-popover')) return

      const now = performance.now()
      const previousWheelAt = lastWheelAt
      lastWheelAt = now
      if (moving) {
        event.preventDefault()
        return
      }
      stops ??= getStops()
      const target = adjacentScrollStop(stops, window.scrollY, Math.sign(event.deltaY))
      if (target === undefined) return
      event.preventDefault()
      if (previousWheelAt !== 0 && now - previousWheelAt < 180) return

      moving = true
      const position = { y: window.scrollY }
      scrollTween = gsap.to(position, {
        y: target,
        duration: 1,
        ease: 'power2.inOut',
        onUpdate: () => window.scrollTo({ top: position.y, behavior: 'auto' }),
        onComplete: () => { moving = false },
      })
    }

    window.addEventListener('wheel', onWheel, { passive: false })
    return () => {
      window.removeEventListener('wheel', onWheel)
      window.removeEventListener('resize', invalidateStops)
      images.forEach((image) => image.removeEventListener('load', invalidateStops))
      scrollTween?.kill()
      document.documentElement.style.scrollBehavior = previousScrollBehavior
    }
  }, [])

  useEffect(() => {
    const page = pageRef.current
    if (!page || !window.matchMedia || window.matchMedia('(prefers-reduced-motion: reduce)').matches || !window.IntersectionObserver) return

    page.classList.add('about-page--animated')
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => entry.target.classList.toggle('is-in-view', entry.isIntersecting))
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' })
    page.querySelectorAll('[data-about-reveal]').forEach((element) => observer.observe(element))
    return () => {
      observer.disconnect()
      page.classList.remove('about-page--animated')
    }
  }, [])

  useEffect(() => {
    // 기준 요소·문턱값은 한 번 구해 두고 리사이즈 때만 다시 잰다 — 스크롤 이벤트마다
    // querySelector + offsetTop 을 읽지 않는다.
    const perspectiveSection = pageRef.current?.querySelector<HTMLElement>('.about-perspective') ?? null
    let threshold = 0

    function measureThreshold() {
      threshold = Math.max(1, (perspectiveSection?.offsetTop ?? window.innerHeight) + 120)
    }

    function updateBackToTopVisibility() {
      setIsBackToTopVisible(window.scrollY >= threshold)
    }

    function handleResize() {
      measureThreshold()
      updateBackToTopVisibility()
    }

    handleResize()
    window.addEventListener('scroll', updateBackToTopVisibility, { passive: true })
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('scroll', updateBackToTopVisibility)
      window.removeEventListener('resize', handleResize)
    }
  }, [])

  useEffect(() => {
    const page = pageRef.current
    if (!page) return

    const prefersReducedMotion =
      typeof window.matchMedia !== 'function' ||
      window.matchMedia('(prefers-reduced-motion: reduce)').matches

    if (prefersReducedMotion) {
      page.classList.add('about-page--reduced-motion')
      return
    }

    gsap.registerPlugin(ScrollTrigger)

    const context = gsap.context(() => {
      gsap.to('.about-hero__panel', {
        clipPath: 'inset(0 0 76% 0)',
        ease: 'none',
        scrollTrigger: {
          trigger: '.about-hero',
          start: 'top top',
          end: 'bottom bottom',
          scrub: 0.65,
        },
      })

      gsap.to('.about-hero__words', {
        yPercent: -12,
        opacity: 0.18,
        ease: 'none',
        scrollTrigger: {
          trigger: '.about-hero',
          start: 'top top',
          end: 'bottom bottom',
          scrub: 0.65,
        },
      })

      const wordTimeline = gsap.timeline({
        scrollTrigger: {
          trigger: '.about-connection',
          start: 'top top',
          end: 'bottom bottom',
          scrub: 0.5,
        },
      })

      connectionWords.forEach((word, index) => {
        wordTimeline
          .to(`.about-connection__word:nth-child(${index + 1})`, {
            color: word.color,
            opacity: 1,
            duration: 1,
          })
          .to(`.about-connection__word:nth-child(${index + 1})`, {
            color: '#b7b2a9',
            opacity: 0.42,
            duration: 1,
          }, '+=0.4')
      })

      gsap.utils.toArray<HTMLElement>('[data-about-parallax]').forEach((element, index) => {
        gsap.fromTo(
          element,
          { y: 36 + index * 9 },
          {
            y: -16,
            ease: 'none',
            scrollTrigger: {
              trigger: element,
              start: 'top bottom',
              end: 'bottom top',
              scrub: 0.8,
            },
          },
        )
      })
    }, page)

    return () => context.revert()
  }, [])

  return (
    <div className="landing-page about-page" ref={pageRef}>
      <Header isLogoVisible scrollToneViewportRatio={0.72} />

      <main>
        <section className="about-hero" aria-labelledby="about-hero-title">
          <div className="about-hero__panel">
            <h1 className="about-hero__words" id="about-hero-title">
              <span>WHO</span>
              <span>WE</span>
              <span>ARE</span>
            </h1>
          </div>
        </section>

        <section className="about-perspective" aria-labelledby="about-perspective-title">
          <div className="about-perspective__copy" data-about-reveal data-about-stop>
            <p className="about-kicker">THE END OF USE IS NOT THE END OF VALUE.</p>
            <h2 id="about-perspective-title">WE SEE WASTE<br />DIFFERENTLY.</h2>
            <div className="about-body-copy">
              <p>버려졌다는 이유만으로<br />소재의 가치가 끝나는 것은 아닙니다.</p>
              <p>SAEROI는 남겨진 소재가 다시 선택되고,<br />가공되고, 새로운 제품으로 이어지는 과정을 연결합니다.</p>
            </div>
          </div>
          <figure className="about-perspective__media" data-about-reveal data-about-stop>
            <img src={siteAssetUrl('/images/landing/journey/trace-material.webp')} alt="다음 쓰임을 기다리는 회수 섬유 소재" />
          </figure>
        </section>

        <section className="about-connection" aria-labelledby="about-connection-title">
          <div className="about-connection__sticky" data-about-reveal>
            <p className="about-kicker">ONE PLATFORM · A CONNECTED CIRCULAR JOURNEY</p>
            <h2 className="about-connection__words" id="about-connection-title">
              {connectionWords.map((word) => <span className="about-connection__word" key={word.label}>{word.label}</span>)}
            </h2>
            <p className="about-connection__copy">
              SAEROI는 버려진 소재와<br />새로운 제품을 만드는 브랜드,<br />그리고 더 나은 선택을 원하는 사람들을 연결합니다.
            </p>
          </div>
        </section>

        <section className="about-journey" aria-labelledby="about-journey-title">
          <header data-about-reveal data-about-stop>
            <p className="about-kicker">A NEW FORM, WITH ITS PAST INTACT</p>
            <h2 id="about-journey-title">FROM MATERIAL<br />TO PRODUCT</h2>
          </header>
          <div className="about-journey__editorial">
            {journeySteps.map((step) => (
              <article className="about-journey__step" key={step.label} data-about-reveal data-about-stop>
                <figure data-about-parallax>
                  <img src={step.image} alt={step.alt} />
                </figure>
                <div>
                  <span>{step.number}</span>
                  <h3>{step.label}</h3>
                  <p>{step.copy}</p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="about-trace" aria-labelledby="about-trace-title">
          <div className="about-trace__intro" data-about-reveal data-about-stop>
            <p className="about-kicker">BLOCKCHAIN-BACKED TRACEABILITY</p>
            <h2 id="about-trace-title">EVERY MATERIAL<br />LEAVES<br /><span className="about-trace__last-line">A TRACE.</span></h2>
            <p className="about-trace__subtitle">Transparency beyond the product</p>
            <div className="about-body-copy">
              <p>소재가 어디에서 왔는지,<br />누구의 손을 거쳤는지,<br />어떻게 가공되고 제품이 되었는지.</p>
              <p>SAEROI는 그 과정을 연결하고<br />검증 가능한 기록으로 남깁니다.</p>
            </div>
          </div>

          <div className="about-trace__visual" data-about-reveal data-about-stop>
            <div className="about-trace__rail" aria-label="소재에서 QR Passport까지의 여정">
              {traceSteps.map((step, index) => (
                <div className="about-trace__point" key={step}>
                  <span>{String(index + 1).padStart(2, '0')}</span>
                  <strong>{step}</strong>
                </div>
              ))}
            </div>
            <div className="about-trace__showcase">
              <figure>
                <img src={siteAssetUrl('/images/landing/journey/verify-qr.webp')} alt="제품 QR을 스캔해 소재 여정을 확인하는 모습" />
              </figure>
              <div className="about-phone" aria-label="SAEROI Material Journey 예시 화면">
                <div className="about-phone__top"><span>9:41</span><img src={logoUrl} alt="SAEROI" /></div>
                <p>소재 여정</p>
                <h3>WASHED 데님</h3>
                <ol>
                  {['수거 완료', '소재 검증', '가공 완료', '제품 생산'].map((item) => (
                    <li key={item}><Check aria-hidden="true" /><span>{item}</span></li>
                  ))}
                </ol>
                <div className="about-phone__product">
                  <span>제품</span>
                  <strong>업사이클 데님 재킷</strong>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="about-audiences" aria-label="브랜드와 소비자를 위한 SAEROI">
          <article className="about-audience about-audience--brands">
            <div data-about-reveal data-about-stop>
              <p>01 · FOR BRANDS</p>
              <h2>FOR<br />BRANDS</h2>
              <em>Materials worth creating with.</em>
              <span>새로운 소재를 발견하고,<br />그 이력과 상태를 확인하고,<br />필요한 만큼 연결합니다.</span>
            </div>
            <ul aria-label="브랜드를 위한 핵심 가치">
              <li>VERIFIED MATERIALS</li><li>SMALL-BATCH SOURCING</li><li>TRACEABLE HISTORY</li>
            </ul>
          </article>
          <article className="about-audience about-audience--you">
            <div data-about-reveal data-about-stop>
              <p>02 · FOR YOU</p>
              <h2>FOR<br />YOU</h2>
              <em>Know the story behind what you wear.</em>
              <span>내가 고른 제품이<br />어디에서 시작되었고<br />어떤 과정을 거쳐 만들어졌는지 확인합니다.</span>
            </div>
            <ul aria-label="소비자를 위한 핵심 가치">
              <li>MATERIAL ORIGIN</li><li>MAKING PROCESS</li><li>QR PASSPORT</li>
            </ul>
          </article>
        </section>

        <section className="about-manifesto" aria-labelledby="about-manifesto-title">
          <div data-about-reveal data-about-stop>
            <p className="about-kicker">OUR MANIFESTO</p>
            <h2 id="about-manifesto-title" aria-label="WHAT WAS LEFT BEHIND CAN MOVE FORWARD.">
              <span>WHAT WAS</span>
              <span>LEFT BEHIND</span>
              <span>CAN MOVE</span>
              <span><em>FORWARD.</em></span>
            </h2>
          </div>
          <div className="about-manifesto__beliefs" data-about-reveal data-about-stop>
            <p>소재의 삶은<br />한 번으로 끝나지 않습니다</p>
            <p>투명한 기록은<br />더 나은 선택을 만듭니다</p>
            <p>버려진 것은 다시,<br />갖고 싶은 것이 됩니다</p>
            <strong>버려진 것의 다음을 연결합니다.</strong>
          </div>
        </section>

        <section className="about-final" aria-labelledby="about-final-title" data-about-stop>
          <img src={siteAssetUrl('/images/landing/hero-upcycled-denim.webp')} alt="업사이클 패션 제품을 착용한 사람" />
          <div className="about-final__overlay" data-about-reveal>
            <p>DISCOVER WHAT COMES NEXT.</p>
            <h2 id="about-final-title">THE NEXT LIFE<br />STARTS HERE</h2>
            <span>Discover fashion with a journey worth knowing</span>
            <div>
              <Link className="about-final__primary" to="/shop">SHOP <ArrowRight aria-hidden="true" /></Link>
              <Link className="about-final__secondary" to="/#brands">EXPLORE BRANDS</Link>
            </div>
          </div>
        </section>
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
