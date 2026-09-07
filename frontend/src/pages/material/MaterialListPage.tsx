import { useState } from 'react'
import { MaterialList } from '../../features/material/components/MaterialList.tsx'
import { Select } from '../../components/common/index.ts'
import type { MaterialRootOrigin } from '../../features/material/types/material.types.ts'

// RECOVERED 는 뺐다. 정책 v3에서 회수 소재 재순환이 MVP 범위에서 제외됐고
// Backend material.root_origin ENUM 도 VIRGIN/WASTE 두 값뿐이다.
// 필터로 남겨 두면 서버가 거절하는 값을 사용자가 고를 수 있다.
const ORIGIN_OPTIONS = [
  { value: 'WASTE', label: '폐기물' },
  { value: 'VIRGIN', label: '신소재' },
] as const

/**
 * Page 의 책임은 조합과 배치뿐이다.
 *
 * 이 파일에 없는 것들에 주목할 것 — axios 호출도, useQuery 도, 응답 가공도 없다.
 * 전부 MaterialList → useMaterials → getMaterials → apiClient 로 내려가 있다.
 *
 * 여기 있는 useState 는 "이 화면에서만 쓰는 필터 선택값" 이라 Zustand 가 아니라
 * 지역 상태가 맞다 (§3 Local State).
 */
export function MaterialListPage() {
  const [rootOrigin, setRootOrigin] = useState<MaterialRootOrigin | ''>('')

  return (
    <main className="page">
      <h1>폐소재 목록</h1>

      <Select
        label="출처 필터"
        placeholder="전체"
        options={ORIGIN_OPTIONS}
        value={rootOrigin}
        onChange={(event) => setRootOrigin(event.target.value as MaterialRootOrigin | '')}
      />

      <MaterialList filters={rootOrigin ? { rootOrigin } : {}} />
    </main>
  )
}
