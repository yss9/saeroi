import { useQuery } from '@tanstack/react-query'
import type { PageResponse } from '../../../types/api.types.ts'
import type { AppError } from '../../../utils/errors.ts'
import { getBrandOrders } from '../api/brandOrderApi.ts'
import { brandOrderKeys } from '../api/brandOrderKeys.ts'
import type { BrandOrder, BrandOrderFilters } from '../types/brandOrder.types.ts'

/** 내게 들어온 주문 목록. 배송지가 함께 온다. */
export function useBrandOrders(filters: BrandOrderFilters = {}) {
  return useQuery<PageResponse<BrandOrder>, AppError>({
    queryKey: brandOrderKeys.list(filters),
    queryFn: () => getBrandOrders(filters),
  })
}

/**
 * 발송 대기 건수.
 *
 * **집계 API 가 따로 없다.** 같은 목록을 `size: 1` 로 불러 `totalElements` 만 읽는다 —
 * 서버가 그렇게 쓰라고 안내한 방법이고, 한 건만 받아 오므로 목록 전체를 세는 것보다 가볍다.
 */
export function usePendingShipmentCount() {
  const query = useQuery<PageResponse<BrandOrder>, AppError>({
    queryKey: brandOrderKeys.list({ status: 'PAID', size: 1 }),
    queryFn: () => getBrandOrders({ status: 'PAID', size: 1 }),
  })

  return query.data?.totalElements ?? 0
}
