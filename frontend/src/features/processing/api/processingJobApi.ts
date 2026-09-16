import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialType } from '../types/processorProfile.types.ts'
import type { ProcessType, ProcessingJobStatus } from '../types/processing.types.ts'
import type { MaterialGrade } from '../../material/types/material.types.ts'

export type ProcessingSignatureAction = 'RESULT_SUBMIT' | 'APPROVE' | 'REJECT'
export interface ProcessingOutput { outputId:number; materialId:number|null; materialType:MaterialType; grade:MaterialGrade; gradeReason:string; name:string; quantity:number }
export interface ProcessingJobDetail {
  jobId:number; jobCode:string; requestId:number; quoteId:number; brandId:number; processorId:number
  fee:number; paymentAmount:number; status:ProcessingJobStatus; paymentExpiresAt:string; createdAt:string
  chainJobId:number|null; inputMaterialId:number; inputQuantity:number; lossQuantity:number; resultRevision:number
  carrier:string|null; trackingNumber:string|null; returnCarrier:string|null; returnTrackingNumber:string|null
  returnShippingEvidenceHash:string|null; rejectionReason:string|null; shippedAt:string|null; receivedAt:string|null; startedAt:string|null
  resultSubmittedAt:string|null; rejectedAt:string|null; returnShippedAt:string|null; completedAt:string|null
  outputs:ProcessingOutput[]
}
export interface ProcessingChainPreparation {
  jobId:number; chainJobId:number|null; status:ProcessingJobStatus; brandWallet:string; processorWallet:string
  inputTokenId:number; inputQuantity:number; processTypes:ProcessType[]
}
export interface ResultDraftInput {
  outputs:Array<{ materialType:MaterialType; grade:MaterialGrade; gradeReason:string; name:string; quantity:number }>
}
export interface SignatureChallenge {
  challengeId:number; action:ProcessingSignatureAction; walletAddress:string; nonce:string; message:string; expiresAt:string
}
const root='/processing-jobs'
export const processingJobApi={
  list:async()=>(await apiClient.get<ProcessingJobDetail[]>(root)).data,
  detail:async(jobId:number)=>(await apiClient.get<ProcessingJobDetail>(`${root}/${jobId}`)).data,
  chainPreparation:async(jobId:number)=>(await apiClient.get<ProcessingChainPreparation>(`${root}/${jobId}/chain`)).data,
  linkChain:async(jobId:number,input:{onChainId:number;chainLockId:number;txHash:string;blockNumber:number})=>(await apiClient.post<ProcessingJobDetail>(`${root}/${jobId}/chain`,input)).data,
  ship:async(jobId:number,input:{carrier?:string;trackingNumber?:string})=>(await apiClient.patch<ProcessingJobDetail>(`${root}/${jobId}/ship`,input)).data,
  receive:async(jobId:number,input:{txHash:string})=>(await apiClient.patch<ProcessingJobDetail>(`${root}/${jobId}/receive`,input)).data,
  start:async(jobId:number)=>(await apiClient.patch<ProcessingJobDetail>(`${root}/${jobId}/start`)).data,
  saveResult:async(jobId:number,input:ResultDraftInput)=>(await apiClient.post<ProcessingJobDetail>(`${root}/${jobId}/results`,input)).data,
  challenge:async(jobId:number,action:ProcessingSignatureAction,rejectionReason?:string)=>(await apiClient.post<SignatureChallenge>(`${root}/${jobId}/signature-challenge`,{action,rejectionReason})).data,
  submitResult:async(jobId:number,challengeId:number,signature:string)=>(await apiClient.post<ProcessingJobDetail>(`${root}/${jobId}/results/submit`,{challengeId,signature})).data,
  reject:async(jobId:number,challengeId:number,signature:string,reason:string)=>(await apiClient.post<ProcessingJobDetail>(`${root}/${jobId}/reject`,{challengeId,signature,reason})).data,
  shipReturn:async(jobId:number,input:{carrier?:string;trackingNumber?:string;evidenceFileId:number;txHash:string})=>(await apiClient.patch<ProcessingJobDetail>(`${root}/${jobId}/return/ship`,input)).data,
  receiveReturn:async(jobId:number,input:{txHash:string})=>(await apiClient.post<ProcessingJobDetail>(`${root}/${jobId}/return/receive`,input)).data,
  returnRejected:async(jobId:number,input:{carrier?:string;trackingNumber?:string;evidenceFileId:number;txHash:string})=>(await apiClient.post<ProcessingJobDetail>(`${root}/${jobId}/return/rejected`,input)).data,
}
