import { EmptyState, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useMaterials } from '../hooks/useMaterials.ts'
import type { MaterialListFilters } from '../types/material.types.ts'
import { MaterialCard } from './MaterialCard.tsx'

export interface MaterialListProps {
  filters?: MaterialListFilters
}

/**
 * 목록 조회 + 3가지 상태 처리의 표준형.
 *
 * Page 가 아니라 Feature Component 가 데이터를 가져온다. Page 는 이걸 배치만 한다.
 * 로딩 / 에러 / 빈 결과를 각각 구분해 다루는 것이 여기서 보여주려는 요점이다.
 */
export function MaterialList({ filters }: MaterialListProps) {
  const { data, isPending, isError, error, refetch } = useMaterials(filters)

  if (isPending) return <Loading variant="skeleton" rows={4} label="소재를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.length === 0) {
    return (
      <EmptyState
        title="등록된 소재가 없습니다"
        description="폐소재를 등록하면 Material Token이 발행되고 이력 추적이 시작됩니다."
      />
    )
  }

  return (
    <ul className="material-list">
      {data.map((material) => (
        <li key={material.id}>
          <MaterialCard material={material} />
        </li>
      ))}
    </ul>
  )
}
