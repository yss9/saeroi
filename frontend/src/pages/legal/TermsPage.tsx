import { useLayoutEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { Footer } from '../landing/components/Footer.tsx'
import { Header } from '../landing/components/Header.tsx'
import { termsSections } from './content.ts'
import '../landing/landing.css'
import './legal.css'

export function TermsPage() {
  const { pathname } = useLocation()
  useLayoutEffect(() => { window.scrollTo({ top: 0, left: 0, behavior: 'instant' }) }, [pathname])

  return (
    <div className="landing-page legal-page">
      <Header isLogoVisible />
      <main className="legal-content">
        <header className="legal-content__header">
          <p className="legal-content__eyebrow">TERMS OF SERVICE</p>
          <h1>이용약관</h1>
        </header>
        {termsSections.map((section) => (
          <section key={section.heading} className="legal-content__section">
            <h2>{section.heading}</h2>
            {section.body.map((paragraph, index) => (
              <p key={index}>{paragraph}</p>
            ))}
          </section>
        ))}
      </main>
      <Footer />
    </div>
  )
}
