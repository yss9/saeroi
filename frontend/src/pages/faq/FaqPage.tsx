import { Footer } from '../landing/components/Footer.tsx'
import { Header } from '../landing/components/Header.tsx'
import { faqCategories } from '../legal/content.ts'
import '../landing/landing.css'
import '../legal/legal.css'

export function FaqPage() {
  return (
    <div className="landing-page legal-page">
      <Header isLogoVisible />
      <main className="legal-content">
        <header className="legal-content__header">
          <p className="legal-content__eyebrow">FAQ</p>
          <h1>자주 묻는 질문</h1>
        </header>
        {faqCategories.map((category) => (
          <section key={category.title} className="legal-faq__category">
            <h2>{category.title}</h2>
            {category.items.map((item) => (
              <details key={item.question} className="legal-faq__item">
                <summary>{item.question}</summary>
                <p>{item.answer}</p>
              </details>
            ))}
          </section>
        ))}
      </main>
      <Footer />
    </div>
  )
}
