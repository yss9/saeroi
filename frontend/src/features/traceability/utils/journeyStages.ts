import { Package, Scissors, Swatches, TShirt, Truck } from '@phosphor-icons/react'
import type { JourneyStageType } from '../types/traceability.types.ts'

/**
 * 여정 단계 메타데이터. `JourneyExplorer`의 Flow View와 Journey Map(둘 다 SVG)이 같은
 * 라벨·색을 쓰기 위해 여기 하나에 둔다 — 컴포넌트 파일에 두면 react-refresh 가
 * "컴포넌트만 export 해야 한다"고 거부한다.
 *
 * <p><b>수거(COLLECTION)가 원단 등록(SOURCE)보다 먼저다.</b> 폐소재를 실제로 모아 오는
 * 일이 먼저 일어나고, 공급업체는 그렇게 모인 원단을 등록한다 — 시스템 기록 시각과는
 * 별개로 업무상 순서가 그렇다.
 */
export const STAGE_ORDER: JourneyStageType[] = [
  'COLLECTION',
  'SOURCE',
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

/**
 * QR 여권 화면의 모든 아이콘이 쓰는 굵기. 단계 아이콘만이 아니라 상세 패널·지표·제품
 * 배지까지 이 하나를 읽는다 — 같은 화면에서 어떤 아이콘은 선, 어떤 아이콘은 2톤 채움,
 * 어떤 아이콘은 완전 채움으로 그려지던 것을 한 값으로 모은다.
 *
 * <p><b>선({@code regular})이 기준이다.</b> "지금 선택된 단계"의 강조는 획 굵기가 아니라
 * 채워진 파란 원과 흰 아이콘이 맡는다. 획까지 굵히면 강조 수단이 둘로 갈린다.
 */
export const JOURNEY_ICON_WEIGHT = 'regular' as const

/**
 * 단계별 아이콘. 도메인 상태 이름이 아니라 <b>소비자가 보는 장면</b>에 맞춘다 — 문서
 * 아이콘은 원단 등록이 아니라 서류 작업으로, 기어는 가공이 아니라 시스템 설정으로,
 * 상점은 브랜드 제작이 아니라 소매점으로 읽혔다.
 *
 * <p>단계마다 다른 색을 갖고 있었지만 지웠다. 색은 이제 단계가 아니라 <b>상태</b>가
 * 정한다 — 선택된 단계만 {@code --brand-blue}이고 나머지는 중립 한 색이다(passport.css).
 */
export const STAGE_META = {
  SOURCE: { icon: Swatches },
  COLLECTION: { icon: Truck },
  PROCESSING: { icon: Scissors },
  BRAND: { icon: TShirt },
  DELIVERY: { icon: Package },
} satisfies Record<JourneyStageType, { icon: typeof Truck }>

export function stageIndex(stage: JourneyStageType): number {
  return STAGE_ORDER.indexOf(stage)
}
