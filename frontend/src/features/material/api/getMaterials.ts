import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialListFilters, MaterialSummary } from '../types/material.types.ts'

/**
 * 소재 목록 조회.
 *
 * ⚠️ Backend 의 Material API 가 아직 없어 현재는 Mock 배열을 돌려준다.
 * TODO(2번/Material 담당): 아래 MOCK 블록을 지우고 주석 처리된 실제 호출을 살린다.
 * 훅·컴포넌트·Query Key 는 전혀 바꿀 필요가 없다 — 이 파일 하나만 고치면 된다.
 * 그것이 API 를 이 계층으로 격리한 이유다.
 */
export async function getMaterials(filters: MaterialListFilters = {}): Promise<MaterialSummary[]> {
  // ── 실제 구현 (Backend 준비되면 이 두 줄로 교체) ──
  // const { data } = await apiClient.get<MaterialSummary[]>('/materials', { params: filters })
  // return data

  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 300)) // 로딩 UI 를 확인할 수 있게 지연
  return MOCK_MATERIALS.filter((material) =>
    filters.rootOrigin ? material.rootOrigin === filters.rootOrigin : true,
  )
}

const MOCK_MATERIALS: MaterialSummary[] = [
  {
    id: 1,
    materialCode: 'MAT-2026-0001',
    materialType: 'DENIM',
    name: '폐데님 원단',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    verificationState: 'SOURCE_VERIFIED',
    status: 'ACTIVE',
    totalQuantity: 1200,
    grade: 'A',
  },
  {
    id: 2,
    materialCode: 'MAT-2026-0002',
    materialType: 'COTTON',
    name: '재고 면 원단',
    rootOrigin: 'RECOVERED',
    generationType: 'INITIAL',
    verificationState: 'REGISTERED',
    status: 'ACTIVE',
    totalQuantity: 480,
    grade: 'B',
  },
  {
    id: 3,
    materialCode: 'MAT-2026-0003',
    materialType: 'POLYESTER',
    name: '분쇄 폴리에스터 칩',
    rootOrigin: 'WASTE',
    generationType: 'PROCESSED',
    verificationState: 'PROCESSING_VERIFIED',
    status: 'ACTIVE',
    totalQuantity: 2600,
    grade: 'A',
  },
]
