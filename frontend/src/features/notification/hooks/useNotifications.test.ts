import { describe, expect, it } from 'vitest'
import { notificationQueryOptions } from './useNotifications.ts'

describe('notificationQueryOptions', () => {
  it('활성화되면 최초 조회와 30초 polling 및 탭 재활성화를 사용한다', () => {
    const options = notificationQueryOptions(true)

    expect(options.enabled).toBe(true)
    expect(options.refetchInterval).toBe(30_000)
    expect(options.refetchOnWindowFocus).toBe(true)
    expect(options.queryKey).toEqual(['notifications', 0, 20])
  })

  it('기업 사용자가 아니면 조회를 비활성화한다', () => {
    expect(notificationQueryOptions(false).enabled).toBe(false)
  })
})
