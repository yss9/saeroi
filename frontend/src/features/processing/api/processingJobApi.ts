import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialType } from '../types/processorProfile.types.ts'
import type { ProcessingJobStatus } from '../types/processing.types.ts'

export type ProcessingSignatureAction = 'RESULT_SUBMIT' | 'APPROVE' | 'REJECT'

export interface ProcessingOutput {
  outputId: number
  materialId: number | null
  materialType: MaterialType
  grade: string
  name: string
  quantity: number
}

export interface ProcessingJobDetail {
  jobId: number
  jobCode: string
  requestId: number
  quoteId: number
  brandId: number
  processorId: number
  fee: number
  paymentAmount: number
  status: ProcessingJobStatus
  paymentExpiresAt: string
  createdAt: string
  inputMaterialId: number
  inputQuantity: number
  lossQuantity: number
  resultRevision: number
  carrier: string | null
  trackingNumber: string | null
  rejectionReason: string | null
  shippedAt: string | null
  receivedAt: string | null
  startedAt: string | null
  resultSubmittedAt: string | null
  rejectedAt: string | null
  completedAt: string | null
  outputs: ProcessingOutput[]
}

export interface ResultDraftInput {
  lossQuantity: number
  outputs: Array<{ materialType: MaterialType; grade: string; name: string; quantity: number }>
}

export interface SignatureChallenge {
  challengeId: number
  action: ProcessingSignatureAction
  walletAddress: string
  nonce: string
  message: string
  expiresAt: string
}

export const processingJobApi = {
  list: async () => (await apiClient.get<ProcessingJobDetail[]>('/v1/processing-jobs')).data,
  detail: async (jobId: number) => (await apiClient.get<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}`)).data,
  ship: async (jobId: number, input: { carrier?: string; trackingNumber?: string }) =>
    (await apiClient.patch<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}/ship`, input)).data,
  receive: async (jobId: number) =>
    (await apiClient.patch<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}/receive`)).data,
  start: async (jobId: number) =>
    (await apiClient.patch<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}/start`)).data,
  saveResult: async (jobId: number, input: ResultDraftInput) =>
    (await apiClient.post<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}/results`, input)).data,
  challenge: async (jobId: number, action: ProcessingSignatureAction, rejectionReason?: string) =>
    (await apiClient.post<SignatureChallenge>(`/v1/processing-jobs/${jobId}/signature-challenge`, { action, rejectionReason })).data,
  submitResult: async (jobId: number, challengeId: number, signature: string) =>
    (await apiClient.post<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}/results/submit`, { challengeId, signature })).data,
  approve: async (jobId: number, challengeId: number, signature: string) =>
    (await apiClient.post<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}/approve`, { challengeId, signature })).data,
  reject: async (jobId: number, challengeId: number, signature: string, reason: string) =>
    (await apiClient.post<ProcessingJobDetail>(`/v1/processing-jobs/${jobId}/reject`, { challengeId, signature, reason })).data,
}
