import { FileText, GearSix, Package, Storefront, Truck } from '@phosphor-icons/react'
import type { JourneyStageType } from '../types/traceability.types.ts'

/**
 * 여정 단계 메타데이터. `JourneyExplorer`의 Flow View와 Journey Map(둘 다 SVG)이 같은
 * 라벨·색을 쓰기 위해 여기 하나에 둔다 — 컴포넌트 파일에 두면 react-refresh 가
 * "컴포넌트만 export 해야 한다"고 거부한다.
 */
export const STAGE_ORDER: JourneyStageType[] = [
  'SOURCE',
  'COLLECTION',
  'PROCESSING',
  'BRAND',
  'DELIVERY',
]

export const STAGE_LABEL: Record<JourneyStageType, string> = {
  SOURCE: '원단 등록',
  COLLECTION: '수거',
  PROCESSING: '가공',
  BRAND: '브랜드 제작',
  DELIVERY: '최종 수령',
}

export const STAGE_META = {
  SOURCE: { icon: FileText, color: '#2f7d59' },
  COLLECTION: { icon: Truck, color: '#216a9f' },
  PROCESSING: { icon: GearSix, color: '#a9855d' },
  BRAND: { icon: Storefront, color: '#f07d45' },
  DELIVERY: { icon: Package, color: '#e95b2b' },
} satisfies Record<JourneyStageType, { icon: typeof FileText; color: string }>

export function stageIndex(stage: JourneyStageType): number {
  return STAGE_ORDER.indexOf(stage)
}
