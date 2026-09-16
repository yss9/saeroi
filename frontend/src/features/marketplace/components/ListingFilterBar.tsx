import { ArrowCounterClockwise } from '@phosphor-icons/react'
import { Select } from '../../../components/common/index.ts'
import type { MaterialGrade, MaterialRootOrigin, MaterialType,
  MaterialGenerationType,
} from '../../material/types/material.types.ts'
import type { ListingFilters } from '../types/marketplace.types.ts'

export interface ListingFilterBarProps {
  filters: ListingFilters
  onFiltersChange: (filters: ListingFilters) => void
  onReset: () => void
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

/**
 * 목록 필터 (정책 FR-011).
 *
 * **정렬은 여기 없다.** 정렬은 거르는 조건이 아니라 결과를 보는 방식이라, 목록 바로 위에
 * 따로 선다 (`ListingSortBar`) — 조건 칸들과 나란히 두면 다섯 번째 조건처럼 읽혔다.
 *
 * 빈 문자열을 `undefined` 로 바꿔서 넘긴다. 그래야 Query Key 에 빈 값이 끼지 않고,
 * "전체" 선택이 필터 없음과 같은 캐시를 쓰게 된다.
 */
export function ListingFilterBar({
  filters,
  onFiltersChange,
  onReset,
}: ListingFilterBarProps) {
  // 되돌릴 것이 없으면 잠근다. 버튼 자체는 늘 같은 자리에 둔다 — 조건을 걸어야 나타나면
  // 그때까지는 되돌리는 방법이 있다는 것을 알 수 없다.
  const hasFilters = Object.keys(filters).length > 0
  function patch<K extends keyof ListingFilters>(key: K, raw: string) {
    const next = { ...filters }
    if (raw === '') delete next[key]
    else next[key] = raw as ListingFilters[K]
    onFiltersChange(next)
  }

  return (
    <div className="listing-filter">
      {/* 고르는 조건 넷. 좁아지면 이 안에서만 줄이 접히고 초기화 버튼은 끝에 남는다. */}
      <div className="listing-filter__fields">
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
      </div>

      {/*
        하나씩 "전체" 로 되돌리려면 네 번을 눌러야 하고, 어떤 조건이 걸려 있었는지
        기억하지 못하면 결과가 왜 적은지도 알 수 없다.
      */}
      <button
        type="button"
        className="listing-filter__reset"
        disabled={!hasFilters}
        onClick={onReset}
      >
        <ArrowCounterClockwise aria-hidden="true" />
        조건 초기화
      </button>
    </div>
  )
}
