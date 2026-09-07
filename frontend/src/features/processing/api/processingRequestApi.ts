import type { ProcessType, ProcessingRequestStatus } from '../types/processing.types.ts'
import { apiClient } from '../../../services/apiClient.ts'
export interface OwnedProcessingMaterial {
  materialId: number
  materialCode: string
  name: string
  materialType: string
  quantity: number
  lockedQuantity: number
  availableQuantity: number
}
export interface ProcessorMatch {
  processorId: number
  name: string
  region: string
  minQuantity: number
  maxQuantity: number
  estimatedLeadDays: number | null
}
export interface ProcessorSearch {
  materialType: string
  quantity: number
  processTypes: ProcessType[]
  page: number
  size: number
}
export interface ProcessingRequestInput {
  materialId: number
  processorId: number
  quantity: number
  processTypes: ProcessType[]
}
export interface CreatedProcessingRequest extends ProcessingRequestInput {
  requestId: number
  requestCode: string
  status: ProcessingRequestStatus
  createdAt: string
  expiresAt: string
}
export interface ProcessingRequestListItem {
  requestId: number
  requestCode: string
  brandId: number
  brandName: string
  processorId: number
  processorName: string
  materialId: number
  materialCode: string
  materialName: string
  materialType: string
  quantity: number
  processTypes: ProcessType[]
  status: ProcessingRequestStatus
  createdAt: string
  expiresAt: string
}
export interface ProcessingRequestList {
  items: ProcessingRequestListItem[]
  total: number
  page: number
  size: number
}
export interface ProcessingRequestListFilter {
  status?: ProcessingRequestStatus
  page: number
  size: number
}
export const processingRequestApi = {
  materials: async () => (await apiClient.get<OwnedProcessingMaterial[]>('/v1/processing-requests/materials')).data,
  search: async (filters: ProcessorSearch, signal?: AbortSignal) => (await apiClient.get<{ items: ProcessorMatch[]; total: number; page: number; size: number }>('/v1/processors/search', { params: { ...filters, processTypes: filters.processTypes.join(',') }, signal })).data,
  create: async (input: ProcessingRequestInput) => (await apiClient.post<CreatedProcessingRequest>('/v1/processing-requests', input)).data,
  cancel: async (id: number) => (await apiClient.post<ProcessingRequestStatus>(`/v1/processing-requests/${id}/cancel`)).data,
  list: async (filters: ProcessingRequestListFilter, signal?: AbortSignal) => (
    await apiClient.get<ProcessingRequestList>('/v1/processing-requests', { params: filters, signal })
  ).data,
}
