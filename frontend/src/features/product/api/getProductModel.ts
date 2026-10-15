import { apiClient } from '../../../services/apiClient.ts'
import type { ProductModel } from '../types/product.types.ts'

/**
 * 제품 상세.
 *
 * ⚠️ **이 엔드포인트는 아직 Backend 에 없다.** 지금 있는 제품 API 는 등록·상태 변경·썸네일
 * 셋뿐이고 조회 경로가 비어 있다. 응답 모양은 추측이 아니다 — 등록이 돌려주는
 * `ProductModelResponse` 를 그대로 쓰면 되므로 필드는 확정돼 있고, 경로만 같은 프리픽스의
 * REST 관례(`GET /v1/product-models/{id}`)를 가정했다.
 *
 * 같은 저장소에 선례가 있다 — `authApi.ts` 도 컨트롤러가 나오기 전에 가정 경로로 먼저
 * 짜여 있었다. 엔드포인트가 열리면 이 파일의 경로 한 줄만 확인하면 된다.
 *
 * **인증이 필요하다.** `SecurityConfig` 가 `/api/v1/auth/**` 와 `GET /api/health` 만 열어
 * 두었고 나머지는 전부 인증이다. 그래서 제품 상세 라우트도 로그인 뒤로 두었다.
 */
export async function getProductModel(productModelId: number): Promise<ProductModel> {
  const { data } = await apiClient.get<ProductModel>(`/v1/product-models/${productModelId}`)
  return data
}
