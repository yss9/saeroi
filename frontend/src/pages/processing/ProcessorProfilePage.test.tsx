import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { ProcessorProfilePage } from './ProcessorProfilePage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
const openKakaoPostcode = vi.hoisted(() => vi.fn())
vi.mock('../../utils/kakaoPostcode.ts', () => ({ openKakaoPostcode }))

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({
    user: { id: 3, email: 'processor@test.com', name: '리뉴 가공소', role: 'PROCESSOR', walletAddress: null },
    accessToken: 'token', isAuthenticated: true,
  })
  vi.mocked(apiClient.get).mockImplementation(async url => {
    if (url.endsWith('/profile/image')) return { data: null }
    if (url.endsWith('/companies/me')) return { data: { userId: 3, companyName: '리뉴 가공소', address: '서울 마포구 월드컵북로 400' } }
    if (url.endsWith('/profile')) return { data: { processorId: 3, region: '서울 성동구' } }
    if (url.endsWith('/capabilities')) return { data: [
      { materialType: 'DENIM', processType: 'CLEANING', minQuantity: 100, maxQuantity: 5000 },
      { materialType: 'COTTON', processType: 'CUTTING', minQuantity: 50, maxQuantity: 2000 },
    ] }
    return { data: [
      { materialType: 'DENIM', processType: 'CLEANING', maxQuantity: 5000, leadDays: 4 },
      { materialType: 'COTTON', processType: 'CUTTING', maxQuantity: 2000, leadDays: 6 },
    ] }
  })
  vi.mocked(apiClient.put).mockImplementation(async (_url, body) => ({ data: body }))
  vi.mocked(apiClient.post).mockResolvedValue({ data: { imageUrl: '/api/v1/processors/me/profile/image' } })
  openKakaoPostcode.mockImplementation(async onComplete => {
    onComplete({ zonecode: '04790', address: '서울 성동구 왕십리로 83-21' })
  })
})

afterEach(cleanup)

test('가공업체 프로필 주소가 없으면 회원가입 업체 주소를 기본 표시한다', async () => {
  vi.mocked(apiClient.get).mockImplementation(async url => {
    if (url.endsWith('/profile/image')) return { data: null }
    if (url.endsWith('/companies/me')) return { data: { userId: 3, companyName: '리뉴 가공소', address: '부산 해운대구 센텀중앙로 55' } }
    if (url.endsWith('/profile')) return { data: null }
    return { data: [] }
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  expect(await screen.findByDisplayValue('부산 해운대구 센텀중앙로 55')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '업체 주소 등록' })).toBeInTheDocument()
  expect(screen.queryByText('회원가입 주소가 기본으로 표시되며 필요하면 변경할 수 있어요.')).not.toBeInTheDocument()
  expect(screen.queryByText('소재와 공정을 선택한 뒤 결과에서 조건을 추가하거나 수정하세요.')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '주소 검색' })).toBeEnabled()
})

test('취급 조건을 등록 여부로 필터링한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.selectOptions(screen.getByLabelText('등록 상태 필터'), 'REGISTERED')
  expect(screen.getByText('검색 결과 2개')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: / 추가$/ })).not.toBeInTheDocument()

  await user.selectOptions(screen.getByLabelText('등록 상태 필터'), 'UNREGISTERED')
  expect(screen.queryByRole('button', { name: /등록됨 · 수정$/ })).not.toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: / 추가$/ }).length).toBeGreaterThan(0)
})

test('업체 대표 이미지를 선택하면 즉시 업로드한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  const input = await screen.findByLabelText('대표 이미지 선택')
  const image = new File(['logo'], 'logo.png', { type: 'image/png' })
  await user.upload(input, image)

  expect(apiClient.post).toHaveBeenCalledWith(
    '/v1/processors/me/profile/image', expect.any(FormData), { headers: { 'Content-Type': undefined } },
  )
  const body = vi.mocked(apiClient.post).mock.calls[0][1] as FormData
  expect(body.get('file')).toBe(image)
  expect(await screen.findByText('대표 이미지가 저장되었어요.')).toBeInTheDocument()
})

test('등록된 조건과 지역 변경을 각각 즉시 저장한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: '업체 주소 등록' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨 · 수정' }))
  expect(screen.getByRole('dialog', { name: '데님 · 세척 조건 수정' })).toBeInTheDocument()
  expect(screen.getByLabelText('최소 처리 수량')).toHaveValue('100')

  await user.clear(screen.getByLabelText('최소 처리 수량'))
  await user.type(screen.getByLabelText('최소 처리 수량'), '200')
  await user.click(screen.getByRole('button', { name: '변경사항 적용' }))
  expect(await screen.findByText('취급 조건이 저장되었어요.')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '주소 검색' }))
  expect(await screen.findByText('업체 주소가 저장되었어요.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '가공업체 정보 저장' })).not.toBeInTheDocument()

  expect(apiClient.put).toHaveBeenNthCalledWith(1, '/v1/processors/me/capabilities', { capabilities: [
    { materialType: 'DENIM', processType: 'CLEANING', minQuantity: 200, maxQuantity: 5000 },
    { materialType: 'COTTON', processType: 'CUTTING', minQuantity: 50, maxQuantity: 2000 },
  ] })
  expect(apiClient.put).toHaveBeenNthCalledWith(2, '/v1/processors/me/lead-times', { leadTimes: [
    { materialType: 'COTTON', processType: 'CUTTING', maxQuantity: 2000, leadDays: 6 },
    { materialType: 'DENIM', processType: 'CLEANING', maxQuantity: 5000, leadDays: 4 },
  ] })
  expect(apiClient.put).toHaveBeenNthCalledWith(3, '/v1/processors/me/profile', { region: '서울 성동구 왕십리로 83-21' })
})

test('모달의 수량 입력은 스핀 버튼 없이 숫자를 직접 입력한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨 · 수정' }))
  for (const input of [screen.getByLabelText('최소 처리 수량'), screen.getByLabelText('최대 처리 수량'), screen.getByLabelText('데님 · 세척 구간 최대 수량 1')]) {
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveAttribute('inputmode', 'numeric')
  }
})

test('검색 결과의 추가 버튼으로 모달을 열고 새 조건을 적용한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.selectOptions(screen.getByLabelText('소재 필터'), 'SILK')
  await user.click(screen.getByRole('checkbox', { name: '세척' }))
  await user.click(screen.getByRole('button', { name: '실크 · 세척 추가' }))

  expect(screen.getByRole('dialog', { name: '실크 · 세척 조건 추가' })).toBeInTheDocument()
  expect(screen.getByText('등록된 취급 조건 2개')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '조건 추가' }))
  expect(screen.getByText('등록된 취급 조건 3개')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '실크 · 세척 등록됨 · 수정' })).toBeEnabled()
})

test('모달에서 소요일 구간의 최대 수량을 직접 입력해 추가한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨 · 수정' }))
  await user.type(screen.getByLabelText('데님 · 세척 새 구간 최대 수량'), '1000')
  await user.click(screen.getByRole('button', { name: '데님 · 세척 소요일 구간 추가' }))

  expect(screen.getByLabelText('데님 · 세척 구간 최대 수량 1')).toHaveValue('1000')
  expect(screen.getByText('1,001 g')).toBeInTheDocument()
  expect(screen.getByLabelText('데님 · 세척 구간 최대 수량 2')).toHaveValue('5000')
})

test('모달을 취소하면 편집 내용을 적용하지 않는다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.click(screen.getByRole('button', { name: '면 · 재단 등록됨 · 수정' }))
  await user.clear(screen.getByLabelText('최소 처리 수량'))
  await user.type(screen.getByLabelText('최소 처리 수량'), '700')
  await user.click(screen.getByRole('button', { name: '취소' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: '면 · 재단 등록됨 · 수정' }))
  expect(screen.getByLabelText('최소 처리 수량')).toHaveValue('50')
})

test('모달 변경사항을 적용하면 즉시 저장하고 Brand 미리보기를 갱신한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: 'Brand 검색 노출 미리보기' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨 · 수정' }))
  await user.clear(screen.getByLabelText('최소 처리 수량'))
  await user.type(screen.getByLabelText('최소 처리 수량'), '200')
  await user.click(screen.getByRole('button', { name: '변경사항 적용' }))
  expect(await screen.findByText('취급 조건이 저장되었어요.')).toBeInTheDocument()
  expect(screen.getByText('데님 · 세척 · 200~5,000 g')).toBeInTheDocument()
})
