import { CuratedPicks } from './components/CuratedPicks.tsx'
import { FeaturedBrands } from './components/FeaturedBrands.tsx'
import { Footer } from './components/Footer.tsx'
import { Header } from './components/Header.tsx'
import { Hero } from './components/Hero.tsx'
import { TrustStrip } from './components/TrustStrip.tsx'
import './landing.css'

export function LandingPage() {
  return (
    <div className="landing-page">
      <Header />
      <main>
        <Hero />
        <FeaturedBrands />
        <CuratedPicks />
        <TrustStrip />
      </main>
      <Footer />
    </div>
  )
}
