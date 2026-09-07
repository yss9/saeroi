import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMaterials } from '../api/getMaterials.ts'
import { materialKeys } from '../api/materialKeys.ts'
import type { MaterialListFilters, MaterialSummary } from '../types/material.types.ts'

/**
 * 소재 목록 조회.
 *
 * 이 훅이 서버 상태를 다루는 **유일한** 방법이다. 결과를 Zustand 로 옮겨 담지 않는다 —
 * 그러면 캐시가 둘이 되어 무효화 타이밍이 어긋나고, 어느 쪽이 최신인지 알 수 없게 된다.
 */
export function useMaterials(filters: MaterialListFilters = {}) {
  return useQuery<MaterialSummary[], AppError>({
    // filters 가 키에 포함되므로 필터가 바뀌면 자동으로 다른 캐시 항목이 된다.
    queryKey: materialKeys.list(filters),
    queryFn: () => getMaterials(filters),
  })
}
