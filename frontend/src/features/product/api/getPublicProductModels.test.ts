import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import type { PageResponse } from '../../../types/api.types.ts'
import type { PublicProductModel } from '../types/publicProduct.types.ts'
import { getPublicProductModels } from './getPublicProductModels.ts'

const response: PageResponse<PublicProductModel> = {
  content: [
    {
      productModelId: 17,
      name: '데님 서클 백',
      category: 'BAG',
      brandId: 3,
      brandName: 'ReWeave',
      price: 129_000,
      description: '폐데님으로 만든 원형 가방',
      thumbnailUrl: '/images/landing/product-denim-circle.webp',
      status: 'ON_SALE',
      stockQuantity: 10,
      createdAt: '2026-09-16T10:00:00',
    },
  ],
  page: 0,
  size: 4,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
}

describe('getPublicProductModels', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('loads the public catalog without authentication or token refresh', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValue({ data: response } as AxiosResponse<PageResponse<PublicProductModel>>)

    await expect(getPublicProductModels({ page: 0, size: 4 })).resolves.toEqual(response)

    expect(get).toHaveBeenCalledWith('/v1/public/product-models', {
      params: { page: 0, size: 4 },
      skipAuth: true,
      skipTokenRefresh: true,
    })
  })
})
