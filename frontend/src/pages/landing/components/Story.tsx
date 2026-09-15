import { ArrowUpRight } from '@phosphor-icons/react'

export function Story() {
  return (
    <section id="story" className="story" aria-labelledby="story-title">
      <div className="story__media" data-reveal>
        <img
          src="/images/landing/hero-upcycled-denim.webp"
          alt="버려진 데님 소재가 새로운 제품으로 이어지는 제작 현장"
        />
      </div>
      <div className="story__overlay">
        <p data-reveal>STORY</p>
        <h2 id="story-title" data-reveal>BEFORE IT<br />BECAME NEW.</h2>
        <div data-reveal>
          <span>소재의 다음을 잇기 위해, SAEROI는 시작되었습니다.</span>
          <a href="#story-detail">OUR STORY <ArrowUpRight aria-hidden="true" /></a>
        </div>
      </div>
      <span id="story-detail" className="landing-page__anchor" aria-hidden="true" />
    </section>
  )
}
