import { apiClient } from '../../../services/apiClient.ts'
import type { CreateMaterialRequest, MaterialDetail } from '../types/material.types.ts'

/** TODO(2번/Material 담당): Backend 준비 후 Mock 제거. */
export async function createMaterial(payload: CreateMaterialRequest): Promise<MaterialDetail> {
  // const { data } = await apiClient.post<MaterialDetail>('/materials', payload)
  // return data

  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 300))
  return {
    ...payload,
    id: Date.now(),
    verificationState: 'REGISTERED',
    status: 'ACTIVE',
    description: payload.description ?? null,
    grade: payload.grade ?? null,
    lengthMm: null,
    widthMm: null,
    registrantId: 1,
    createdAt: new Date().toISOString(),
  }
}
