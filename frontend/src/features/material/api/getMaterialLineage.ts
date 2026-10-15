import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialLineage } from '../types/material.types.ts'

/**
 * 소재 혈통. 위로 조상, 아래로 자손을 한 번에 받는다.
 *
 * **Mock 이 아니다** — Backend 가 MR !131 로 구현했다.
 *
 * 방향별로 두 번 부르지 않는다. 서버가 한 호출에 양방향을 주는 이유는 재귀 상한
 * (깊이 5 · 노드 200)을 한 번에 계산해야 하기 때문이다 — 나눠 부르면 두 응답의
 * 상한 판정이 서로 어긋난다.
 */
export async function getMaterialLineage(materialId: number): Promise<MaterialLineage> {
  const { data } = await apiClient.get<MaterialLineage>(`/materials/${materialId}/lineage`)
  return data
}
