import { ArrowRight } from '@phosphor-icons/react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import logoUrl from '../../../assets/saeroi-logo-vector.svg'
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
      <div className="site-footer__top">
        <div className="site-footer__intro">
          <Link className="site-footer__logo" to="/" aria-label="SAEROI 홈">
            <img src={logoUrl} alt="" />
          </Link>
          <p>쓰임을 다한 소재에서<br />다음의 가치를 발견합니다.</p>
        </div>

        <nav className="site-footer__navigation" aria-label="푸터 메뉴">
          {footerNavigation.map((group) => (
            <div key={group.title}>
              <h2>{group.title}</h2>
              {group.links.map((link) => link.href.startsWith('/') ? (
                <Link key={link.label} to={link.href}>{link.label}</Link>
              ) : (
                <a key={link.label} href={link.href}>{link.label}</a>
              ))}
            </div>
          ))}
        </nav>
      </div>

      <div className="site-footer__newsletter">
        <div>
          <p>NEWSLETTER</p>
          <h2>새로운 쓰임을 가장 먼저 만나보세요.</h2>
        </div>
        <form noValidate onSubmit={handleSubmit}>
          <label htmlFor="newsletter-email">이메일 주소</label>
          <input
            id="newsletter-email"
            name="email"
            type="email"
            value={email}
            placeholder="EMAIL ADDRESS"
            aria-invalid={feedback?.type === 'error'}
            aria-describedby={feedback ? 'newsletter-feedback' : undefined}
            onChange={(event) => {
              setEmail(event.target.value)
              setFeedback(null)
            }}
          />
          <button type="submit" aria-label="구독하기"><ArrowRight aria-hidden="true" /></button>
        </form>
        {feedback?.type === 'error' && (
          <p className="site-footer__feedback site-footer__feedback--error" id="newsletter-feedback" role="alert">
            {feedback.message}
          </p>
        )}
        {feedback?.type === 'success' && (
          <p className="site-footer__feedback site-footer__feedback--success" id="newsletter-feedback" role="status">
            {feedback.message}
          </p>
        )}
      </div>

      <div className="site-footer__bottom">
        <p>© 2026 SAEROI</p>
        <nav aria-label="법적 고지">
          <a href="#terms">이용약관</a>
          <a href="#privacy">개인정보처리방침</a>
          <a href="#shipping">배송 및 반품</a>
          <a href="#faq">FAQ</a>
        </nav>
        <span>SEOUL · KR</span>
      </div>
    </footer>
  )
}
