import { apiClient } from '../../../services/apiClient.ts'
import type { ProcessingCustodyMaterial } from '../types/inventory.types.ts'

/** 토큰 소유권과 별개로 Processor가 가공을 위해 배송받는 소재를 조회한다. */
export async function getProcessingCustodyMaterials(): Promise<ProcessingCustodyMaterial[]> {
  const { data } = await apiClient.get<ProcessingCustodyMaterial[]>(
    '/processing-jobs/custody-materials',
  )
  return data
}