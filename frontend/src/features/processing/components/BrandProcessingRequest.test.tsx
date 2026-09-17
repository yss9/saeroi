import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import { BrandProcessingPage } from '../../../pages/processing/BrandProcessingPage.tsx'
vi.mock('../../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))
beforeEach(() => {
  useAuthStore.setState({
    user: { id: 1, email: 'brand@test.com', name: '테스트 브랜드', role: 'BRAND', walletAddress: null },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
  vi.mocked(apiClient.get).mockImplementation(async (url) => ({ data: url.includes('materials') ? [{ materialId: 1, materialCode: 'MAT-1', name: '폐데님', materialType: 'DENIM', quantity: 1000, lockedQuantity: 200, availableQuantity: 800 }] : { items: [{ processorId: 2, name: '새로 가공소', region: '서울', minQuantity: 1, maxQuantity: 1000, estimatedLeadTimes: [{ processType: 'CLEANING', leadDays: 3 }, { processType: 'CUTTING', leadDays: 5 }] }], total: 1, page: 0, size: 12 } }))
  vi.mocked(apiClient.post).mockResolvedValue({ data: { requestId: 7, requestCode: 'PR-7', status: 'QUOTING', quantity: 500, processTypes: ['CLEANING'], expiresAt: '2026-09-14T10:00:00', createdAt: '2026-09-07T10:00:00' } })
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})
function setup() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><MemoryRouter><BrandProcessingPage /></MemoryRouter></QueryClientProvider>)
  return userEvent.setup()
}
async function conditions() {
  const user = setup()
  expect(screen.queryByText('소재와 수량, 필요한 가공 목적을 선택해 주세요.')).not.toBeInTheDocument()
  await user.selectOptions(await screen.findByLabelText('보유 소재'), '1')
  await user.type(screen.getByLabelText('가공 수량 (g)'), '500')
  await user.click(screen.getByLabelText('세척'))
  return user
}
it('예약 수량을 제외한 가용량 초과와 소수를 차단한다', async () => {
  const user = await conditions()
  await user.clear(screen.getByLabelText('가공 수량 (g)'))
  await user.type(screen.getByLabelText('가공 수량 (g)'), '801')
  expect(screen.getByRole('button', { name: '검색' })).toBeDisabled()
  expect(screen.getByRole('alert')).toHaveTextContent('가용 수량')
  await user.clear(screen.getByLabelText('가공 수량 (g)'))
  await user.type(screen.getByLabelText('가공 수량 (g)'), '0.5')
  expect(screen.getByRole('button', { name: '검색' })).toBeDisabled()
})
it('처음 진입하면 등록된 가공업체 전체를 표시한다', async () => {
  setup()
  expect(await screen.findByRole('radio', { name: /새로 가공소/ })).toBeInTheDocument()
  expect(screen.getByText('전체 업체')).toBeInTheDocument()
  expect(screen.queryByText('등록된 가공업체 전체를 보고 있어요.')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '검색' })).toBeDisabled()
})
it('복수 목적 검색 후 한 가공사를 선택하고 신청 및 취소한다', async () => {
  const user = await conditions()
  await user.click(screen.getByLabelText('재단'))
  await user.click(screen.getByRole('button', { name: '검색' }))
  expect(await screen.findByRole('heading', { name: '가공업체 목록' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '검색' })).toBeInTheDocument()
  expect(screen.getByLabelText('보유 소재')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '조건 수정' })).not.toBeInTheDocument()
  expect(await screen.findByText('세척 3일')).toBeInTheDocument()
  expect(screen.getByText('재단 5일')).toBeInTheDocument()
  await user.click(await screen.findByRole('radio', { name: /새로 가공소/ }))
  await user.click(screen.getByRole('button', { name: /신청 내용 확인/ }))
  await user.click(screen.getByRole('button', { name: '가공 신청하기' }))
  expect(await screen.findByRole('heading', { name: '가공 신청이 완료되었어요' })).toBeInTheDocument()
  expect(apiClient.post).toHaveBeenCalledWith('/v1/processing-requests', { materialId: 1, processorId: 2, quantity: 500, processTypes: ['CLEANING', 'CUTTING'] })
  vi.mocked(apiClient.post).mockResolvedValueOnce({ data: 'CANCELED' })
  await user.click(screen.getByRole('button', { name: '신청 취소' }))
  expect(await screen.findByRole('heading', { name: '가공 신청이 취소되었어요' })).toBeInTheDocument()
})
it('조건 변경 후 기존 가공사 선택을 재사용하지 않는다', async () => {
  const user = await conditions()
  await user.click(screen.getByRole('button', { name: '검색' }))
  await user.click(await screen.findByRole('radio', { name: /새로 가공소/ }))
  await user.click(screen.getByLabelText('재단'))
  expect(await screen.findByRole('button', { name: /신청 내용 확인/ })).toBeDisabled()
})
it('일치하는 가공사가 없으면 조건 완화 안내를 표시한다', async () => {
  const user = await conditions()
  vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { items: [], total: 0, page: 0, size: 12 } })
  await user.click(screen.getByRole('button', { name: '검색' }))
  expect(await screen.findByText('조건에 맞는 가공업체가 없어요')).toBeInTheDocument()
  expect(screen.getByText(/수량이나 가공 목적을 조정/)).toBeInTheDocument()
})
it('가용량 전체를 신청할 수 있지만 안전한 정수 범위를 넘는 수량은 막는다', async () => {
  const user = await conditions()
  await user.clear(screen.getByLabelText('가공 수량 (g)'))
  await user.type(screen.getByLabelText('가공 수량 (g)'), '800')
  expect(screen.getByRole('button', { name: '검색' })).toBeEnabled()
  await user.clear(screen.getByLabelText('가공 수량 (g)'))
  await user.type(screen.getByLabelText('가공 수량 (g)'), '9007199254740992')
  expect(screen.getByRole('button', { name: '검색' })).toBeDisabled()
})
it('실패한 신청은 성공으로 표시하지 않고 다시 시도할 수 있다', async () => {
  const user = await conditions()
  await user.click(screen.getByRole('button', { name: '검색' }))
  await user.click(await screen.findByRole('radio', { name: /새로 가공소/ }))
  await user.click(screen.getByRole('button', { name: /신청 내용 확인/ }))
  vi.mocked(apiClient.post).mockRejectedValueOnce(new Error('가용 수량이 변경되었어요'))
  await user.click(screen.getByRole('button', { name: '가공 신청하기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('가용 수량이 변경되었어요')
  expect(screen.queryByRole('heading', { name: '가공 신청이 완료되었어요' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '가공 신청하기' })).toBeEnabled()
})
it('요청 처리 중에는 중복 신청을 막는다', async () => {
  const user = await conditions()
  await user.click(screen.getByRole('button', { name: '검색' }))
  await user.click(await screen.findByRole('radio', { name: /새로 가공소/ }))
  await user.click(screen.getByRole('button', { name: /신청 내용 확인/ }))
  vi.mocked(apiClient.post).mockImplementationOnce(() => new Promise(() => {}))
  await user.dblClick(screen.getByRole('button', { name: '가공 신청하기' }))
  expect(screen.getByRole('button', { name: /신청 중/ })).toBeDisabled()
  expect(apiClient.post).toHaveBeenCalledTimes(1)
})
// 서버 시각은 이미 한국 시각이다 — 백엔드의 모든 시각 소스가 Asia/Seoul 로 고정돼 있고
// (common/config/ClockConfig), 시간대 없이 내려온다. 여기서 UTC 로 보고 Z 를 붙이면
// 브라우저가 한 번 더 옮겨 아홉 시간이 더해진다.
it('시간대 없이 내려온 만료일을 그대로(한국 시각) 표시한다', async () => {
  const user = await conditions()
  await user.click(screen.getByRole('button', { name: '검색' }))
  await user.click(await screen.findByRole('radio', { name: /새로 가공소/ }))
  await user.click(screen.getByRole('button', { name: /신청 내용 확인/ }))
  await user.click(screen.getByRole('button', { name: '가공 신청하기' }))
  await screen.findByRole('heading', { name: '가공 신청이 완료되었어요' })
  expect(screen.getByText(/만료 일시:/)).toHaveTextContent(
    new Date('2026-09-14T10:00:00').toLocaleString('ko-KR'),
  )
})
it('신청 후 가용 재고가 감소해도 완료 요약의 신청 수량을 유지한다', async () => {
  const user = await conditions()
  await user.click(screen.getByRole('button', { name: '검색' }))
  await user.click(await screen.findByRole('radio', { name: /새로 가공소/ }))
  await user.click(screen.getByRole('button', { name: /신청 내용 확인/ }))
  const originalGet = vi.mocked(apiClient.get).getMockImplementation()!
  vi.mocked(apiClient.get).mockImplementation(async (url, config) => url.includes('materials') ? { data: [{ materialId: 1, materialCode: 'MAT-1', name: '폐데님 (재고 갱신)', materialType: 'DENIM', quantity: 1000, lockedQuantity: 700, availableQuantity: 300 }] } : originalGet(url, config))
  await user.click(screen.getByRole('button', { name: '가공 신청하기' }))
  await screen.findByText('폐데님 (재고 갱신)')
  expect(screen.getByText('500 g')).toBeInTheDocument()
})
