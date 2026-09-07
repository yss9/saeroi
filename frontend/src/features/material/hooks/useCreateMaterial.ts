import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { createMaterial } from '../api/createMaterial.ts'
import { materialKeys } from '../api/materialKeys.ts'
import type { CreateMaterialRequest, CreateMaterialResponse } from '../types/material.types.ts'

/**
 * 소재 등록.
 *
 * 성공 후 목록 캐시를 무효화하는 것이 핵심이다. 이걸 빠뜨리면 등록에 성공했는데
 * 목록으로 돌아갔을 때 방금 만든 소재가 보이지 않는다.
 */
export function useCreateMaterial() {
  const queryClient = useQueryClient()

  return useMutation<CreateMaterialResponse, AppError, CreateMaterialRequest>({
    mutationFn: createMaterial,
    onSuccess: (created) => {
      // lists() 로 무효화하면 필터가 다른 모든 목록 캐시가 함께 정리된다.
      void queryClient.invalidateQueries({ queryKey: materialKeys.lists() })
      // 상세 캐시는 미리 채우지 않는다. 등록 응답은 { materialId, materialCode } 둘뿐이라
      // MaterialDetail 로 쓸 수 없고, 부분 객체를 넣으면 상세 화면이 owners·status 를
      // undefined 로 읽는다. 상세로 이동하면 그 화면이 스스로 조회하게 둔다.
      void queryClient.invalidateQueries({ queryKey: materialKeys.detail(created.materialId) })
    },
  })
}
