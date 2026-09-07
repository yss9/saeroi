import { apiClient } from '../../../services/apiClient.ts'
import type { CreateMaterialRequest, CreateMaterialResponse } from '../types/material.types.ts'

/**
 * 소재 등록. `SUPPLIER`·`BRAND` 만 가능하다 (아니면 403 `MATERIAL_ROLE_NOT_ALLOWED`).
 *
 * ⚠️ **경로에 `/v1` 이 없다.** 지갑 API 는 `/api/v1/...` 인데 소재는 `/api/materials` 다.
 * `apiClient.baseURL` 이 `/api` 라 여기서는 `/materials` 로 쓴다.
 * 전체 표면을 `/v1` 으로 통일하는 것은 이메일 로그인 컨트롤러가 들어올 때
 * 프론트·백엔드를 같은 주기에 함께 고친다 — 한쪽만 옮기면 깨진다.
 *
 * 응답은 `{ materialId, materialCode }` **둘뿐**이다. 전체 필드를 주지 않는 것이
 * 서버 쪽 의도이며, 나머지는 상세 조회가 한 곳에서 책임진다. 그래서 등록 직후
 * 상세로 이동하면 그 화면이 스스로 조회한다.
 */
export async function createMaterial(
  payload: CreateMaterialRequest,
): Promise<CreateMaterialResponse> {
  const { data } = await apiClient.post<CreateMaterialResponse>('/materials', payload)
  return data
}
