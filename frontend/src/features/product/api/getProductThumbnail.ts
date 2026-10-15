import { apiClient } from '../../../services/apiClient.ts'

/**
 * 제품 대표 이미지 — `GET /v1/product-models/{id}/thumbnail`
 *
 * **`<img src>` 로는 못 쓴다.** 이 응답은 인증을 요구하는데 브라우저는 `<img>` 요청에
 * Authorization 헤더를 붙이지 않는다. 그래서 여기서 바이트를 받아 화면이 blob URL 로
 * 바꿔 쓴다 (`useProductThumbnail`).
 *
 * 저장 키를 URL 에 넣지 않는 것이 서버 설계다 — 키를 클라이언트가 조립하면 `..` 나 남의
 * 키를 찔러 볼 수 있다. 제품 id 로 물으면 서버가 행에서 키를 꺼내므로 그런 요청 자체가
 * 만들어지지 않는다.
 *
 * 응답이 `Cache-Control: no-store` 라 브라우저가 보관하지 않는다. 같은 이미지를 다시
 * 보여줄 때도 다시 받아야 하므로, 화면 안에서의 재사용은 TanStack Query 캐시가 맡는다.
 *
 * ⚠️ 실패 문구가 서버가 준 문구가 아니다. `responseType: 'blob'` 이면 에러 응답의 본문도
 * Blob 으로 오고 `apiClient` 가 봉투를 읽을 수 없다. 이미지가 없는 경우
 * (`PRODUCT_THUMBNAIL_NOT_FOUND`)는 화면이 자리표시자로 처리하므로 문구가 필요하지 않다.
 */
export async function getProductThumbnail(productModelId: number): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(
    `/v1/product-models/${productModelId}/thumbnail`,
    { responseType: 'blob' },
  )
  return data
}
