import { CaretDown, InstagramLogo, YoutubeLogo } from '@phosphor-icons/react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { footerNavigation } from '../content.ts'

type NewsletterFeedback =
  | { type: 'error'; message: string }
  | { type: 'success'; message: string }
  | null

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function Footer() {
  const [email, setEmail] = useState('')
  const [feedback, setFeedback] = useState<NewsletterFeedback>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalizedEmail = email.trim()

    if (!normalizedEmail) {
      setFeedback({ type: 'error', message: '이메일 주소를 입력해 주세요.' })
      return
    }

    if (!EMAIL_PATTERN.test(normalizedEmail)) {
      setFeedback({ type: 'error', message: '올바른 이메일 주소를 입력해 주세요.' })
      return
    }

    setFeedback({ type: 'success', message: '구독 신청이 완료되었습니다.' })
  }

  return (
    <footer id="about" className="site-footer">
      <div className="site-footer__intro">
        {/* 일반 <a href="/">는 basename(/test/)을 무시하고 도메인 루트로 풀 네비게이션한다 —
            test 스택에서는 이게 운영 메인으로 튀어나가는 것으로 드러난다. Link 로 바꿔야
            basename 이 자동으로 반영된다. */}
        <Link className="site-footer__logo" to="/" aria-label="SAEROI 홈">
          {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — basename 과 별개로
              직접 BASE_URL 을 붙여야 /test/ 에서 404 가 안 난다. */}
          <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        </Link>
        <p>쓰임을 다한 소재에 새로운 가치를 더합니다.</p>
        <div className="site-footer__socials" aria-label="소셜 미디어">
          <a href="#instagram" aria-label="SAEROI 인스타그램"><InstagramLogo aria-hidden="true" /></a>
          <a href="#youtube" aria-label="SAEROI 유튜브"><YoutubeLogo aria-hidden="true" /></a>
        </div>
      </div>

      <nav className="site-footer__navigation" aria-label="푸터 메뉴">
        {footerNavigation.map((group) => (
          <div key={group.title}>
            <h2>{group.title}</h2>
            {group.links.map((link) => (
              <a key={link.label} href={link.href}>{link.label}</a>
            ))}
          </div>
        ))}
      </nav>

      <div className="site-footer__newsletter">
        <h2>새로운 소식을 받아보세요</h2>
        <p>신규 브랜드와 순환 소재 이야기를 가장 먼저 전해드립니다.</p>
        <form noValidate onSubmit={handleSubmit}>
          <label htmlFor="newsletter-email">이메일 주소</label>
          <div>
            <input
              id="newsletter-email"
              name="email"
              type="email"
              value={email}
              placeholder="이메일 주소"
              aria-invalid={feedback?.type === 'error'}
              aria-describedby={feedback ? 'newsletter-feedback' : undefined}
              onChange={(event) => {
                setEmail(event.target.value)
                setFeedback(null)
              }}
            />
            <button type="submit">구독하기</button>
          </div>
        </form>
        {feedback?.type === 'error' && <p className="site-footer__feedback site-footer__feedback--error" id="newsletter-feedback" role="alert">{feedback.message}</p>}
        {feedback?.type === 'success' && <p className="site-footer__feedback site-footer__feedback--success" id="newsletter-feedback" role="status">{feedback.message}</p>}
      </div>

      <div className="site-footer__bottom">
        <p className="site-footer__copyright">© 2026 SAEROI. 모든 권리 보유.</p>
        <nav aria-label="법적 고지">
          <a href="#terms">서비스 약관</a>
          <a href="#privacy">개인정보 처리방침</a>
          <a href="#shipping">배송 및 반품</a>
          <a href="#faq">FAQ</a>
          <a href="#support">문의하기</a>
        </nav>
        <button type="button" aria-label="국가 및 통화 선택">
          한국어 (KR) <CaretDown aria-hidden="true" />
        </button>
      </div>
    </footer>
  )
}
