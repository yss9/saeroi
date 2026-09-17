import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '../../app/providers/AppProviders.tsx'
import { routes } from '../../app/router/routes.tsx'
import { useAuthStore } from '../../stores/authStore.ts'
import { apiClient } from '../../services/apiClient.ts'
import { queryClient } from '../../services/queryClient.ts'

vi.mock('../../utils/kakaoPostcode.ts', () => ({ openKakaoPostcode: vi.fn() }))

const PROFILE = {
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
  role: 'BRAND' as const,
  status: 'ACTIVE' as const,
}

function signIn() {
  useAuthStore.setState({
    user: { id: 8, name: '새로이 브랜드', email: 'brand@saeroi.test', role: 'BRAND', walletAddress: null },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
}

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

/**
 * 조회 ↔ 수정.
 *
 * <b>저장이 성공할 때만 닫는다.</b> 실패했는데 닫으면 사용자가 적은 것이 사라지고
 * 무엇이 잘못됐는지도 볼 수 없다.
 *
 * <b>업체 정보를 못 읽었으면 열지 않는다.</b> 서버가 전부 필수로 받으므로(PUT 은 부분
 * 수정이 아니다) 빈 폼으로 저장하면 멀쩡한 값이 지워진다.
 */
describe('업체 정보 수정', () => {
  it('정보 수정을 누르면 지금 값이 채워진 폼이 뜬다', async () => {
    const user = userEvent.setup()
    mockApiGet(PROFILE)
    signIn()

    renderRoute()
    await user.click(await screen.findByRole('button', { name: '정보 수정' }))

    expect(screen.getByLabelText('대표자명')).toHaveValue('김새로')
  })

  it('업체 정보를 못 읽으면 수정 버튼을 내지 않는다', async () => {
    mockApiGet(null)
    signIn()

    renderRoute()

    expect(await screen.findByRole('heading', { name: '회사 정보', level: 1 })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '정보 수정' })).not.toBeInTheDocument()
  })

  it('저장이 성공하면 조회로 돌아가고 고친 값이 보인다', async () => {
    const user = userEvent.setup()
    // 저장 뒤 무효화가 재조회를 부른다. 서버라면 고쳐진 값이 오므로 목도 그렇게 둔다 —
    // 옛 값을 계속 돌려주면 이 테스트는 실제로 일어나지 않는 일을 검증하게 된다.
    let stored = PROFILE
    vi.spyOn(apiClient, 'get').mockImplementation((url: string) =>
      url.includes('/companies/me')
        ? Promise.resolve({ data: stored } as AxiosResponse)
        : Promise.resolve({ data: [] } as AxiosResponse),
    )
    vi.spyOn(apiClient, 'put').mockImplementation((_url: string, body: unknown) => {
      stored = { ...stored, ...(body as { representativeName: string }) }
      return Promise.resolve({ data: stored } as AxiosResponse)
    })
    signIn()

    renderRoute()
    await user.click(await screen.findByRole('button', { name: '정보 수정' }))
    const name = screen.getByLabelText('대표자명')
    await user.clear(name)
    await user.type(name, '박새로')
    await user.click(screen.getByRole('button', { name: '저장' }))

    // 응답을 캐시에 곧바로 넣으므로 다시 조회하지 않아도 새 값이 보인다.
    expect(await screen.findByText('박새로')).toBeInTheDocument()
    expect(screen.queryByLabelText('대표자명')).not.toBeInTheDocument()
  })

  it('저장이 실패하면 폼에 남는다', async () => {
    const user = userEvent.setup()
    mockApiGet(PROFILE)
    vi.spyOn(apiClient, 'put').mockRejectedValue(new Error('서버 오류'))
    signIn()

    renderRoute()
    await user.click(await screen.findByRole('button', { name: '정보 수정' }))
    const name = screen.getByLabelText('대표자명')
    await user.clear(name)
    await user.type(name, '박새로')
    await user.click(screen.getByRole('button', { name: '저장' }))

    // 적은 값이 그대로 남아 있어야 다시 시도할 수 있다.
    expect(await screen.findByLabelText('대표자명')).toHaveValue('박새로')
  })
})
