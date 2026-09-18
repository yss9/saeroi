import type { MaterialType } from '../../material/types/material.types.ts'
import type { ProcessType } from './processing.types.ts'

export type { MaterialType } from '../../material/types/material.types.ts'
export { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'

export interface ProcessorProfile { processorId: number; region: string }
export interface ProcessorCapability {
  materialType: MaterialType
  processType: ProcessType
  minQuantity: number
  maxQuantity: number
}
export interface ProcessorLeadTime {
  materialType: MaterialType
  processType: ProcessType
  maxQuantity: number
  leadDays: number
}
