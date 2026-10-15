import type { MaterialType } from '../../material/types/material.types.ts'
import type { ProcessType } from './processing.types.ts'

export type { MaterialType } from '../../material/types/material.types.ts'
export { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'

export interface ProcessorProfile { processorId: number; region: string }
/**
 * 가공사가 등록하는 취급 조건 한 줄.
 *
 * **적는 것은 둘뿐이다** — 하루에 얼마나 처리하는가, kg 당 얼마인가. 소요일은 서버가
 * `⌈수량 ÷ dailyCapacity⌉` 로 계산하므로 따로 받지 않는다(수량 구간별 소요일 표는 없앴다).
 */
export interface ProcessorCapability {
  materialType: MaterialType
  processType: ProcessType
  /** 하루 처리량 (gram/일). 소요일의 분모라 0 일 수 없다 */
  dailyCapacity: number
  /** kg 당 단가 (원). 0 도 받는다 — 무상 가공이 있다 */
  pricePerKg: number
}
