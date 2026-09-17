import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PublicProductModel } from '../../features/product/types/publicProduct.types.ts'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore, type AuthUser } from '../../stores/authStore.ts'
import type { PageResponse } from '../../types/api.types.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import { LandingPage } from './LandingPage.tsx'

const publicCatalog: PageResponse<PublicProductModel> = {
  content: [
    publicProduct(1, '데님 서클 백', 'ReWeave', 'BAG', 129_000, 'product-denim-circle.webp'),
    publicProduct(2, '패치워크 워크 자켓', 'RE:CODE', 'CLOTHING', 149_000, 'product-work-jacket.webp'),
    publicProduct(3, '새들 슬링 백', 'DUSTY MILLER', 'BAG', 99_000, 'product-saddle-bag.webp'),
    publicProduct(4, '패치워크 버킷 햇', 'OCEAN LOOP', 'ACCESSORY', 59_000, 'product-bucket-hat.webp'),
  ],
  page: 0,
  size: 4,
  totalElements: 4,
  totalPages: 1,
  hasNext: false,
}

beforeEach(() => {
  useAuthStore.getState().signOut()
  vi.spyOn(apiClient, 'get').mockImplementation(async (url) => ({ data: url.endsWith('/product-ids') ? [] : publicCatalog }) as AxiosResponse)
  vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {} } as AxiosResponse)
  vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: {} } as AxiosResponse)
})

afterEach(() => {
  cleanup()
  useAuthStore.getState().signOut()
  window.history.replaceState(null, '', '/')
  Object.defineProperty(window, 'scrollY', { configurable: true, value: 0 })
  vi.restoreAllMocks()
})

function LocationProbe() {
  const location = useLocation()
  return (
    <output aria-label="현재 위치" data-location-state={JSON.stringify(location.state)}>
      {location.pathname}{location.hash}
    </output>
  )
}

function renderLandingPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ShopProvider>
          <LandingPage />
          <LocationProbe />
        </ShopProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function publicProduct(
  productModelId: number,
  name: string,
  brandName: string,
  category: PublicProductModel['category'],
  price: number,
  image: string,
): PublicProductModel {
  return {
    productModelId,
    name,
    category,
    subcategory: null,
    brandId: productModelId,
    brandName,
    price,
    description: `${name} 설명`,
    thumbnailUrl: `/images/landing/${image}`,
    status: 'ON_SALE',
    stockQuantity: 10,
    createdAt: '2026-09-16T10:00:00',
  }
}

describe('LandingPage', () => {
  it('renders the editorial homepage sections and a product from the public catalog', async () => {
    renderLandingPage()

    expect(screen.getByRole('heading', { name: 'WASTE IS NOT THE END.' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'WHY SAEROI' })).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'SHOP' })).getByRole('heading', { name: 'SHOP' })).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'BRANDS' })).getByRole('heading', { name: 'BRANDS' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'BEFORE IT BECAME NEW.' })).toBeInTheDocument()
    expect(await screen.findByRole('article', { name: '데님 서클 백 상품' })).toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledWith('/v1/public/product-models', {
      params: { page: 0, size: 4 },
      skipAuth: true,
      skipTokenRefresh: true,
    })
    expect(screen.getByText('Beyond The Product')).toBeInTheDocument()
    expect(screen.getByText('제품 너머의 이야기까지.')).toBeInTheDocument()
    expect(screen.getByText('New Life, New Form')).toBeInTheDocument()
    expect(screen.getByText('Meet The Makers')).toBeInTheDocument()
    expect(screen.getByText('소재의 다음을 잇기 위해, SAEROI는 시작되었습니다.')).toBeInTheDocument()

    const bagButton = screen.getByRole('button', { name: '데님 서클 백 장바구니 담기' })
    expect(bagButton).toHaveTextContent('')

    const footerNavigation = screen.getByRole('navigation', { name: '푸터 메뉴' })
    expect(within(footerNavigation).getAllByRole('heading').map((heading) => heading.textContent)).toEqual([
      'ABOUT',
      'CUSTOMER',
    ])

    const journeyImages = within(screen.getByRole('region', { name: 'WHY SAEROI' })).getAllByRole('img')
    expect(journeyImages.map((image) => image.getAttribute('src'))).toEqual([
      '/images/landing/journey/trace-material.webp',
      '/images/landing/journey/transform-process.webp',
      '/images/landing/journey/verify-qr.webp',
    ])
  })

  it('uses the supplied vector logo in the intro, header, and footer', () => {
    const { container } = renderLandingPage()

    const logoImages = container.querySelectorAll<HTMLImageElement>('a[aria-label="SAEROI 홈"] img')
    expect(logoImages).toHaveLength(2)

    logoImages.forEach((logo) => {
      expect(logo.getAttribute('src')).toContain('saeroi-logo-vector.svg')
      expect(logo).toHaveAttribute('alt', '')
    })
    const introLogo = screen.getByRole('img', { name: 'SAEROI' })
    expect(introLogo.querySelector('svg')).toBeInTheDocument()
    expect(introLogo.querySelector('#o-letter > #o-motion > #o-spinner > #g12')).toBeInTheDocument()
  })

  it('sends a signed-out wishlist action to login with a return location', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const button = await screen.findByRole('button', { name: '데님 서클 백 찜하기' })
    await user.click(button)

    expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/login')
    expect(screen.getByLabelText('현재 위치')).toHaveAttribute(
      'data-location-state',
      JSON.stringify({ from: { pathname: '/#shop' } }),
    )
    expect(button).toHaveAttribute('aria-pressed', 'false')
  })

  it('fills the wishlist heart while the control is hovered', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const button = await screen.findByRole('button', { name: '데님 서클 백 찜하기' })
    const heartBeforeHover = button.querySelector('svg')?.innerHTML

    await user.hover(button)

    expect(button.querySelector('svg')?.innerHTML).not.toBe(heartBeforeHover)

    await user.unhover(button)

    expect(button.querySelector('svg')?.innerHTML).toBe(heartBeforeHover)
  })

  it('toggles a product wishlist for a signed-in consumer', async () => {
    useAuthStore.getState().signIn(
      {
        id: 1,
        email: 'consumer@saeroi.kr',
        name: '김새로이',
        role: 'CONSUMER',
        walletAddress: null,
      },
      'access-token',
    )
    const user = userEvent.setup()
    renderLandingPage()

    const button = await screen.findByRole('button', { name: '데님 서클 백 찜하기' })
    await user.click(button)

    expect(await screen.findByRole('button', { name: '데님 서클 백 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
    expect(apiClient.post).toHaveBeenCalledWith('/v1/consumers/me/wishlist', { productModelId: 1 })
  })

  it('changes the featured product with the carousel controls', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    await user.click(await screen.findByRole('button', { name: '다음 상품' }))

    expect(screen.getByRole('article', { name: '패치워크 워크 자켓 상품' })).toBeInTheDocument()
  })

  it('shows an empty state when there are no products on sale', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { ...publicCatalog, content: [], totalElements: 0, totalPages: 0 },
    } as AxiosResponse)

    renderLandingPage()

    expect(await screen.findByText('판매 중인 상품이 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /장바구니 담기/ })).not.toBeInTheDocument()
  })

  it('shows an error state and retries the public catalog request', async () => {
    vi.mocked(apiClient.get)
      .mockRejectedValueOnce(createAppError(ERROR_KIND.NETWORK_ERROR))
      .mockResolvedValueOnce({ data: publicCatalog } as AxiosResponse)
    const user = userEvent.setup()

    renderLandingPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('상품을 불러오지 못했습니다')
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByRole('article', { name: '데님 서클 백 상품' })).toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledTimes(2)
  })

  it('connects BRANDS, SHOP and ABOUT discovery links while preserving home sections', () => {
    renderLandingPage()

    const primaryNavigation = screen.getByRole('navigation', { name: '주요 메뉴' })
    const sectionLinks = [
      within(primaryNavigation).getByRole('link', { name: 'BRANDS' }),
      screen.getByRole('link', { name: 'EXPLORE BRANDS' }),
    ]

    for (const link of sectionLinks) {
      expect(link).toHaveAttribute('href', '/brands')
    }
    expect(screen.getByRole('region', { name: 'BRANDS' })).toBeInTheDocument()

    expect(within(primaryNavigation).getByRole('link', { name: 'ABOUT' })).toHaveAttribute('href', '/about')
    expect(within(primaryNavigation).getByRole('link', { name: 'SHOP' })).toHaveAttribute('href', '/shop')
    expect(screen.getByRole('link', { name: 'VIEW ALL PRODUCTS' })).toHaveAttribute('href', '/shop')
    expect(screen.getByRole('link', { name: 'OUR STORY' })).toHaveAttribute('href', '/about')
    expect(screen.getByRole('link', { name: '우리의 이야기' })).toHaveAttribute('href', '/about')
  })

  it('shows login and signup links in the account menu when signed out', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const trigger = screen.getByRole('button', { name: '계정 메뉴' })
    expect(trigger).toHaveAttribute('aria-expanded', 'false')

    await user.click(trigger)

    expect(trigger).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: '회원가입' })).toHaveAttribute('href', '/signup')
    expect(screen.queryByText(/MetaMask/)).not.toBeInTheDocument()
  })

  it.each([
    // 사람 모양 버튼은 "내 계정" 으로 간다. 업무 화면은 사이드바가 들고 있다 —
    // 예전에는 브랜드가 이 버튼을 누르면 가공 요청으로 갔다.
    [{ role: 'CONSUMER', name: '김새로이' }, '개인회원', '마이페이지', '/mypage'],
    [{ role: 'SUPPLIER', name: '새로이 공급사' }, '사업자회원', '마이페이지', '/company/account'],
    [{ role: 'BRAND', name: '새로이 브랜드' }, '사업자회원', '마이페이지', '/company/account'],
    [{ role: 'PROCESSOR', name: '새로이 가공사' }, '사업자회원', '마이페이지', '/company/account'],
    [{ role: 'ADMIN', name: '새로이 운영자' }, '관리자', '관리자 페이지', '/admin'],
  ] as const)(
    'shows the correct destination for a signed-in $role account',
    async (account, accountType, destinationLabel, destinationPath) => {
      const signedInUser: AuthUser = {
        id: 1,
        email: 'member@saeroi.kr',
        name: account.name,
        role: account.role,
        walletAddress: null,
      }
      useAuthStore.getState().signIn(signedInUser, 'access-token')
      const user = userEvent.setup()
      renderLandingPage()

      await user.click(screen.getByRole('button', { name: '계정 메뉴' }))

      expect(screen.getByText(account.name)).toBeInTheDocument()
      expect(screen.getByText(accountType)).toBeInTheDocument()
      expect(screen.getByRole('link', { name: destinationLabel })).toHaveAttribute(
        'href',
        destinationPath,
      )
      expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
    },
  )

  it('signs out from the account menu', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: null } as AxiosResponse)
    useAuthStore.getState().signIn(
      {
        id: 1,
        email: 'member@saeroi.kr',
        name: '김새로이',
        role: 'CONSUMER',
        walletAddress: null,
      },
      'access-token',
    )
    const user = userEvent.setup()
    renderLandingPage()

    await user.click(screen.getByRole('button', { name: '계정 메뉴' }))
    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(false))
    expect(post).toHaveBeenCalledWith('/v1/auth/logout', undefined, {
      skipAuth: true,
      skipTokenRefresh: true,
    })
    expect(screen.getByRole('button', { name: '계정 메뉴' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('keeps the session when server-side token revocation fails', async () => {
    vi.spyOn(apiClient, 'post').mockRejectedValue(createAppError(ERROR_KIND.NETWORK_ERROR))
    useAuthStore.getState().signIn(
      {
        id: 1,
        email: 'member@saeroi.kr',
        name: '김새로이',
        role: 'CONSUMER',
        walletAddress: null,
      },
      'access-token',
    )
    const user = userEvent.setup()
    renderLandingPage()

    await user.click(screen.getByRole('button', { name: '계정 메뉴' }))
    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.',
    )
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('closes the account menu with Escape and restores trigger focus', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    const trigger = screen.getByRole('button', { name: '계정 메뉴' })
    await user.click(trigger)
    await user.keyboard('{Escape}')

    expect(trigger).toHaveAttribute('aria-expanded', 'false')
    expect(trigger).toHaveFocus()
  })

  it('keeps only the requested centered primary navigation items', () => {
    renderLandingPage()

    const navigation = screen.getByRole('navigation', { name: '주요 메뉴' })
    expect(within(navigation).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'SHOP',
      'BRANDS',
      'ABOUT',
    ])
    expect(screen.queryByRole('button', { name: '메뉴 열기' })).not.toBeInTheDocument()
  })

  it('moves to the top with the fixed back-to-top control', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
    const user = userEvent.setup()
    renderLandingPage()

    expect(screen.queryByRole('button', { name: '페이지 맨 위로 이동' })).not.toBeInTheDocument()
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 1000 })
    fireEvent.scroll(window)

    await user.click(await screen.findByRole('button', { name: '페이지 맨 위로 이동' }))

    expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' })
  })

  it('validates and accepts a newsletter email', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    await user.click(screen.getByRole('button', { name: '구독하기' }))
    expect(screen.getByRole('alert')).toHaveTextContent('이메일 주소를 입력해 주세요.')

    await user.type(screen.getByLabelText('이메일 주소'), 'hello@saeroi.kr')
    await user.click(screen.getByRole('button', { name: '구독하기' }))
    expect(screen.getByText('구독 신청이 완료되었습니다.')).toHaveAttribute('role', 'status')
  })

  it('rejects an invalid newsletter email', async () => {
    const user = userEvent.setup()
    renderLandingPage()

    await user.type(screen.getByLabelText('이메일 주소'), 'hello@saeroi')
    await user.click(screen.getByRole('button', { name: '구독하기' }))

    expect(screen.getByRole('alert')).toHaveTextContent('올바른 이메일 주소를 입력해 주세요.')
  })
})
