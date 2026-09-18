// 비밀번호 변경.
//
// 고정하는 것 셋.
//
//   1) **현재 비밀번호를 함께 보낸다.** 본인 확인이라 편의를 위해 뺄 수 있는 칸이 아니다.
//   2) **규칙은 가입과 같다.** 화면이 서버보다 느슨하면 다 적고 저장한 뒤에야 400 을 본다.
//   3) **확인 칸이 어긋나면 보내지 않는다.** 오타 하나로 못 들어가는 계정이 만들어진다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PasswordChangeForm } from './PasswordChangeForm.tsx'

const api = vi.hoisted(() => ({ changePassword: vi.fn() }))
vi.mock('../api/changePassword.ts', () => ({ changePassword: api.changePassword }))

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <PasswordChangeForm />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  options: { current?: string; next?: string; confirm?: string } = {},
) {
  const current = options.current ?? 'Old1234!'
  const next = options.next ?? 'New1234!'
  const confirm = options.confirm ?? next

  await user.type(screen.getByLabelText('현재 비밀번호'), current)
  await user.type(screen.getByLabelText('새 비밀번호'), next)
  await user.type(screen.getByLabelText('새 비밀번호 확인'), confirm)
}

beforeEach(() => {
  api.changePassword.mockResolvedValue(undefined)
})
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('PasswordChangeForm', () => {
  it('현재 비밀번호와 새 비밀번호를 함께 보낸다', async () => {
    const user = renderForm()
    await fill(user)

    await user.click(screen.getByRole('button', { name: '비밀번호 변경' }))

    // TanStack Query 가 두 번째 인자로 컨텍스트를 넘기므로 첫 인자만 본다.
    await waitFor(() => expect(api.changePassword).toHaveBeenCalled())
    expect(api.changePassword.mock.calls[0][0]).toEqual({
      currentPassword: 'Old1234!',
      newPassword: 'New1234!',
    })
    // 바꾼 뒤 칸을 비운다 — 남겨 두면 다음 사람이 화면 앞에 앉았을 때 그대로 보인다.
    expect(await screen.findByRole('status')).toHaveTextContent('비밀번호를 바꿨습니다')
    expect(screen.getByLabelText('현재 비밀번호')).toHaveValue('')
  })

  it('규칙에 맞지 않는 새 비밀번호는 보내지 않는다', async () => {
    const user = renderForm()
    await fill(user, { next: 'onlyletters', confirm: 'onlyletters' })

    await user.click(screen.getByRole('button', { name: '비밀번호 변경' }))

    expect(await screen.findByText(/8자 이상 입력해 주세요/)).toBeInTheDocument()
    expect(api.changePassword).not.toHaveBeenCalled()
  })

  it('확인 칸이 어긋나면 보내지 않는다', async () => {
    const user = renderForm()
    await fill(user, { confirm: 'New1234!x' })

    await user.click(screen.getByRole('button', { name: '비밀번호 변경' }))

    expect(await screen.findByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
    expect(api.changePassword).not.toHaveBeenCalled()
  })

  // 서버도 거절하겠지만, 바꾸지 않은 값을 보내고 기다릴 이유가 없다.
  it('지금 쓰는 비밀번호와 같으면 보내지 않는다', async () => {
    const user = renderForm()
    await fill(user, { current: 'Same1234!', next: 'Same1234!' })

    await user.click(screen.getByRole('button', { name: '비밀번호 변경' }))

    expect(await screen.findByText(/다르게 정해 주세요/)).toBeInTheDocument()
    expect(api.changePassword).not.toHaveBeenCalled()
  })
})
