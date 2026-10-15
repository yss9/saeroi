import { act, cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { usePaymentCountdown } from './usePaymentCountdown.ts'

function CountdownProbe({ remainingSeconds }: { remainingSeconds: number }) {
  const countdown = usePaymentCountdown(remainingSeconds, '2026-09-20T00:30:00+09:00')
  return <button type="button" disabled={countdown.expired}>{countdown.label}</button>
}

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

describe('usePaymentCountdown', () => {
  it('서버의 남은 초로 목표 시각을 잡고 실제 경과 시간만큼 갱신한다', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-20T00:00:00+09:00'))
    render(<CountdownProbe remainingSeconds={750} />)

    expect(screen.getByRole('button', { name: '12분 30초 남음' })).toBeEnabled()

    act(() => vi.advanceTimersByTime(705_000))
    expect(screen.getByRole('button', { name: '45초 남음' })).toBeEnabled()

    act(() => vi.advanceTimersByTime(45_000))
    expect(screen.getByRole('button', { name: '결제 마감' })).toBeDisabled()
  })
})
