import { apiClient } from '../../../services/apiClient.ts'
import type { PublicProductDetail } from '../types/publicProduct.types.ts'

export async function getPublicProductModel(id: number): Promise<PublicProductDetail> {
  const { data } = await apiClient.get<PublicProductDetail>(`/v1/public/product-models/${id}`, {
    skipAuth: true, skipTokenRefresh: true,
  })
  return data
}
