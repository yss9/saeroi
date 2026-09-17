import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../services/apiClient.ts'
import type { PageResponse } from '../../types/api.types.ts'
import { isAppError, type AppError } from '../../utils/errors.ts'

export interface PublicBrand {
  brandId: number
  brandName: string
  introduction: string | null
  logoUrl: string | null
  productCount: number
}
export type BrandSort = 'PRODUCT_COUNT' | 'NAME'
export interface BrandFilters { keyword?: string; sort: BrandSort; page: number; size: number }
export const brandIntroduction = {
  card: '브랜드 소개 준비 중',
  detail: '아직 등록된 브랜드 소개가 없습니다. 아래에서 이 브랜드의 상품을 만나보세요.',
}

export function usePublicBrands(filters: BrandFilters) {
  return useQuery<PageResponse<PublicBrand>, AppError>({
    queryKey: ['public-brands', 'list', filters],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<PublicBrand>>('/v1/public/brands', {
        params: filters, skipAuth: true, skipTokenRefresh: true,
      })
      return data
    },
  })
}

export function usePublicBrand(id: number | null) {
  return useQuery<PublicBrand, AppError>({
    queryKey: ['public-brands', 'detail', id],
    enabled: id !== null,
    queryFn: async () => {
      const { data } = await apiClient.get<PublicBrand>(`/v1/public/brands/${id}`, { skipAuth: true, skipTokenRefresh: true })
      return data
    },
    retry: (count, error) => !(isAppError(error) && error.status === 404) && count < 1,
  })
}
