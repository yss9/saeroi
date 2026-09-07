import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialBalance } from '../types/material.types.ts'

/**
 * 소재의 보유자별 잔량.
 *
 * **Mock 이 아니다** — Backend 가 MR !106 으로 구현했다.
 * `apiClient.baseURL` 이 `/api` 라 여기서는 `/materials/...` 로 쓴다.
 * 경로에 `/v1` 이 없는 이유는 `createMaterial.ts` 주석 참고.
 *
 * 보유자가 없으면 **빈 배열**이 온다 (404 가 아니다). 소재는 있는데 아직 아무도
 * 들고 있지 않은 상태와 소재가 없는 것은 다르고, 서버가 그 둘을 구분해 준다.
 * 없는 소재는 404 `MATERIAL_NOT_FOUND` 다.
 */
export async function getMaterialBalances(materialId: number): Promise<MaterialBalance[]> {
  const { data } = await apiClient.get<MaterialBalance[]>(`/materials/${materialId}/balance`)
  return data
}
