import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { createMaterial } from '../api/createMaterial.ts'
import { materialKeys } from '../api/materialKeys.ts'
import type { CreateMaterialRequest, MaterialDetail } from '../types/material.types.ts'

/**
 * 소재 등록.
 *
 * 성공 후 목록 캐시를 무효화하는 것이 핵심이다. 이걸 빠뜨리면 등록에 성공했는데
 * 목록으로 돌아갔을 때 방금 만든 소재가 보이지 않는다.
 */
export function useCreateMaterial() {
  const queryClient = useQueryClient()

  return useMutation<MaterialDetail, AppError, CreateMaterialRequest>({
    mutationFn: createMaterial,
    onSuccess: (created) => {
      // lists() 로 무효화하면 필터가 다른 모든 목록 캐시가 함께 정리된다.
      void queryClient.invalidateQueries({ queryKey: materialKeys.lists() })
      // 상세는 방금 받은 응답으로 채워 넣는다 — 등록 직후 상세로 이동해도 재요청이 없다.
      queryClient.setQueryData(materialKeys.detail(created.id), created)
    },
  })
}
