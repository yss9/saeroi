import { ArrowLeft } from '@phosphor-icons/react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import './auth-entry.css'

interface AuthLayoutProps {
  eyebrow?: string
  title: string
  description: string
  children: ReactNode
  wide?: boolean
  compact?: boolean
}

export function AuthLayout({
  eyebrow,
  title,
  description,
  children,
  wide = false,
  compact = false,
}: AuthLayoutProps) {
  return (
    <div className="auth-entry">
      <header className="auth-entry__header">
        <Link className="auth-entry__logo" to="/" aria-label="SAEROI 홈">
          {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — /test/ 에서 404 방지 */}
          <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        </Link>
        <Link className="auth-entry__home-link" to="/">
          <ArrowLeft aria-hidden="true" />
          SAEROI HOME
        </Link>
      </header>

      <main className="auth-entry__main">
        <section className={`auth-entry__panel${wide ? ' auth-entry__panel--wide' : ''}${compact ? ' auth-entry__panel--compact' : ''}`}>
          <div className="auth-entry__intro">
            {eyebrow ? <p className="auth-entry__eyebrow">{eyebrow}</p> : null}
            <h1>{title}</h1>
            <p>{description}</p>
          </div>
          {children}
        </section>
      </main>

      <footer className="auth-entry__footer">© 2026 SAEROI</footer>
    </div>
  )
}
