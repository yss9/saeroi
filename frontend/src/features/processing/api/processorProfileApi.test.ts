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
    await processorProfileApi.updateLeadTimes({ leadTimes: [{ materialType: 'DENIM', processType: 'CLEANING', maxQuantity: 500, leadDays: 3 }] })
    expect(put).toHaveBeenNthCalledWith(1, '/v1/processors/me/profile', { region: '부산' })
    expect(put).toHaveBeenNthCalledWith(2, '/v1/processors/me/capabilities', { capabilities: [{ materialType: 'DENIM', processType: 'CLEANING', minQuantity: 100, maxQuantity: 500 }] })
    expect(put).toHaveBeenNthCalledWith(3, '/v1/processors/me/lead-times', { leadTimes: [{ materialType: 'DENIM', processType: 'CLEANING', maxQuantity: 500, leadDays: 3 }] })
  })

  it('대표 이미지를 multipart로 업로드하고 blob으로 조회한다', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { imageUrl: '/api/v1/processors/me/profile/image' } } as AxiosResponse)
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: new Blob(['logo'], { type: 'image/png' }) } as AxiosResponse)
    const file = new File(['logo'], 'logo.png', { type: 'image/png' })

    await processorProfileApi.uploadImage(file)
    await processorProfileApi.getImage()

    expect(post).toHaveBeenCalledWith('/v1/processors/me/profile/image', expect.any(FormData), { headers: { 'Content-Type': undefined } })
    expect((post.mock.calls[0][1] as FormData).get('file')).toBe(file)
    expect(get).toHaveBeenCalledWith('/v1/processors/me/profile/image', { responseType: 'blob' })
  })
})
