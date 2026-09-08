import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { processorProfileApi } from './processorProfileApi.ts'

afterEach(() => vi.restoreAllMocks())

describe('processor profile API', () => {
  it('내 업체 설정의 세 리소스를 조회한다', async () => {
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: { processorId: 3, region: '서울' } } as AxiosResponse)
      .mockResolvedValueOnce({ data: [] } as AxiosResponse)
      .mockResolvedValueOnce({ data: [] } as AxiosResponse)
    await processorProfileApi.getProfile()
    await processorProfileApi.getCapabilities()
    await processorProfileApi.getLeadTimes()
    expect(get.mock.calls.map(call => call[0])).toEqual([
      '/v1/processors/me/profile', '/v1/processors/me/capabilities', '/v1/processors/me/lead-times',
    ])
  })

  it('세 PUT에 완전 교체 payload를 전달한다', async () => {
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue({ data: [] } as AxiosResponse)
    await processorProfileApi.updateProfile({ region: '부산' })
    await processorProfileApi.updateCapabilities({ capabilities: [{ materialType: 'DENIM', processType: 'CLEANING', minQuantity: 100, maxQuantity: 500 }] })
    await processorProfileApi.updateLeadTimes({ leadTimes: [{ maxQuantity: 500, leadDays: 3 }] })
    expect(put).toHaveBeenNthCalledWith(1, '/v1/processors/me/profile', { region: '부산' })
    expect(put).toHaveBeenNthCalledWith(2, '/v1/processors/me/capabilities', { capabilities: [{ materialType: 'DENIM', processType: 'CLEANING', minQuantity: 100, maxQuantity: 500 }] })
    expect(put).toHaveBeenNthCalledWith(3, '/v1/processors/me/lead-times', { leadTimes: [{ maxQuantity: 500, leadDays: 3 }] })
  })
})
