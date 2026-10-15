import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/**
 * 소재 상세.
 *
 * **Mock 을 걷어냈다** — Backend 가 MR !99 로 구현했다.
 *
 * `verificationState` 는 조회 시점에 계산된다 — 지금 판정되는 단계는 `REGISTERED` ·
 * `SOURCE_VERIFIED` · `TRANSACTION_VERIFIED` 셋이다. `verification` 은 `SOURCE_VERIFIED`
 * 일 때만 근거(지갑·Tx 해시·시각)를 담는다 — `MaterialDetail` 타입 주석 참고.
 *
 * ⚠️ `tokenId`·`chainId`·`contractAddress` 가 `null` 인 것은 정상이다. mint 확정 전
 * 소재의 상태다. **`tokenId === null` 이 "등록됐지만 온체인에 없음" 의 유일한 신호다.**
 *
 * `owners` 는 `material_balance` 기준이며 `registrantId`(등록자)와 다르다.
 * Projection 동기화가 아직 없어 빈 배열일 수 있다.
 */
export async function getMaterial(materialId: number): Promise<MaterialDetail> {
  const { data } = await apiClient.get<MaterialDetail>(`/materials/${materialId}`)
  return data
}
