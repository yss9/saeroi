import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { LandingPage } from './LandingPage.tsx'

afterEach(() => {
  cleanup()
  window.history.replaceState(null, '', '/')
})

function renderLandingPage() {
  return render(
    <MemoryRouter>
      <ShopProvider>
        <LandingPage />
      </ShopProvider>
    </MemoryRouter>,
  )
}

describe('LandingPage', () => {
  it('renders the reference sections and five curated products', () => {
    renderLandingPage()

    expect(screen.getByRole('heading', { name: '지속 가능한 가치를 만드는 브랜드' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '당신만을 위한 업사이클링 픽' })).toBeInTheDocument()
    expect(screen.getAllByRole('article', { name: /상품/ })).toHaveLength(5)
  })

  it('uses the supplied logo image in the header and footer home links', () => {
    const { container } = renderLandingPage()

    const logoImages = container.querySelectorAll<HTMLImageElement>('a[aria-label="SAEROI 홈"] img')
    expect(logoImages).toHaveLength(2)

    logoImages.forEach((logo) => {
      expect(logo).toHaveAttribute('src', '/img/logo.png')
      expect(logo).toHaveAttribute('alt', '')
    })
  })

  it('toggles a product wishlist state from its accessible button', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const button = screen.getByRole('button', { name: '데님 서클 백 찜하기' })
    await user.click(button)

    expect(screen.getByRole('button', { name: '데님 서클 백 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('navigates every header and brand discovery link to an existing hash target', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const primaryNavigation = screen.getByRole('navigation', { name: '주요 메뉴' })
    const links = [
      ...within(primaryNavigation).getAllByRole('link'),
      screen.getByRole('link', { name: '모든 브랜드 보기' }),
      screen.getByRole('link', { name: 'RE:CODE 브랜드 보기' }),
      screen.getByRole('link', { name: 'DUSTY MILLER 브랜드 보기' }),
      screen.getByRole('link', { name: 'ReWeave 브랜드 보기' }),
      screen.getByRole('link', { name: 'OCEAN LOOP 브랜드 보기' }),
    ]

    for (const link of links) {
      const href = link.getAttribute('href')
      expect(href).toMatch(/^#[a-z0-9-]+$/)
      expect(document.querySelector(href ?? '')).toBeInTheDocument()

      await user.click(link)
      expect(window.location.hash).toBe(href)
    }
  })

  it('opens the wallet disclosure and renders its unavailable state', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const trigger = screen.getByRole('button', { name: '계정 및 지갑' })
    expect(trigger).toHaveAttribute('aria-expanded', 'false')

    await user.click(trigger)

    expect(trigger).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('link', { name: 'MetaMask 설치하기' })).toHaveAttribute(
      'href',
      'https://metamask.io/download/',
    )
    expect(screen.getByText('지갑 확장 프로그램이 감지되지 않았습니다.')).toBeInTheDocument()
  })

  it('opens and closes the mobile navigation menu', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const menu = screen.getByRole('button', { name: '메뉴 열기' })
    await user.click(menu)

    expect(menu).toHaveAttribute('aria-expanded', 'true')
    await user.click(screen.getByRole('button', { name: '메뉴 닫기' }))
    expect(screen.getByRole('button', { name: '메뉴 열기' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('restores focus to the menu trigger after a mobile link closes the menu', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const menu = screen.getByRole('button', { name: '메뉴 열기' })
    await user.click(menu)
    await user.tab()

    const mobileNavigation = screen.getByRole('navigation', { name: '모바일 메뉴' })
    expect(within(mobileNavigation).getByRole('link', { name: '신상품' })).toHaveFocus()

    await user.keyboard('{Enter}')

    expect(menu).toHaveAttribute('aria-expanded', 'false')
    expect(menu).toHaveFocus()
  })

  it('validates and accepts a newsletter email', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    await user.click(screen.getByRole('button', { name: '구독하기' }))
    expect(screen.getByRole('alert')).toHaveTextContent('이메일 주소를 입력해 주세요.')

    await user.type(screen.getByLabelText('이메일 주소'), 'hello@saeroi.kr')
    await user.click(screen.getByRole('button', { name: '구독하기' }))
    expect(screen.getByRole('status')).toHaveTextContent('구독 신청이 완료되었습니다.')
  })

  it('rejects an invalid newsletter email', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    await user.type(screen.getByLabelText('이메일 주소'), 'hello@saeroi')
    await user.click(screen.getByRole('button', { name: '구독하기' }))

    expect(screen.getByRole('alert')).toHaveTextContent('올바른 이메일 주소를 입력해 주세요.')
  })
})
