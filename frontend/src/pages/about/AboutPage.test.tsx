import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { AboutPage } from './AboutPage.tsx'

function renderAboutPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/about']}>
        <ShopProvider>
          <AboutPage />
        </ShopProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  // jsdom에는 실제 스크롤 API가 없으므로 브라우저 동작은 명시적으로 대체.
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
})

afterEach(() => {
  cleanup()
  Object.defineProperty(window, 'scrollY', { configurable: true, value: 0 })
  vi.restoreAllMocks()
})

describe('AboutPage', () => {
  it('starts at the hero when navigating from a scrolled HOME through OUR STORY', () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {
      Object.defineProperty(window, 'scrollY', { configurable: true, value: 0 })
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/']}>
          <ShopProvider>
            <Routes>
              <Route path="/" element={<Link to="/about">OUR STORY</Link>} />
              <Route path="/about" element={<AboutPage />} />
            </Routes>
          </ShopProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 2800 })
    expect(scrollTo).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('link', { name: 'OUR STORY' }))

    expect(scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' })
    expect(window.scrollY).toBe(0)
    expect(screen.getByRole('heading', { name: 'WHO WE ARE' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '페이지 맨 위로 이동' })).not.toBeInTheDocument()
  })

  it('renders the approved editorial story in order', () => {
    renderAboutPage()

    ;[
      'WHO WE ARE',
      'WE SEE WASTE DIFFERENTLY.',
      'MATERIALS BRANDS PEOPLE',
      'FROM MATERIAL TO PRODUCT',
      'EVERY MATERIAL LEAVES A TRACE.',
      'FOR BRANDS',
      'FOR YOU',
      'WHAT WAS LEFT BEHIND CAN MOVE FORWARD.',
      'THE NEXT LIFE STARTS HERE',
    ].forEach((name) => expect(screen.getByRole('heading', { name })).toBeInTheDocument())

    expect(screen.getByText(/SAEROI는 남겨진 소재가 다시 선택되고/)).toBeInTheDocument()
    expect(screen.getByLabelText('소재에서 QR Passport까지의 여정')).toBeInTheDocument()
    expect(screen.getByLabelText('SAEROI Material Journey 예시 화면')).toBeInTheDocument()
    expect(screen.getByText('WASHED 데님')).toBeInTheDocument()
    expect(screen.getByText('업사이클 데님 재킷')).toBeInTheDocument()
    expect(screen.queryByText('A platform for the next life of materials.')).not.toBeInTheDocument()
    expect(screen.queryByText('SCROLL TO DISCOVER')).not.toBeInTheDocument()
  })

  it('connects the header to SHOP, BRANDS and ABOUT while preserving the home discovery link', () => {
    renderAboutPage()

    const navigation = screen.getByRole('navigation', { name: '주요 메뉴' })
    expect(within(navigation).getByRole('link', { name: 'SHOP' })).toHaveAttribute('href', '/shop')
    expect(within(navigation).getByRole('link', { name: 'BRANDS' })).toHaveAttribute('href', '/brands')
    expect(within(navigation).getByRole('link', { name: 'ABOUT' })).toHaveAttribute('href', '/about')

    expect(screen.getAllByRole('link', { name: /^SHOP/ }).at(-1)).toHaveAttribute('href', '/shop')
    expect(screen.getByRole('link', { name: 'EXPLORE BRANDS' })).toHaveAttribute('href', '/#brands')
  })

  it('reveals the same fixed back-to-top control used by the homepage', () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
    renderAboutPage()

    expect(screen.queryByRole('button', { name: '페이지 맨 위로 이동' })).not.toBeInTheDocument()
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 2000 })
    fireEvent.scroll(window)
    fireEvent.click(screen.getByRole('button', { name: '페이지 맨 위로 이동' }))

    expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' })
  })
})
