import { Footer } from '../landing/components/Footer.tsx'
import { Header } from '../landing/components/Header.tsx'
import { privacySections } from './content.ts'
import '../landing/landing.css'
import './legal.css'

export function PrivacyPage() {
  return (
    <div className="landing-page legal-page">
      <Header isLogoVisible />
      <main className="legal-content">
        <header className="legal-content__header">
          <p className="legal-content__eyebrow">PRIVACY POLICY</p>
          <h1>개인정보처리방침</h1>
        </header>
        {privacySections.map((section) => (
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
