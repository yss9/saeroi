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
    // 소요일 표는 없어졌다. 가공사가 적는 것은 하루 처리량과 단가 둘뿐이다.
    return { data: [
      { materialType: 'DENIM', processType: 'CLEANING', dailyCapacity: 5000, pricePerKg: 1200 },
      { materialType: 'COTTON', processType: 'CUTTING', dailyCapacity: 2000, pricePerKg: 800 },
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
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨' }))
  expect(screen.getByRole('dialog', { name: '데님 · 세척 조건 수정' })).toBeInTheDocument()
  expect(screen.getByLabelText('하루 처리량')).toHaveValue('5')
  expect(screen.getByLabelText('kg 당 단가')).toHaveValue('1200')

  await user.clear(screen.getByLabelText('하루 처리량'))
  await user.type(screen.getByLabelText('하루 처리량'), '200')
  await user.clear(screen.getByLabelText('kg 당 단가'))
  await user.type(screen.getByLabelText('kg 당 단가'), '3000')
  await user.click(screen.getByRole('button', { name: '변경사항 적용' }))
  expect(await screen.findByText('취급 조건이 저장되었어요.')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '주소 검색' }))
  expect(await screen.findByText('업체 주소가 저장되었어요.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '가공업체 정보 저장' })).not.toBeInTheDocument()

  // 하루 처리량은 g 로 보낸다. 사람이 kg 으로 세니 화면만 kg 으로 묻는다.
  expect(apiClient.put).toHaveBeenNthCalledWith(1, '/v1/processors/me/capabilities', { capabilities: [
    { materialType: 'DENIM', processType: 'CLEANING', dailyCapacity: 200_000, pricePerKg: 3000 },
    { materialType: 'COTTON', processType: 'CUTTING', dailyCapacity: 2000, pricePerKg: 800 },
  ] })
  // 소요일 표는 없앴다 — 두 번째 호출은 주소 저장이다.
  expect(apiClient.put).toHaveBeenNthCalledWith(2, '/v1/processors/me/profile', { region: '서울 성동구 왕십리로 83-21' })
})

test('모달의 수량 입력은 스핀 버튼 없이 숫자를 직접 입력한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨' }))
  expect(screen.getByLabelText('하루 처리량')).toHaveAttribute('type', 'text')
  expect(screen.getByLabelText('하루 처리량')).toHaveAttribute('inputmode', 'decimal')
  expect(screen.getByLabelText('kg 당 단가')).toHaveAttribute('type', 'text')
  expect(screen.getByLabelText('kg 당 단가')).toHaveAttribute('inputmode', 'numeric')
})

test('검색 결과의 추가 버튼으로 모달을 열고 새 조건을 적용한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  // 소재도 공정과 같은 체크박스다 — 여러 소재를 한 번에 훑어 조건을 한 벌로 적는다.
  await user.click(screen.getByRole('checkbox', { name: '실크' }))
  await user.click(screen.getByRole('checkbox', { name: '세척' }))
  await user.click(screen.getByRole('button', { name: '실크 · 세척 추가' }))

  expect(screen.getByRole('dialog', { name: '실크 · 세척 조건 추가' })).toBeInTheDocument()
  expect(screen.getByText('등록된 취급 조건 2개')).toBeInTheDocument()
  // 하루 처리량과 단가 없이는 등록되지 않는다 — 그 둘이 브랜드가 보는 값이다.
  await user.clear(screen.getByLabelText('하루 처리량'))
  await user.type(screen.getByLabelText('하루 처리량'), '50')
  await user.type(screen.getByLabelText('kg 당 단가'), '4000')
  await user.click(screen.getByRole('button', { name: '조건 추가' }))
  expect(screen.getByText('등록된 취급 조건 3개')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '실크 · 세척 등록됨' })).toBeEnabled()
})

// 적은 두 숫자로 브랜드가 무엇을 보게 되는지 그 자리에서 보여준다 — 따로 계산하게 두지 않는다.
test('하루 처리량과 단가를 적으면 브랜드에게 보일 금액과 소요일을 알려준다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨' }))
  await user.clear(screen.getByLabelText('하루 처리량'))
  await user.type(screen.getByLabelText('하루 처리량'), '100')
  await user.clear(screen.getByLabelText('kg 당 단가'))
  await user.type(screen.getByLabelText('kg 당 단가'), '2000')

  expect(screen.getByText(/200,000원 · 1일/)).toBeInTheDocument()
})

test('모달을 취소하면 편집 내용을 적용하지 않는다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: '취급 조건 검색' })
  await user.click(screen.getByRole('button', { name: '면 · 재단 등록됨' }))
  await user.clear(screen.getByLabelText('하루 처리량'))
  await user.type(screen.getByLabelText('하루 처리량'), '700')
  await user.click(screen.getByRole('button', { name: '취소' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: '면 · 재단 등록됨' }))
  expect(screen.getByLabelText('하루 처리량')).toHaveValue('2')
  expect(screen.getByLabelText('kg 당 단가')).toHaveValue('800')
})

// 등록한 조건을 고치러 검색 칸으로 되돌아가지 않아도 된다 — 미리보기에 이미 보이는 줄이
// 그 조건의 입구다.
test('미리보기의 등록된 조건을 누르면 수정 모달이 열린다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  await screen.findByRole('heading', { name: 'Brand 검색 노출 미리보기' })
  expect(screen.getByText('클릭하면 수정할 수 있어요.')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: '데님 · 세척 조건 수정' }))

  expect(screen.getByRole('dialog', { name: '데님 · 세척 조건 수정' })).toBeInTheDocument()
  expect(screen.getByLabelText('하루 처리량')).toHaveValue('5')
})

test('모달 변경사항을 적용하면 즉시 저장하고 Brand 미리보기를 갱신한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: 'Brand 검색 노출 미리보기' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '데님 · 세척 등록됨' }))
  await user.clear(screen.getByLabelText('하루 처리량'))
  await user.type(screen.getByLabelText('하루 처리량'), '200')
  await user.clear(screen.getByLabelText('kg 당 단가'))
  await user.type(screen.getByLabelText('kg 당 단가'), '3000')
  await user.click(screen.getByRole('button', { name: '변경사항 적용' }))
  expect(await screen.findByText('취급 조건이 저장되었어요.')).toBeInTheDocument()
  expect(screen.getByText('데님 · 세척 · 하루 200 kg · kg당 3,000원')).toBeInTheDocument()
})
