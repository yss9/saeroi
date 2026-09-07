// QuantityInput 이 쓰는 단위 정의.
//
// 컴포넌트 파일과 분리한 이유: 한 파일이 컴포넌트와 상수를 함께 export 하면
// Vite 의 Fast Refresh 가 그 모듈을 갱신할 때 상태를 잃는다 (react-refresh 규칙).
//
// ⚠️ TODO(2번/Material 담당 + Backend 협의): backend `material` 테이블에는 아직
// unit 컬럼이 없다 (total_quantity BIGINT + length_mm/width_mm 만 있음).
// 단위를 어디에 둘지 확정되면 이 파일을 features/material/types 로 옮기고
// Backend Enum 이름과 일치시킨다. 지금은 UI 계약만 잡아둔 상태다.

export type QuantityUnit = 'KG' | 'METER'

export const QUANTITY_UNIT_LABEL: Record<QuantityUnit, string> = {
  KG: 'kg',
  METER: 'm',
}
