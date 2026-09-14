import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { notificationApi } from './notificationApi.ts'

afterEach(() => vi.restoreAllMocks())

describe('notificationApi', () => {
  it('알림 페이지를 조회한다', async () => {
    const page = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, unreadCount: 0 }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: page } as AxiosResponse)

    await expect(notificationApi.list(0, 20)).resolves.toEqual(page)
    expect(get).toHaveBeenCalledWith('/v1/notifications', { params: { page: 0, size: 20 } })
  })

  it('개별 알림과 전체 알림을 읽음 처리한다', async () => {
    const patch = vi.spyOn(apiClient, 'patch').mockResolvedValue({ data: {} } as AxiosResponse)

    await notificationApi.read(12)
    await notificationApi.readAll()

    expect(patch).toHaveBeenNthCalledWith(1, '/v1/notifications/12/read')
    expect(patch).toHaveBeenNthCalledWith(2, '/v1/notifications/read-all')
  })
})
