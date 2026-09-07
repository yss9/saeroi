import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMaterialBalances } from '../api/getMaterialBalances.ts'
import { materialKeys } from '../api/materialKeys.ts'
import type { MaterialBalance } from '../types/material.types.ts'

/**
 * 소재의 보유자별 잔량.
 *
 * `useMaterial` 과 같은 형태다 — `materialId` 가 `null` 인 동안(라우트 파라미터
 * 파싱 실패 등)에는 `enabled` 로 요청 자체를 막는다. 키에 `null` 을 넣지 않도록
 * `0` 으로 대체하지만, 그 키로 실제 요청이 나가지는 않는다.
 *
 * 결과를 Zustand 로 옮겨 담지 않는다. 캐시가 둘이 되면 무효화 타이밍이 어긋나고
 * 어느 쪽이 최신인지 알 수 없게 된다.
 */
export function useMaterialBalances(materialId: number | null) {
  return useQuery<MaterialBalance[], AppError>({
    queryKey: materialKeys.balance(materialId ?? 0),
    queryFn: () => getMaterialBalances(materialId as number),
    enabled: materialId !== null,
  })
}
