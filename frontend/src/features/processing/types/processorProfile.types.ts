import type { ProcessType } from './processing.types.ts'

export type MaterialType = 'DENIM' | 'COTTON' | 'LEATHER' | 'LINING' | 'METAL' | 'MIXED' | 'ETC'

export interface ProcessorProfile { processorId: number; region: string }
export interface ProcessorCapability {
  materialType: MaterialType
  processType: ProcessType
  minQuantity: number
  maxQuantity: number
}
export interface ProcessorLeadTime { maxQuantity: number; leadDays: number }

export const MATERIAL_TYPE_LABEL: Record<MaterialType, string> = {
  DENIM: '데님', COTTON: '면', LEATHER: '가죽', LINING: '안감', METAL: '금속', MIXED: '혼합', ETC: '기타',
}
