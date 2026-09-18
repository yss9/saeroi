import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
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

afterEach(cleanup)

async function unlockGate() {
  const user = userEvent.setup()
  render(
    <MemoryRouter>
      <ConsumerProfilePage />
    </MemoryRouter>,
  )
  await user.type(screen.getByLabelText('현재 비밀번호'), 'anything')
  await user.click(screen.getByRole('button', { name: '확인' }))
  return user
}

function phoneRow() {
  return screen.getByText('전화번호').closest('.consumer-profile-edit__field-row') as HTMLElement
}

describe('ConsumerProfilePage 전화번호 입력', () => {
  it('숫자만 입력해도 하이픈을 자동으로 넣는다', async () => {
    const user = await unlockGate()

    await user.click(within(phoneRow()).getByRole('button', { name: '변경' }))
    const phoneInput = screen.getByPlaceholderText('010-1234-5678')
    await user.type(phoneInput, '01012345678')

    expect(phoneInput).toHaveValue('010-1234-5678')
  })

  it('형식에 맞지 않는 번호는 저장을 막고 안내를 보여준다', async () => {
    const user = await unlockGate()

    await user.click(within(phoneRow()).getByRole('button', { name: '변경' }))
    const phoneInput = screen.getByPlaceholderText('010-1234-5678')
    await user.type(phoneInput, '010123456')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(screen.getByText('올바른 휴대전화 번호를 입력해 주세요.')).toBeInTheDocument()
    // 저장이 막혀 편집 모드가 그대로 유지된다.
    expect(screen.getByPlaceholderText('010-1234-5678')).toBeInTheDocument()
  })

  it('올바른 번호는 저장 후 화면에 하이픈 포함 형태로 보인다', async () => {
    const user = await unlockGate()

    await user.click(within(phoneRow()).getByRole('button', { name: '변경' }))
    const phoneInput = screen.getByPlaceholderText('010-1234-5678')
    await user.type(phoneInput, '01012345678')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(screen.getByText('010-1234-5678')).toBeInTheDocument()
  })
})
