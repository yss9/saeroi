import { useMutation } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { uploadProductImage } from '../api/uploadProductImage.ts'
import type { ProductImageUpload } from '../types/product.types.ts'

/**
 * 제품 이미지 업로드.
 *
 * **무효화할 캐시가 없다.** 업로드는 아직 어떤 제품에도 붙지 않은 파일을 만드는 일이고,
 * 그 파일을 가리키는 조회가 없다. 제품 캐시가 바뀌는 시점은 등록이 성공할 때다.
 *
 * 파일을 고르는 즉시 부른다 — 제출 시점에 업로드와 등록을 묶으면 "이미지는 올라갔는데
 * 등록이 실패" 한 상태에서 사용자가 다시 제출할 때 같은 파일을 또 올린다. 실패 지점을
 * 하나씩 분리해 두면 어디서 멈췄는지가 화면에 그대로 드러난다.
 */
export function useUploadProductImage() {
  return useMutation<ProductImageUpload, AppError, File>({
    mutationFn: uploadProductImage,
  })
}
