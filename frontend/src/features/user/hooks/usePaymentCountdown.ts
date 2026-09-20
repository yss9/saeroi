import { useEffect, useState } from 'react'

export interface PaymentCountdown {
  remainingSeconds: number | null
  label: string | null
  expired: boolean
}

/**
 * 서버가 계산한 남은 초를 브라우저에서 이어 세는 결제 마감 시계.
 *
 * 서버 응답을 받은 시점의 `Date.now()` 에 남은 초를 더해 목표 시각을 한 번 정한다. 이후에는
 * 그 목표와 현재 시각의 차이를 다시 계산하므로, 백그라운드 탭에서 interval 실행이 늦어져도
 * 시간이 밀리지 않는다. `paymentDeadline` 은 새 응답을 구분하는 키로만 쓰고 시각 계산에는
 * 사용하지 않는다.
 */
export function usePaymentCountdown(
  remainingSeconds: number | null | undefined,
  paymentDeadline?: string | null,
): PaymentCountdown {
  const normalized = normalizeRemainingSeconds(remainingSeconds)
  const deadlineKey = paymentDeadline ?? null
  const [clock, setClock] = useState(() => ({
    sourceSeconds: normalized,
    deadlineKey,
    seconds: normalized,
  }))

  useEffect(() => {
    const receivedAt = Date.now()
    const targetAt = normalized === null ? null : receivedAt + normalized * 1000
    let interval: number | undefined
    const update = () => {
      const seconds = targetAt === null ? null : Math.max(0, Math.ceil((targetAt - Date.now()) / 1000))
      setClock({ sourceSeconds: normalized, deadlineKey, seconds })
      if (seconds === 0 && interval !== undefined) window.clearInterval(interval)
    }
    const immediate = window.setTimeout(update, 0)
    if (targetAt !== null && normalized !== 0) interval = window.setInterval(update, 1000)
    return () => {
      window.clearTimeout(immediate)
      if (interval !== undefined) window.clearInterval(interval)
    }
  }, [deadlineKey, normalized])

  // 새 응답이 렌더된 직후 effect가 실행되기 전에는 이전 시계 대신 새 서버 값을 바로 쓴다.
  const seconds = clock.sourceSeconds === normalized && clock.deadlineKey === deadlineKey
    ? clock.seconds
    : normalized
  if (seconds === null) {
    return { remainingSeconds: null, label: null, expired: false }
  }

  return {
    remainingSeconds: seconds,
    label: formatPaymentCountdown(seconds),
    expired: seconds === 0,
  }
}

export function formatPaymentCountdown(seconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(seconds))
  if (safeSeconds === 0) return '결제 마감'
  if (safeSeconds < 60) return `${safeSeconds}초 남음`
  return `${Math.floor(safeSeconds / 60)}분 ${safeSeconds % 60}초 남음`
}

function normalizeRemainingSeconds(value: number | null | undefined): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null
  return Math.max(0, Math.floor(value))
}
