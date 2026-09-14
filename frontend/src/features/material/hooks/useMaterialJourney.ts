import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMaterialJourney } from '../api/getMaterialJourney.ts'
import { materialKeys } from '../api/materialKeys.ts'
import type { MaterialJourney } from '../types/material.types.ts'

/** 소재 Journey Timeline. `useMaterialLineage`와 같은 무효화 규칙을 따른다. */
export function useMaterialJourney(materialId: number | null) {
  return useQuery<MaterialJourney, AppError>({
    queryKey: materialKeys.journey(materialId ?? 0),
    queryFn: () => getMaterialJourney(materialId as number),
    enabled: materialId !== null,
  })
}
