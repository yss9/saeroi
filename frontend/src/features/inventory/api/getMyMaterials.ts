import { apiClient } from '../../../services/apiClient.ts'
import type { InventoryItem } from '../types/inventory.types.ts'

/**
 * 내가 보유한 소재 목록.
 *
 * **Mock 이 아니다** — Backend 가 MR !106 으로 구현했다.
 *
 * 경로가 `/materials/me` 인데 이 파일이 `features/inventory` 에 있는 것은 의도적이다.
 * 프론트 구조는 "보유 소재" 를 별도 feature 로 잡아 뒀고(`inventory.types.ts` 주석,
 * 라우트 `/inventory`, `InventoryPage`), 폴더가 API 경로를 그대로 따라야 할 이유는 없다.
 *
 * 보유자를 파라미터로 받지 않는다 — 서버가 JWT `sub` 로 판단한다. 쿼리로 받으면
 * 남의 보유 현황을 들여다볼 수 있다.
 *
 * 보유가 없으면 **빈 배열**이다. 오류가 아니다.
 */
export async function getMyMaterials(): Promise<InventoryItem[]> {
  const { data } = await apiClient.get<InventoryItem[]>('/materials/me')
  return data
}
