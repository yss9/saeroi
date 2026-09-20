import type { ProcessType, ProcessingJobStatus, ProcessingRequestStatus } from '../types/processing.types.ts'
import type { MaterialType } from '../types/processorProfile.types.ts'
import { apiClient } from '../../../services/apiClient.ts'
export interface OwnedProcessingMaterial {
  materialId: number
  materialCode: string
  name: string
  materialType: MaterialType
  quantity: number
  lockedQuantity: number
  availableQuantity: number
}
/**
 * 검색 결과에 실리는 가공사 한 곳.
 *
 * 견적 단계가 사라지면서 **금액과 기간이 이 목록에 이미 적혀 있다.** 단가와 수량만 받아
 * 화면이 곱하면 올림이 어긋나는 순간 목록 금액과 결제창 금액이 달라지므로, 청구될 값은
 * 서버가 계산한 estimatedPrice·estimatedDays 를 그대로 쓴다.
 */
export interface ProcessorMatch {
  processorId: number
  name: string
  region: string
  /** 요청한 공정 중 가장 느린 것의 하루 처리량(g/일). 둘러볼 때는 등록된 조합 중 가장 큰 값. */
  dailyCapacity: number
  /** 요청한 공정 전부를 합한 kg 당 단가(원). 둘러볼 때는 등록된 조합 중 가장 싼 값. */
  pricePerKg: number
  /** 실제로 청구될 금액. **수량 없이 둘러보면 null** — 0 으로 채우면 "공짜" 가 된다. */
  estimatedPrice: number | null
  /** 총 소요일. 공정이 여럿이면 차례로 하므로 합이다. 수량이 없으면 null. */
  estimatedDays: number | null
  /** 공정별 소요일. 수량이 없으면 빈 목록. */
  estimatedLeadTimes: Array<{ processType: ProcessType; leadDays: number }>
}
export interface ProcessorSearch {
  materialType?: MaterialType
  quantity?: number
  processTypes?: ProcessType[]
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
  materialType: MaterialType
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
export type ProcessingRequestDetail = ProcessingRequestListItem
export interface SubmitProcessingQuoteInput {
  price: number
  leadDays: number
  memo?: string
}
export interface SubmittedProcessingQuote extends SubmitProcessingQuoteInput {
  quoteId: number
  requestId: number
  processorId: number
  processorName: string
  status: 'SUBMITTED' | 'SELECTED' | 'REJECTED' | 'EXPIRED'
  createdAt: string
}
export interface ProcessingJob {
  jobId: number
  jobCode: string
  requestId: number
  quoteId: number
  brandId: number
  processorId: number
  fee: number
  paymentMethod: 'MOCK' | 'KAKAO_PAY'
  paymentReference: string | null
  paymentAmount: number
  status: ProcessingJobStatus
  paymentExpiresAt: string
  createdAt: string
}
export interface ProcessingPaymentReady {
  jobId: number
  tid: string
  redirectUrl: string
  amount: number
  live: boolean
  paymentExpiresAt: string
}
export interface ProcessingRequestListFilter {
  status?: ProcessingRequestStatus
  page: number
  size: number
}
export const processingRequestApi = {
  materials: async () => (await apiClient.get<OwnedProcessingMaterial[]>('/v1/processing-requests/materials')).data,
  search: async (filters: ProcessorSearch, signal?: AbortSignal) => (await apiClient.get<{ items: ProcessorMatch[]; total: number; page: number; size: number }>('/v1/processors/search', { params: { ...filters, ...(filters.processTypes ? { processTypes: filters.processTypes.join(',') } : {}) }, signal })).data,
  create: async (input: ProcessingRequestInput) => (await apiClient.post<CreatedProcessingRequest>('/v1/processing-requests', input)).data,
  cancel: async (id: number) => (await apiClient.post<ProcessingRequestStatus>(`/v1/processing-requests/${id}/cancel`)).data,
  submitQuote: async (requestId: number, input: SubmitProcessingQuoteInput) => (await apiClient.post<SubmittedProcessingQuote>(`/v1/processing-requests/${requestId}/quotes`, input)).data,
  reject: async (requestId: number) => (await apiClient.post<ProcessingRequestStatus>(`/v1/processing-requests/${requestId}/reject`)).data,
  list: async (filters: ProcessingRequestListFilter, signal?: AbortSignal) => (
    await apiClient.get<ProcessingRequestList>('/v1/processing-requests', { params: filters, signal })
  ).data,
  detail: async (requestId: number, signal?: AbortSignal) => (
    await apiClient.get<ProcessingRequestDetail>(`/v1/processing-requests/${requestId}`, { signal })
  ).data,
  quotes: async (requestId: number, signal?: AbortSignal) => (
    await apiClient.get<SubmittedProcessingQuote[]>(`/v1/processing-requests/${requestId}/quotes`, { signal })
  ).data,
  selectQuote: async (quoteId: number) => (
    await apiClient.post<ProcessingJob>(`/v1/quotes/${quoteId}/select`, { paymentMethod: 'MOCK' })
  ).data,
  readyPayment: async (jobId: number) => (
    await apiClient.post<ProcessingPaymentReady>(`/processing-jobs/${jobId}/payment`)
  ).data,
  confirmPayment: async (jobId: number, pgToken: string) => (
    await apiClient.post<ProcessingJob>(`/processing-jobs/${jobId}/payment/confirm`, { pgToken })
  ).data,
}
