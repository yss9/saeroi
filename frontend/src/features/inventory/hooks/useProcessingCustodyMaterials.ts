import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getProcessingCustodyMaterials } from '../api/getProcessingCustodyMaterials.ts'
import { inventoryKeys } from '../api/inventoryKeys.ts'
import type { ProcessingCustodyMaterial } from '../types/inventory.types.ts'

export function useProcessingCustodyMaterials(enabled: boolean) {
  return useQuery<ProcessingCustodyMaterial[], AppError>({
    queryKey: inventoryKeys.list({ kind: 'processing-custody' }),
    queryFn: getProcessingCustodyMaterials,
    enabled,
    refetchInterval: enabled ? 30_000 : false,
  })
}