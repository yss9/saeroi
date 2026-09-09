import { cleanup, render, screen } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '../../app/providers/AppProviders.tsx'
import { routes } from '../../app/router/routes.tsx'
import { useAuthStore } from '../../stores/authStore.ts'
import { apiClient } from '../../services/apiClient.ts'
import { queryClient } from '../../services/queryClient.ts'

function renderRoute() {
  const router = createMemoryRouter(routes, { initialEntries: ['/company/account'] })
  return render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  )
}

/**
 * URL 별로 다른 모양을 돌려준다 — `/wallets/me` 는 배열, `/companies/me` 는 단일 객체다.
 * 하나의 값으로 뭉뚱그리면(과거 `{ data: [] }` 공용 mock) 업체 정보 쪽이 실제로는
 * 아무것도 검증하지 못한 채 통과해 버린다.
 */
function mockApiGet(companyProfile: unknown) {
  vi.spyOn(apiClient, 'get').mockImplementation((url: string) => {
    if (url.includes('/companies/me')) {
      return Promise.resolve({ data: companyProfile } as AxiosResponse)
    }
    return Promise.resolve({ data: [] } as AxiosResponse)
  })
}

beforeEach(() => {
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  // AppProviders 는 앱 전역 싱글턴 queryClient 를 쓴다(services/queryClient.ts 문서 참고 —
  // "테스트에서는 각 테스트마다 새 QueryClient 를 쓰는 편이 낫다"). 이 파일은 라우팅 트리
  // 전체를 그대로 렌더해야 해서 client 를 바꿔 끼울 수 없으므로, staleTime(30s) 안에서
  // 이전 테스트가 채워 둔 ['company','me'] 캐시를 그대로 읽는 걸 막으려면 매번 비운다.
  queryClient.clear()
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.restoreAllMocks()
})

describe('company account route', () => {
  it('allows an authenticated business member to enter the dashboard account page', () => {
    mockApiGet(null)
    useAuthStore.setState({
      user: {
        id: 8,
        name: '새로이 브랜드',
        email: 'brand@saeroi.test',
        role: 'BRAND',
        walletAddress: null,
      },
      accessToken: 'test-token',
      isAuthenticated: true,
    })

    renderRoute()

    expect(screen.getByRole('heading', { name: '회사 정보', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })).toBeInTheDocument()
  })

  it('renders the fetched company profile once the request resolves', async () => {
    mockApiGet({
      userId: 8,
      companyName: '새로이 텍스타일',
      businessNumber: '123-45-67890',
      representativeName: '김새로',
      companyPhone: '02-1234-5678',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 212',
      addressDetail: '3층',
      introduction: null,
      logoUrl: null,
      role: 'BRAND',
      status: 'ACTIVE',
    })
    useAuthStore.setState({
      user: {
        id: 8,
        name: '새로이 브랜드',
        email: 'brand@saeroi.test',
        role: 'BRAND',
        walletAddress: null,
      },
      accessToken: 'test-token',
      isAuthenticated: true,
    })

    renderRoute()

    expect(await screen.findByRole('heading', { name: '새로이 텍스타일' })).toBeInTheDocument()
    expect(await screen.findByText('123-45-67890')).toBeInTheDocument()
    expect(await screen.findByText('김새로')).toBeInTheDocument()
    expect(await screen.findByText('02-1234-5678')).toBeInTheDocument()
    expect(await screen.findByText('(06236) 서울특별시 강남구 테헤란로 212 3층')).toBeInTheDocument()
    expect(await screen.findByText('활성 계정')).toBeInTheDocument()
  })
})
