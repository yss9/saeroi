import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMaterial } from '../api/getMaterial.ts'
import { materialKeys } from '../api/materialKeys.ts'
import type { MaterialDetail } from '../types/material.types.ts'

export function useMaterial(materialId: number | null) {
  return useQuery<MaterialDetail, AppError>({
    // materialId 가 null 인 동안(라우트 파라미터 파싱 실패 등)에는 요청하지 않는다.
    // queryKey 에 null 을 넣지 않도록 0 으로 대체하되 enabled 로 실행 자체를 막는다.
    queryKey: materialKeys.detail(materialId ?? 0),
    queryFn: () => getMaterial(materialId as number),
    enabled: materialId !== null,
  })
}
