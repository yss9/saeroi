import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NotificationCenter } from './NotificationCenter.tsx'
import type { NotificationPage } from '../types/notification.types.ts'

const mocks = vi.hoisted(() => ({
  read: vi.fn(),
  readAll: vi.fn(),
  query: vi.fn(),
}))

vi.mock('../hooks/useNotifications.ts', () => ({
  useNotifications: () => mocks.query(),
  useReadNotification: () => ({ mutate: mocks.read, isPending: false }),
  useReadAllNotifications: () => ({ mutate: mocks.readAll, isPending: false }),
}))

const notifications: NotificationPage = {
  items: [
    {
      notificationId: 10,
      type: 'PROCESSING_REQUEST_RECEIVED',
      title: '새 가공 요청',
      message: 'Brand A가 가공 요청을 보냈습니다.',
      targetPath: '/processor/jobs',
      referenceType: 'PROCESSING_REQUEST',
      referenceId: 31,
      read: false,
      createdAt: '2026-09-14T09:00:00',
    },
    {
      notificationId: 9,
      type: 'TRADE_SHIPPED',
      title: '소재 발송 완료',
      message: '소재가 발송되었습니다.',
      targetPath: '/trades/22',
      referenceType: 'MATERIAL_TRADE',
      referenceId: 22,
      read: true,
      createdAt: '2026-09-14T08:00:00',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  unreadCount: 1,
}

function renderCenter() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<NotificationCenter enabled />} />
        <Route path="/processor/jobs" element={<h1>가공 작업 페이지</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mocks.query.mockReturnValue({ data: notifications, isLoading: false, isError: false })
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('NotificationCenter', () => {
  it('읽지 않은 개수를 표시하고 알림 목록을 연다', async () => {
    const user = userEvent.setup()
    renderCenter()

    await user.click(screen.getByRole('button', { name: '알림 1개' }))

    expect(screen.getByRole('region', { name: '알림 목록' })).toBeInTheDocument()
    expect(screen.getByText('새 가공 요청')).toBeInTheDocument()
    expect(screen.getByText('소재 발송 완료')).toBeInTheDocument()
  })

  it('알림을 누르면 읽음 처리하고 대상 화면으로 이동한다', async () => {
    const user = userEvent.setup()
    renderCenter()

    await user.click(screen.getByRole('button', { name: '알림 1개' }))
    await user.click(screen.getByRole('button', { name: /새 가공 요청/ }))

    expect(mocks.read).toHaveBeenCalledWith(10)
    expect(screen.getByRole('heading', { name: '가공 작업 페이지' })).toBeInTheDocument()
  })

  it('모두 읽음 처리와 Escape 닫기를 지원한다', async () => {
    const user = userEvent.setup()
    renderCenter()

    await user.click(screen.getByRole('button', { name: '알림 1개' }))
    await user.click(screen.getByRole('button', { name: '모두 읽음' }))
    expect(mocks.readAll).toHaveBeenCalledOnce()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('region', { name: '알림 목록' })).not.toBeInTheDocument()
  })

  it('알림이 없으면 빈 상태를 표시한다', async () => {
    mocks.query.mockReturnValue({
      data: { ...notifications, items: [], totalElements: 0, unreadCount: 0 },
      isLoading: false,
      isError: false,
    })
    const user = userEvent.setup()
    renderCenter()

    await user.click(screen.getByRole('button', { name: '새 알림 없음' }))

    expect(screen.getByText('새로운 알림이 없습니다.')).toBeInTheDocument()
  })

  it('99개를 넘는 읽지 않은 알림은 99+ 배지로 표시한다', () => {
    mocks.query.mockReturnValue({
      data: { ...notifications, unreadCount: 120 },
      isLoading: false,
      isError: false,
    })

    renderCenter()

    expect(screen.getByRole('button', { name: '알림 120개' })).toHaveTextContent('99+')
  })

  it('조회 중과 조회 실패 상태를 패널 안에서 안내한다', async () => {
    const user = userEvent.setup()
    mocks.query.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    const loading = renderCenter()
    await user.click(screen.getByRole('button', { name: '새 알림 없음' }))
    expect(screen.getByText('알림을 불러오는 중입니다.')).toBeInTheDocument()

    loading.unmount()
    mocks.query.mockReturnValue({ data: undefined, isLoading: false, isError: true })
    renderCenter()
    await user.click(screen.getByRole('button', { name: '새 알림 없음' }))
    expect(screen.getByText('알림을 불러오지 못했습니다.')).toBeInTheDocument()
  })

  it('패널 밖을 누르면 목록을 닫는다', async () => {
    const user = userEvent.setup()
    renderCenter()

    await user.click(screen.getByRole('button', { name: '알림 1개' }))
    expect(screen.getByRole('region', { name: '알림 목록' })).toBeInTheDocument()

    await user.click(document.body)
    expect(screen.queryByRole('region', { name: '알림 목록' })).not.toBeInTheDocument()
  })
})
