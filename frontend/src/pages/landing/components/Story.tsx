import { ArrowUpRight } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { siteAssetUrl } from '../../../utils/siteAssetUrl.ts'

export function Story() {
  return (
    <section id="story" className="story" aria-labelledby="story-title">
      <div className="story__media" data-reveal>
        <img
          src={siteAssetUrl('/images/landing/hero-upcycled-denim.webp')}
          alt="버려진 데님 소재가 새로운 제품으로 이어지는 제작 현장"
        />
      </div>
      <div className="story__overlay">
        <p data-reveal>STORY</p>
        <h2 id="story-title" data-reveal>BEFORE IT<br />BECAME NEW.</h2>
        <div data-reveal>
          <span>소재의 다음을 잇기 위해, SAEROI는 시작되었습니다.</span>
          <Link to="/about">OUR STORY <ArrowUpRight aria-hidden="true" /></Link>
        </div>
      </div>
    </section>
  )
}
