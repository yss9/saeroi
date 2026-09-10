import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { ProcessorProfilePage } from './ProcessorProfilePage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), put: vi.fn() } }))
const openKakaoPostcode = vi.hoisted(() => vi.fn())
vi.mock('../../utils/kakaoPostcode.ts', () => ({ openKakaoPostcode }))

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({
    user: { id: 3, email: 'processor@test.com', name: '리뉴 가공소', role: 'PROCESSOR', walletAddress: null },
    accessToken: 'token', isAuthenticated: true,
  })
  vi.mocked(apiClient.get).mockImplementation(async url => {
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
  openKakaoPostcode.mockImplementation(async onComplete => {
    onComplete({ zonecode: '04790', address: '서울 성동구 왕십리로 83-21' })
  })
})

afterEach(cleanup)

test('Processor가 g 단위 가공 조건과 소요일을 불러오고 일괄 저장한다', async () => {
  const user = userEvent.setup()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><MemoryRouter><ProcessorProfilePage /></MemoryRouter></QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: '가공업체 정보 등록' })).toBeInTheDocument()
  expect(screen.getByDisplayValue('서울 성동구')).toBeInTheDocument()
  expect(screen.getByLabelText('최소 수량 1')).toHaveValue(100)
  expect(screen.getAllByText('g').length).toBeGreaterThan(0)

  await user.click(screen.getByRole('button', { name: '주소 검색' }))
  expect(screen.getByLabelText('지역')).toHaveValue('서울 성동구 왕십리로 83-21')

  await user.clear(screen.getByLabelText('최소 수량 1'))
  await user.type(screen.getByLabelText('최소 수량 1'), '200')
  await user.click(screen.getByRole('button', { name: '가공업체 정보 저장' }))

  expect(apiClient.put).toHaveBeenNthCalledWith(1, '/v1/processors/me/profile', { region: '서울 성동구 왕십리로 83-21' })
  expect(apiClient.put).toHaveBeenNthCalledWith(2, '/v1/processors/me/capabilities', { capabilities: [
    { materialType: 'DENIM', processType: 'CLEANING', minQuantity: 200, maxQuantity: 5000 },
    { materialType: 'COTTON', processType: 'CUTTING', minQuantity: 50, maxQuantity: 2000 },
  ] })
  expect(apiClient.put).toHaveBeenNthCalledWith(3, '/v1/processors/me/lead-times', { leadTimes: [
    { materialType: 'COTTON', processType: 'CUTTING', maxQuantity: 2000, leadDays: 6 },
    { materialType: 'DENIM', processType: 'CLEANING', maxQuantity: 5000, leadDays: 4 },
  ] })
  expect(await screen.findByText('가공업체 정보가 저장되었어요.')).toBeInTheDocument()
})
