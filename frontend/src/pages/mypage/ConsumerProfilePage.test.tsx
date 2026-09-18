import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsumerProfilePage } from './ConsumerProfilePage.tsx'

vi.mock('../landing/components/Header.tsx', () => ({ Header: () => <header /> }))
vi.mock('../landing/components/Footer.tsx', () => ({ Footer: () => <footer /> }))

vi.mock('../../features/auth/hooks/useAuth.ts', () => ({
  useAuth: () => ({ user: { id: 1, name: '새로이 고객', email: 'consumer@saeroi.test', role: 'CONSUMER' } }),
}))

vi.mock('../../features/auth/hooks/useLogout.ts', () => ({
  useLogout: () => ({ mutate: vi.fn(), isPending: false, error: null }),
}))

vi.mock('../../features/user/hooks/useConsumerMyPage.ts', () => ({
  useConsumerMyPage: () => ({ data: { defaultAddress: null } }),
}))

const profileApi = vi.hoisted(() => ({
  getMyConsumerProfile: vi.fn(),
  updateMyConsumerProfile: vi.fn(),
}))
vi.mock('../../features/user/api/consumerProfileApi.ts', () => profileApi)

const passwordApi = vi.hoisted(() => ({ changePassword: vi.fn() }))
vi.mock('../../features/company/api/changePassword.ts', () => passwordApi)

const baseProfile = {
  id: 1,
  email: 'consumer@saeroi.test',
  name: '새로이 고객',
  phone: '010-1234-5678',
  role: 'CONSUMER',
  status: 'ACTIVE',
  createdAt: '2026-09-18T00:00:00',
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const user = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ConsumerProfilePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return user
}

async function unlockGate() {
  const user = renderPage()
  await user.type(screen.getByLabelText('현재 비밀번호'), 'anything')
  await user.click(screen.getByRole('button', { name: '확인' }))
  await screen.findByText(baseProfile.phone)
  return user
}

function phoneRow() {
  return screen.getByText('전화번호').closest('.consumer-profile-edit__field-row') as HTMLElement
}

/**
 * 실제 백엔드처럼 PUT 이후의 GET 도 새 값을 돌려주게 맞춘다. 저장 성공 뒤 목록을 다시
 * 읽는 훅(`useUpdateConsumerProfile`의 invalidateQueries)이 있어, GET mock 이 고정값이면
 * 재조회가 방금 저장한 값을 옛 값으로 덮어써 버린다.
 */
function mockPersistedUpdate(overrides: Partial<typeof baseProfile>) {
  const updated = { ...baseProfile, ...overrides }
  profileApi.updateMyConsumerProfile.mockResolvedValue(updated)
  profileApi.getMyConsumerProfile.mockImplementation(async () => updated)
}

beforeEach(() => {
  vi.clearAllMocks()
  profileApi.getMyConsumerProfile.mockResolvedValue(baseProfile)
})
afterEach(cleanup)

describe('ConsumerProfilePage 전화번호 입력', () => {
  it('숫자만 입력해도 하이픈을 자동으로 넣는다', async () => {
    const user = await unlockGate()

    await user.click(within(phoneRow()).getByRole('button', { name: '변경' }))
    const phoneInput = screen.getByPlaceholderText('010-1234-5678')
    await user.clear(phoneInput)
    await user.type(phoneInput, '01099998888')

    expect(phoneInput).toHaveValue('010-9999-8888')
  })

  it('형식에 맞지 않는 번호는 저장을 막고 서버로 보내지 않는다', async () => {
    const user = await unlockGate()

    await user.click(within(phoneRow()).getByRole('button', { name: '변경' }))
    const phoneInput = screen.getByPlaceholderText('010-1234-5678')
    await user.clear(phoneInput)
    await user.type(phoneInput, '010123456')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(screen.getByText('올바른 휴대전화 번호를 입력해 주세요.')).toBeInTheDocument()
    // 저장이 막혀 편집 모드가 그대로 유지된다.
    expect(screen.getByPlaceholderText('010-1234-5678')).toBeInTheDocument()
    expect(profileApi.updateMyConsumerProfile).not.toHaveBeenCalled()
  })

  // 지금까지 겪던 증상 — 저장을 눌러도 서버에 남지 않아 개인정보 화면에 반영되지 않았다.
  it('저장하면 서버에 실제로 반영되고 화면에도 새 값이 보인다', async () => {
    const user = await unlockGate()
    // 저장 성공 뒤에는 목록을 다시 읽는다(invalidateQueries) — 실제 서버라면 방금 저장한
    // 값을 그대로 돌려주므로, mock도 이후 GET이 새 값을 주도록 맞춘다. 첫 조회(unlockGate
    // 안에서 옛 값을 기다림) 뒤에 걸어야 한다.
    mockPersistedUpdate({ phone: '010-9999-8888' })

    await user.click(within(phoneRow()).getByRole('button', { name: '변경' }))
    const phoneInput = screen.getByPlaceholderText('010-1234-5678')
    await user.clear(phoneInput)
    await user.type(phoneInput, '01099998888')
    await user.click(screen.getByRole('button', { name: '저장' }))

    // 서버는 이름·연락처를 한 요청으로만 받는다 — 이름은 그대로, 연락처만 새 값이어야 한다.
    await waitFor(() => expect(profileApi.updateMyConsumerProfile).toHaveBeenCalled())
    expect(profileApi.updateMyConsumerProfile.mock.calls[0][0]).toEqual({
      name: baseProfile.name,
      phone: '010-9999-8888',
    })
    expect(await screen.findByText('010-9999-8888')).toBeInTheDocument()
    expect(screen.getByText('회원 정보가 저장되었습니다.')).toBeInTheDocument()
  })
})

describe('ConsumerProfilePage 이름 수정', () => {
  it('이름을 바꿔 저장하면 나머지 칸(연락처)은 지금 값 그대로 함께 보낸다', async () => {
    const nameRow = () => screen.getByText('이름').closest('.consumer-profile-edit__field-row') as HTMLElement
    const user = await unlockGate()
    mockPersistedUpdate({ name: '김새로이' })

    await user.click(within(nameRow()).getByRole('button', { name: '변경' }))
    const nameInput = within(nameRow()).getByRole('textbox')
    await user.clear(nameInput)
    await user.type(nameInput, '김새로이')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(profileApi.updateMyConsumerProfile).toHaveBeenCalled())
    expect(profileApi.updateMyConsumerProfile.mock.calls[0][0]).toEqual({
      name: '김새로이',
      phone: baseProfile.phone,
    })
    expect(await screen.findByText('김새로이')).toBeInTheDocument()
  })
})
