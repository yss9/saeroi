import { env } from '../../../config/env.ts'
import { apiClient } from '../../../services/apiClient.ts'
import type { ProductPassport } from '../types/traceability.types.ts'

/**
 * QR Passport 공개 조회. **인증 헤더가 있어도, 없어도 같은 응답이다** —
 * `SecurityConfig` 가 `GET /api/v1/qr/**` 를 permitAll 로 열어 두었다 (§6-3, 2026-09-08).
 *
 * `qrToken` 은 `product_item.qr_token`(추측 불가 32자 hex)이지 제품 번호가 아니다.
 * 순번을 넣으면 서버가 항상 404 를 준다 — 그 값으로는 어떤 개체도 못 찾는다.
 */
export async function getPassport(qrToken: string): Promise<ProductPassport> {
  const { data } = await apiClient.get<ProductPassport>(`/v1/qr/${qrToken}`)
  return data
}

/**
 * 대표 이미지 주소. 제품 상세와 달리 `fetch` 로 받아 blob URL 로 바꿀 필요가 없다 —
 * 이 엔드포인트는 인증이 없어서(§6-3) `<img src>` 가 헤더 없이 그대로 불러올 수 있다.
 */
export function getPassportThumbnailUrl(qrToken: string): string {
  return `${env.apiBaseUrl}/v1/qr/${qrToken}/thumbnail`
}
