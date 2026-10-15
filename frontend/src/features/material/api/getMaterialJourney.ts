import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialJourney } from '../types/material.types.ts'

/**
 * 소재 Journey Timeline. 혈통(`/lineage`, 공간)과 달리 이 소재에 <b>시간순으로</b>
 * 무슨 일이 있었는지를 준다 (S15P21E104-73).
 */
export async function getMaterialJourney(materialId: number): Promise<MaterialJourney> {
  const { data } = await apiClient.get<MaterialJourney>(`/materials/${materialId}/journey`)
  return data
}
