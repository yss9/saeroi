import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMaterialLineage } from '../api/getMaterialLineage.ts'
import { materialKeys } from '../api/materialKeys.ts'
import type { MaterialLineage } from '../types/material.types.ts'

/**
 * 소재 혈통.
 *
 * 키를 `detail(id)` 아래에 두어 거래·가공으로 소재가 바뀔 때 상세 무효화 한 번으로
 * 함께 갱신되게 한다 (`materialKeys.lineage` 참고 — balance 와 같은 이유다).
 */
export function useMaterialLineage(materialId: number | null) {
  return useQuery<MaterialLineage, AppError>({
    queryKey: materialKeys.lineage(materialId ?? 0),
    queryFn: () => getMaterialLineage(materialId as number),
    enabled: materialId !== null,
  })
}
