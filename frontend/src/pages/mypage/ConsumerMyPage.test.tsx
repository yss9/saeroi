import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConsumerMyPageView } from '../../features/user/components/ConsumerMyPageView.tsx'
import { createConsumerMyPageMock } from '../../features/user/mocks/consumerMyPage.mock.ts'

const profile = {
  id: 5,
  name: '새로이 고객',
  email: 'consumer@saeroi.test',
}

function renderView(
  state: Parameters<typeof ConsumerMyPageView>[0]['state'],
  onSignOut?: () => void,
) {
  return render(
    <MemoryRouter>
      <ConsumerMyPageView state={state} onSignOut={onSignOut} />
    </MemoryRouter>,
  )
}

afterEach(cleanup)

describe('ConsumerMyPageView', () => {
  it('renders consumer account, order, and address summaries', () => {
    const data = createConsumerMyPageMock(profile)
    renderView({ status: 'ready', data })

    expect(screen.getByRole('heading', { name: '새로이 고객님, 반갑습니다.' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '주문 현황' })).toBeInTheDocument()
    expect(screen.getAllByRole('article', { name: /주문$/ })).toHaveLength(2)
    expect(screen.getByRole('heading', { name: '기본 배송지' })).toBeInTheDocument()
    expect(screen.getByText('서울특별시 강남구 테헤란로 212', { exact: false })).toBeInTheDocument()
  })

  it('renders loading, error, and empty states separately', () => {
    const { rerender } = renderView({ status: 'loading' })
    expect(screen.getByRole('status', { name: '마이페이지 정보를 불러오는 중' })).toBeInTheDocument()

    rerender(
      <MemoryRouter>
        <ConsumerMyPageView state={{ status: 'error', error: new Error('조회 실패') }} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('조회 실패')

    const emptyData = {
      ...createConsumerMyPageMock(profile),
      recentOrders: [],
      defaultAddress: null,
    }
    rerender(
      <MemoryRouter>
        <ConsumerMyPageView state={{ status: 'ready', data: emptyData }} />
      </MemoryRouter>,
    )
    expect(screen.getByText('최근 주문이 없습니다')).toBeInTheDocument()
    expect(screen.getByText('등록된 배송지가 없습니다')).toBeInTheDocument()
  })

  it('calls the sign-out action from the account summary', async () => {
    const user = userEvent.setup()
    const onSignOut = vi.fn()
    renderView({ status: 'ready', data: createConsumerMyPageMock(profile) }, onSignOut)

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(onSignOut).toHaveBeenCalledOnce()
  })

  it('shows pending and failed sign-out states', () => {
    const data = createConsumerMyPageMock(profile)
    const { rerender } = render(
      <MemoryRouter>
        <ConsumerMyPageView
          state={{ status: 'ready', data }}
          onSignOut={() => undefined}
          signOutPending
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('button', { name: '로그아웃 중…' })).toBeDisabled()

    rerender(
      <MemoryRouter>
        <ConsumerMyPageView
          state={{ status: 'ready', data }}
          onSignOut={() => undefined}
          signOutError="로그아웃에 실패했습니다."
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('로그아웃에 실패했습니다.')
  })
})
