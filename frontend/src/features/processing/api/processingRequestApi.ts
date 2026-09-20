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
  /** 혈통을 따라 누적된, 확정된 가공 종류. 가공 전이면 빈 배열이다 */
  completedProcessTypes: ProcessType[]
}
/**
 * 검색에 걸린 가공사 하나.
 *
 * **수량 상한이 없다.** 예전에는 최대 처리량을 넘으면 업체가 목록에서 아예 사라져,
 * 브랜드는 "왜 아무도 안 나오지" 만 보고 이유를 알 수 없었다 — 이제 소요일이 늘어나고
 * 너무 오래 걸리면 그 숫자를 보고 고르지 않는다.
 *
 * ⚠️ **금액을 화면에서 곱하지 않는다.** 단가 × 수량을 화면이 계산하면 올림이 어긋나는
 * 순간 목록 금액과 결제창 금액이 달라진다. 요청 생성이 같은 식으로 `fee` 를 확정하므로
 * `estimatedPrice` 를 그대로 쓰면 언제나 일치한다.
 */
export interface ProcessorMatch {
  processorId: number
  name: string
  region: string
  /** 고른 공정 중 가장 느린 공정의 하루 처리량 (gram/일) */
  dailyCapacity: number
  /** 고른 공정 전부를 합한 kg 당 단가 (원) */
  pricePerKg: number
  /** 실제 청구될 금액 (원). **수량을 주지 않고 둘러볼 때는 null** 이다 */
  estimatedPrice: number | null
  /** 총 소요일. 공정이 여럿이면 합. 수량을 주지 않으면 null */
  estimatedDays: number | null
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
/**
 * 요청 생성 결과.
 *
 * **요청이 곧 작업을 만든다.** 견적 선택이 작업을 만드는 유일한 입구였는데 그 단계가
 * 없어졌다 — 여기서 받은 `jobId`·`fee` 로 곧장 결제로 넘어간다.
 *
 * `expiresAt` 은 결제 전에는 결제 기한(생성 +15분)이고, 결제한 뒤에는 가공사가 답해야
 * 하는 기한(승인 +3일)으로 밀린다.
 */
export interface CreatedProcessingRequest extends ProcessingRequestInput {
  requestId: number
  requestCode: string
  status: ProcessingRequestStatus
  createdAt: string
  expiresAt: string
  jobId: number
  /** 서버가 확정한 가공비 (원). 공정마다 ⌈단가 × 수량(g) ÷ 1000⌉ 을 더한 값이다 */
  fee: number
  paymentMethod: string
  paymentExpiresAt: string
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
  reject: async (requestId: number) => (await apiClient.post<ProcessingRequestStatus>(`/v1/processing-requests/${requestId}/reject`)).data,
  list: async (filters: ProcessingRequestListFilter, signal?: AbortSignal) => (
    await apiClient.get<ProcessingRequestList>('/v1/processing-requests', { params: filters, signal })
  ).data,
  detail: async (requestId: number, signal?: AbortSignal) => (
    await apiClient.get<ProcessingRequestDetail>(`/v1/processing-requests/${requestId}`, { signal })
  ).data,
  readyPayment: async (jobId: number) => (
    await apiClient.post<ProcessingPaymentReady>(`/processing-jobs/${jobId}/payment`)
  ).data,
  confirmPayment: async (jobId: number, pgToken: string) => (
    await apiClient.post<ProcessingJob>(`/processing-jobs/${jobId}/payment/confirm`, { pgToken })
  ).data,
}
