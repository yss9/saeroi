import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import { BrandProcessingPage } from '../../../pages/processing/BrandProcessingPage.tsx'
vi.mock('../../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))
// 가공사 검색 응답은 백엔드가 견적 단계를 들어내면서 계약이 바뀌었다. 수량 구간이 사라지고
// 하루 처리량·단가가 들어왔으며, 금액과 소요일은 서버가 계산해 내려준다(화면은 단가만 적는다).
//   - 조건 검색: estimatedPrice·estimatedDays 가 채워진다
//   - 필터 없이 둘러보기: 수량을 모르므로 둘 다 null 이고 estimatedLeadTimes 는 빈 배열이다
const MATCHED_PROCESSOR = { processorId: 2, name: '새로 가공소', region: '서울', dailyCapacity: 8000, pricePerKg: 3000, estimatedPrice: 1500, estimatedDays: 2, estimatedLeadTimes: [{ processType: 'CLEANING', leadDays: 1 }, { processType: 'CUTTING', leadDays: 1 }] }
const BROWSED_PROCESSOR = { processorId: 2, name: '새로 가공소', region: '서울', dailyCapacity: 10000, pricePerKg: 1200, estimatedPrice: null, estimatedDays: null, estimatedLeadTimes: [] }
/** 소재는 혈통을 따라 누적된 가공 이력을 함께 싣는다 — 가공 전이면 빈 배열이다. */
const MATERIAL = { materialId: 1, materialCode: 'MAT-1', name: '폐데님', materialType: 'DENIM', quantity: 1000, lockedQuantity: 200, availableQuantity: 800, completedProcessTypes: [] }
function mockProcessors(item: unknown) {
  vi.mocked(apiClient.get).mockImplementation(async (url) => ({ data: url.includes('materials') ? [MATERIAL] : { items: [item], total: 1, page: 0, size: 12 } }))
}

beforeEach(() => {
  useAuthStore.setState({
    user: { id: 1, email: 'brand@test.com', name: '테스트 브랜드', role: 'BRAND', walletAddress: null },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
  vi.mocked(apiClient.get).mockImplementation(async (url) => ({ data: url.includes('materials') ? [MATERIAL] : { items: [MATCHED_PROCESSOR], total: 1, page: 0, size: 12 } }))
  // 요청이 곧 작업을 만든다 — 응답에 jobId 와 확정된 가공비가 함께 온다.
  vi.mocked(apiClient.post).mockResolvedValue({ data: { requestId: 7, requestCode: 'PR-7', status: 'REQUESTED', quantity: 500, processTypes: ['CLEANING'], expiresAt: '2026-09-14T10:00:00', createdAt: '2026-09-07T10:00:00', jobId: 44, fee: 1500, paymentMethod: 'MOCK', paymentExpiresAt: '2026-09-07T10:15:00' } })
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
it('보유 소재 선택지와 선택한 소재에 가공 이력을 함께 보여준다', async () => {
  const user = setup()

  expect(await screen.findByRole('option', { name: /^폐데님 · 가공 전 · MAT-1/ })).toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('보유 소재'), '1')
  expect(screen.getAllByText('가공 전').length).toBeGreaterThan(0)
})

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
  expect(await screen.findByText('세척 1일')).toBeInTheDocument()
  expect(screen.getByText('재단 1일')).toBeInTheDocument()
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
  vi.mocked(apiClient.get).mockImplementation(async (url, config) => url.includes('materials') ? { data: [{ ...MATERIAL, name: '폐데님 (재고 갱신)', lockedQuantity: 700, availableQuantity: 300 }] } : originalGet(url, config))
  await user.click(screen.getByRole('button', { name: '가공 신청하기' }))
  await screen.findByText('폐데님 (재고 갱신)')
  expect(screen.getByText('500 g')).toBeInTheDocument()
})

// 배포 사고 재현: 둘러보기 응답에는 예상 금액도 소요일도 없다. 그 자리를 수량 구간으로
// 그리던 화면이 없는 값을 포맷하다 통째로 죽었다 — 진입만 해도 터지는 자리라 목록이
// 그려지는지까지 확인한다.
it('예상 금액이 없는 둘러보기 응답에서도 업체 목록을 그린다', async () => {
  mockProcessors(BROWSED_PROCESSOR)
  setup()
  expect(await screen.findByRole('radio', { name: /새로 가공소/ })).toBeInTheDocument()
  // 카드는 고를 때 쓰는 값만 적는다 — 하루 처리량도 kg당 단가도 그 자리가 아니다.
  expect(screen.queryByText(/하루 처리량/)).not.toBeInTheDocument()
  expect(screen.queryByText(/kg당 단가/)).not.toBeInTheDocument()
  expect(screen.getByText('수량과 가공 목적을 정하면 금액과 소요일을 계산해요')).toBeInTheDocument()
})

it('조건 검색 결과에는 서버가 계산한 예상 금액과 소요일을 보여준다', async () => {
  const user = await conditions()
  await user.click(screen.getByLabelText('재단'))
  await user.click(screen.getByRole('button', { name: '검색' }))
  expect(await screen.findByRole('radio', { name: /새로 가공소/ })).toBeInTheDocument()
  expect(screen.getByText(/예상 금액/)).toHaveTextContent('1,500원')
  expect(screen.getByText(/예상 소요일/)).toHaveTextContent('2일')
  expect(screen.getByText(/공정별 소요일/)).toHaveTextContent('세척 1일')
  expect(screen.getByText(/공정별 소요일/)).toHaveTextContent('재단 1일')
})
