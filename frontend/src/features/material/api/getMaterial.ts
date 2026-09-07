import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/**
 * 소재 상세.
 *
 * **Mock 을 걷어냈다** — Backend 가 MR !99 로 구현했다.
 *
 * ⚠️ 응답의 `verificationState` 는 **항상 `'REGISTERED'`** 다. `material` 에 그 컬럼이
 * 없고 파생 판정 근거(`signed_at`)도 스키마에 없어서다. 소재의 사실이 아니라 구현의
 * 한계이므로 **이 값으로 검증 배지를 그리면 안 된다** — 실제로 검증된 소재도
 * "등록만 됨" 으로 보인다.
 *
 * ⚠️ `tokenId`·`chainId`·`contractAddress` 가 `null` 인 것은 정상이다. mint 결과를
 * 기록하는 confirm 엔드포인트가 아직 없다. **`tokenId === null` 이 "등록됐지만
 * 온체인에 없음" 의 유일한 신호다.**
 *
 * `owners` 는 `material_balance` 기준이며 `registrantId`(등록자)와 다르다.
 * Projection 동기화가 아직 없어 빈 배열일 수 있다.
 */
export async function getMaterial(materialId: number): Promise<MaterialDetail> {
  const { data } = await apiClient.get<MaterialDetail>(`/materials/${materialId}`)
  return data
}
