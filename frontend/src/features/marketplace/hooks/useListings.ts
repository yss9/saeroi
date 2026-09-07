import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getListings, type GetListingsParams } from '../api/getListings.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'
import type { ListingSummary } from '../types/marketplace.types.ts'

/**
 * 판매 목록 (Brand Market).
 *
 * 필터가 Query Key 에 들어가므로 조건이 바뀌면 자동으로 재조회된다.
 * 필터 상태 자체는 화면의 useState 다 — 다른 화면과 공유할 값이 아니다.
 */
export function useListings(params: GetListingsParams = {}) {
  return useQuery<ListingSummary[], AppError>({
    queryKey: marketplaceKeys.list(params),
    queryFn: () => getListings(params),
    // 판매 가능 수량은 다른 사람의 구매로 수시로 바뀐다. 기본 30초는 길다.
    staleTime: 5_000,
  })
}
