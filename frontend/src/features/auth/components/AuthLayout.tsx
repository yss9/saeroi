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
}

export function AuthLayout({
  eyebrow,
  title,
  description,
  children,
  wide = false,
}: AuthLayoutProps) {
  return (
    <div className="auth-entry">
      <header className="auth-entry__header">
        <Link className="auth-entry__logo" to="/" aria-label="SAEROI 홈">
          <img src="/img/logo.png" alt="" />
        </Link>
        <Link className="auth-entry__home-link" to="/">
          <ArrowLeft aria-hidden="true" />
          쇼핑몰로 돌아가기
        </Link>
      </header>

      <main className="auth-entry__main">
        <section className={`auth-entry__panel${wide ? ' auth-entry__panel--wide' : ''}`}>
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
