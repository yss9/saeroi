import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMyMaterials } from '../api/getMyMaterials.ts'
import { inventoryKeys } from '../api/inventoryKeys.ts'
import type { InventoryItem } from '../types/inventory.types.ts'

/**
 * 내 보유 소재 목록.
 *
 * `lists()` 키를 쓴다. 소재 등록·거래 후
 * `invalidateQueries({ queryKey: inventoryKeys.lists() })` 로 갱신하면 된다.
 *
 * 사용자 id 를 키에 넣지 않는다 — 서버가 토큰으로 판단하므로 요청에 사용자가
 * 드러나지 않는다. 로그아웃 시 캐시 전체를 비우는 것이 authStore 책임이다.
 */
export function useMyMaterials() {
  return useQuery<InventoryItem[], AppError>({
    queryKey: inventoryKeys.lists(),
    queryFn: getMyMaterials,
  })
}
