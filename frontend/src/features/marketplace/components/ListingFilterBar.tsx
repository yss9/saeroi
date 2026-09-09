import { Select } from '../../../components/common/index.ts'
import type { MaterialGrade, MaterialRootOrigin, MaterialType,
  MaterialGenerationType,
} from '../../material/types/material.types.ts'
import {
  LISTING_SORT_LABEL,
  type ListingFilters,
  type ListingSort,
} from '../types/marketplace.types.ts'

export interface ListingFilterBarProps {
  filters: ListingFilters
  sort: ListingSort
  onFiltersChange: (filters: ListingFilters) => void
  onSortChange: (sort: ListingSort) => void
}

const MATERIAL_TYPE_OPTIONS = [
  { value: 'DENIM', label: '데님' },
  { value: 'COTTON', label: '면' },
  { value: 'LEATHER', label: '가죽' },
  { value: 'LINING', label: '안감' },
  { value: 'METAL', label: '금속' },
  { value: 'MIXED', label: '혼방' },
  { value: 'ETC', label: '기타' },
] as const

const ORIGIN_OPTIONS = [
  { value: 'WASTE', label: '폐기물' },
  { value: 'VIRGIN', label: '신소재' },
] as const

/**
 * 생성 유형. **출처와 독립된 축이다** (화면 설계서 B03의 2축 필터).
 *
 * "폐기물 + 최초 등록" 은 원물이고 "폐기물 + 가공 산출물" 은 이미 한 번 손이 간 소재다.
 * 구매자에게는 완전히 다른 물건이라 출처만으로는 고를 수 없다.
 */
const GENERATION_OPTIONS = [
  { value: 'INITIAL', label: '최초 등록' },
  { value: 'PROCESSED', label: '가공 산출물' },
  { value: 'PRODUCTION_SCRAP', label: '생산 부산물' },
] as const

const GRADE_OPTIONS = [
  { value: 'A', label: 'A · 오염·손상 없음' },
  { value: 'B', label: 'B · 경미한 오염·손상' },
  { value: 'C', label: 'C · 파쇄·재생 원료화' },
] as const

const SORT_OPTIONS = (Object.keys(LISTING_SORT_LABEL) as ListingSort[]).map((value) => ({
  value,
  label: LISTING_SORT_LABEL[value],
}))

/**
 * 목록 필터 (정책 FR-011).
 *
 * 빈 문자열을 `undefined` 로 바꿔서 넘긴다. 그래야 Query Key 에 빈 값이 끼지 않고,
 * "전체" 선택이 필터 없음과 같은 캐시를 쓰게 된다.
 */
export function ListingFilterBar({
  filters,
  sort,
  onFiltersChange,
  onSortChange,
}: ListingFilterBarProps) {
  function patch<K extends keyof ListingFilters>(key: K, raw: string) {
    const next = { ...filters }
    if (raw === '') delete next[key]
    else next[key] = raw as ListingFilters[K]
    onFiltersChange(next)
  }

  return (
    <div className="listing-filter">
      <Select
        label="소재 종류"
        placeholder="전체"
        options={MATERIAL_TYPE_OPTIONS}
        value={filters.materialType ?? ''}
        onChange={(event) => patch('materialType', event.target.value as MaterialType)}
      />
      <Select
        label="출처"
        placeholder="전체"
        options={ORIGIN_OPTIONS}
        value={filters.rootOrigin ?? ''}
        onChange={(event) => patch('rootOrigin', event.target.value as MaterialRootOrigin)}
      />
      <Select
        label="생성 유형"
        placeholder="전체"
        options={GENERATION_OPTIONS}
        value={filters.generationType ?? ''}
        onChange={(event) =>
          patch('generationType', event.target.value as MaterialGenerationType)
        }
      />
      <Select
        label="등급"
        placeholder="전체"
        options={GRADE_OPTIONS}
        value={filters.grade ?? ''}
        onChange={(event) => patch('grade', event.target.value as MaterialGrade)}
      />
      <Select
        label="정렬"
        options={SORT_OPTIONS}
        value={sort}
        onChange={(event) => onSortChange(event.target.value as ListingSort)}
      />
    </div>
  )
}
