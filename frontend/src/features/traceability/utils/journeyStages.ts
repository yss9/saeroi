import { Package, Scissors, Swatches, TShirt, Truck } from '@phosphor-icons/react'
import type { JourneyStageType } from '../types/traceability.types.ts'

/**
 * 여정 단계 메타데이터. `JourneyExplorer`의 Flow View와 Journey Map(둘 다 SVG)이 같은
 * 라벨·색을 쓰기 위해 여기 하나에 둔다 — 컴포넌트 파일에 두면 react-refresh 가
 * "컴포넌트만 export 해야 한다"고 거부한다.
 *
 * <p><b>이 순서는 백엔드가 내려주는 `sequence`와 같다</b> — `ProductJourneyQueryService`의
 * `stages()`가 SOURCE=1, COLLECTION=2, PROCESSING=3, BRAND=4, DELIVERY=5로 만든다.
 * 한동안 프론트만 COLLECTION을 앞에 두고 있었는데, 같은 여정을 두 곳이 다른 차례로
 * 말하면 STEP 번호가 API 응답과 어긋난다.
 */
export const STAGE_ORDER: JourneyStageType[] = [
  'SOURCE',
  'COLLECTION',
  'PROCESSING',
  'BRAND',
  'DELIVERY',
]

/**
 * <b>COLLECTION은 브랜드가 소재를 수령한 운송 구간이다.</b> 완료된 거래가 있으면
 * 백엔드는 브랜드 수령 노드를 함께 내려, 공급업체 → 브랜드 → 가공업체 순서를 보여 준다.
 * 거래가 없는 자가 보유 소재는 실제 수령 기록이 없으므로 노드를 만들지 않는다.
 *
 * <p>'수거'는 폐소재를 현장에서 모아 오는 행위를 가리키는데 여기 담기는 건 이미 등록된
 * 원단이 거래를 거쳐 실려 가는 구간이라, 화면에 보이는 말을 실제 데이터에 맞춘다.
 * 도메인 상태 이름 `COLLECTION`은 API 계약이므로 그대로 둔다.
 */
export const STAGE_LABEL: Record<JourneyStageType, string> = {
  SOURCE: '원단 등록',
  COLLECTION: '브랜드 수령',
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
