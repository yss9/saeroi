import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/** TODO(2번/Material 담당): Backend 준비 후 Mock 제거. */
export async function getMaterial(materialId: number): Promise<MaterialDetail> {
  // const { data } = await apiClient.get<MaterialDetail>(`/materials/${materialId}`)
  // return data

  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 300))
  return {
    id: materialId,
    materialCode: `MAT-2026-${String(materialId).padStart(4, '0')}`,
    materialType: 'DENIM',
    name: '폐데님 원단',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    verificationState: 'SOURCE_VERIFIED',
    status: 'ACTIVE',
    totalQuantity: 1200,
    grade: 'A',
    description: '봉제 공장에서 회수한 데님 자투리 원단입니다.',
    lengthMm: 1500,
    widthMm: 1100,
    registrantId: 1,
    createdAt: '2026-09-01T09:00:00',
  }
}
