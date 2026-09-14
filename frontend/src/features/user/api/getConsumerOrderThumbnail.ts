import { apiClient } from '../../../services/apiClient.ts'

/** Backend가 주문상품에 내려준 인증 필요 이미지 경로를 Blob으로 읽는다. */
export async function getConsumerOrderThumbnail(imageUrl: string): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(toApiClientPath(imageUrl), {
    responseType: 'blob',
  })
  return data
}

/** `/api`가 이미 붙은 서버 경로를 apiClient의 base URL 기준으로 정규화한다. */
function toApiClientPath(imageUrl: string): string {
  return imageUrl.startsWith('/api/') ? imageUrl.slice('/api'.length) : imageUrl
}
