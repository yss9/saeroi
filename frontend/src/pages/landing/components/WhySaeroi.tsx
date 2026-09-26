import { saeroiValues } from '../content.ts'
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'

interface WhySaeroiProps {
  // 휠 페이지네이션의 두 번째 정지 지점. 이미지는 그 자리에서 커지고,
  // 세 번째 지점에서는 제목이 위로 빠지며 생긴 공간에 설명이 왼쪽부터 순서대로 들어온다.
  showCaptions: boolean
}

export function WhySaeroi({ showCaptions }: WhySaeroiProps) {
  const mediaRefs = useRef<(HTMLDivElement | null)[]>([])
  // 배포본과 같은 4:5 비율(폭 기준)로 커지는 목표 높이. 화면 2(고정 높이)는 CSS clamp
  // 값을 그대로 쓰고, 화면 3에서만 이 값으로 늘어난다 — px → px 전환이라야 애니메이션이
  // 걸린다('auto'는 전환되지 않는다) — 그리고 카드마다 순서대로(0/150/300ms) 늘어나는
  // 효과도 이 값 + transitionDelay 조합으로 낸다.
  const [expandedHeights, setExpandedHeights] = useState<number[]>(
    () => new Array(saeroiValues.length).fill(0),
  )

  // 최초 측정은 useLayoutEffect 로 동기 처리한다 — ResizeObserver 의 첫 콜백은
  // 브라우저가 다음 렌더링 프레임에서 비동기로 호출해 주는데, 탭이 백그라운드라
  // 프레임이 돌지 않는 동안에는 이 콜백 자체가 지연된다. getBoundingClientRect 는
  // 그런 경우에도 즉시 값을 준다.
  useLayoutEffect(() => {
    setExpandedHeights(mediaRefs.current.map((el) => (el ? el.getBoundingClientRect().width * (5 / 4) : 0)))
  }, [])

  useEffect(() => {
    if (typeof ResizeObserver === 'undefined') return
    const elements = mediaRefs.current
    const observer = new ResizeObserver((entries) => {
      setExpandedHeights((current) => {
        const next = [...current]
        for (const entry of entries) {
          const index = elements.indexOf(entry.target as HTMLDivElement)
          if (index === -1) continue
          next[index] = entry.contentRect.width * (5 / 4)
        }
        return next
      })
    })

    elements.forEach((element) => element && observer.observe(element))
    return () => observer.disconnect()
  }, [])

  return (
    <section id="why-saeroi" className="why-saeroi" aria-labelledby="why-saeroi-title">
      {/* className은 항상 고정 문자열로 둔다 — showCaptions에 따라 className을 매번
          새로 계산하면 리렌더될 때마다 React가 class 속성을 통째로 덮어써서,
          IntersectionObserver가 직접 붙여준 `is-visible`(등장 애니메이션 상태)이
          지워진다. 그러면 3번 화면을 갔다가 2번으로 돌아왔을 때 제목이 다시는
          보이지 않는다. 그래서 접힘 여부는 별도의 data-collapsed 속성으로만 표현한다. */}
      <header
        className="editorial-heading"
        data-reveal
        data-collapsed={showCaptions ? 'true' : undefined}
      >
        <div>
          <h2 id="why-saeroi-title">WHY SAEROI</h2>
          <p className="editorial-heading__subtitle">Beyond The Product</p>
          <p className="editorial-heading__body">제품 너머의 이야기까지.</p>
        </div>
      </header>

      <div className={`why-saeroi__journey${showCaptions ? ' why-saeroi__journey--captions' : ''}`}>
        {saeroiValues.map((value, index) => (
          <article
            className={`value-story value-story--${value.id}`}
            key={value.id}
            data-reveal
            style={{ '--reveal-delay': `${220 + index * 190}ms` } as CSSProperties}
          >
            <div
              className="value-story__media"
              ref={(el) => { mediaRefs.current[index] = el }}
              style={{
                height: showCaptions && expandedHeights[index] ? `${expandedHeights[index]}px` : undefined,
                transitionDelay: showCaptions ? `${index * 150}ms` : '0ms',
              }}
            >
              <img src={value.image} alt={value.imageAlt} />
            </div>
            <div
              className="value-story__copy"
              aria-hidden={!showCaptions}
              style={{ transitionDelay: showCaptions ? `${index * 150}ms` : '0ms' } as CSSProperties}
            >
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
