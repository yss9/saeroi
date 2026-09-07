import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/**
 * 소재 상세.
 *
 * ⚠️ **아직 Mock 이다.** 실제 연결은 S15P21E104-180 소관이다.
 * 이 파일이 이번 브랜치에서 손을 탄 이유는 `MaterialDetail` 타입이 Backend 계약
 * (MR !99)에 맞춰 확장됐기 때문이고, Mock 값도 **서버가 실제로 돌려주는 값**으로
 * 맞춰 두었다. 그렇게 하지 않으면 화면이 Mock 에서만 동작하고 연결 후 깨진다.
 *
 * 서버 실제 동작에 맞춘 부분:
 * - `verificationState` 는 **항상 `'REGISTERED'`** 다. `material` 에 컬럼이 없고
 *   파생 판정 근거(`signed_at`)도 스키마에 없어서다. `'SOURCE_VERIFIED'` 를
 *   Mock 에 넣어 두면 화면이 검증 배지를 그리도록 만들어지고, 연결하면 전부 사라진다
 * - `owners` 는 `[]` 다. `material_balance` Projection 동기화(-376)가 막혀 있다
 * - `evidenceHash`·`manifestUrl`·`tokenId`·`chainId`·`contractAddress` 는 `null` 이다
 *
 * -180 에서 아래 두 줄로 교체한다. 훅·컴포넌트는 그대로 둔다.
 *   const { data } = await apiClient.get<MaterialDetail>(`/materials/${materialId}`)
 *   return data
 */
export async function getMaterial(materialId: number): Promise<MaterialDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 300))
  return {
    id: materialId,
    materialCode: `MAT-${String(materialId).padStart(6, '0')}`,
    materialType: 'DENIM',
    name: '폐데님 원단',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    verificationState: 'REGISTERED',
    status: 'ACTIVE',
    totalQuantity: 1_200_000,
    grade: 'A',
    description: '봉제 공장에서 회수한 데님 자투리 원단입니다.',
    lengthMm: 1500,
    widthMm: 1100,
    registrantId: 1,
    createdAt: '2026-09-01T09:00:00',
    owners: [],
    evidenceHash: null,
    manifestUrl: null,
    tokenId: null,
    chainId: null,
    contractAddress: null,
  }
}
