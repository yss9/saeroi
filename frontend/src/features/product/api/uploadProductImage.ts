import { apiClient } from '../../../services/apiClient.ts'
import type { ProductImageUpload } from '../types/product.types.ts'

/**
 * 제품 이미지 업로드 — `POST /v1/product-images`
 *
 * **등록과 별개의 요청이다.** 여기서 받은 `storageKey` 를 제품 등록 요청의
 * `thumbnailUrl` 에 넣는다. 이미지가 없으면 등록 자체가 불가능하다(`@NotBlank`).
 *
 * `Content-Type` 을 직접 지정하지 않는다. `apiClient` 의 기본값이 `application/json`
 * 인데, `FormData` 를 보낼 때는 브라우저가 `boundary` 를 포함한 헤더를 만들어야 한다.
 * 직접 `multipart/form-data` 를 적으면 boundary 가 빠져 서버가 파트를 하나도 못 읽는다
 * (`uploadEvidence` 와 같은 이유).
 *
 * 파트 이름은 `file` 이다 — 서버가 `@RequestParam("file")` 로 받으므로 이름이 다르면
 * 파일이 없는 요청으로 보인다. 증빙 업로드는 `files` 로 여러 장을 받지만 제품 대표
 * 이미지는 한 장이다.
 *
 * ⚠️ 등록까지 가지 않으면 이 파일은 아무도 가리키지 않는 채로 남는다. 정리는 서버 배치의
 * 몫이므로 화면이 되돌리려 하지 않는다 — 삭제 API 도 없다.
 */
export async function uploadProductImage(file: File): Promise<ProductImageUpload> {
  const form = new FormData()
  form.append('file', file)

  const { data } = await apiClient.post<ProductImageUpload>('/v1/product-images', form, {
    headers: { 'Content-Type': undefined },
  })
  return data
}
