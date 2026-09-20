import { apiClient } from '../../../services/apiClient.ts'
import type { MaterialType } from '../types/processorProfile.types.ts'
import type { ProcessType, ProcessingJobStatus } from '../types/processing.types.ts'
import type { MaterialGrade } from '../../material/types/material.types.ts'

export type ProcessingSignatureAction = 'RESULT_SUBMIT' | 'APPROVE' | 'REJECT'
export interface ProcessingOutput { outputId:number; materialId:number|null; materialType:MaterialType; grade:MaterialGrade; gradeReason:string; name:string; quantity:number }
export interface ProcessingJobDetail {
  jobId:number; jobCode:string; requestId:number; brandId:number; processorId:number
  fee:number; paymentAmount:number; status:ProcessingJobStatus; paymentExpiresAt:string; createdAt:string
  chainJobId:number|null; inputMaterialId:number; inputQuantity:number; lossQuantity:number; resultRevision:number
  carrier:string|null; trackingNumber:string|null; returnCarrier:string|null; returnTrackingNumber:string|null
  returnShippingEvidenceHash:string|null; rejectionReason:string|null; shippedAt:string|null; receivedAt:string|null; startedAt:string|null
  resultSubmittedAt:string|null; rejectedAt:string|null; returnShippedAt:string|null; completedAt:string|null
  /** 가공사가 답해야 하는 시각. 결제 전에는 null */
  acceptExpiresAt:string|null
  acceptedAt:string|null; declinedAt:string|null
  /** 가공사가 **일 자체를** 거절한 사유. 자동 거절(무응답)이면 null — 결과 반려 사유(rejectionReason)와 다른 값이다 */
  declineReason:string|null
  /** 환불된 시각. 값이 있으면 돈이 돌아간 것이다 */
  refundedAt:string|null
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
  /**
   * 가공사가 일을 맡는다 — `PATCH /processing-jobs/{jobId}/accept`
   *
   * **결제된 작업만 수락할 수 있다.** 소재 발송은 이 뒤에만 열린다(결제만으로 보내면 409).
   */
  accept:async(jobId:number)=>(await apiClient.patch<ProcessingJobDetail>(`${root}/${jobId}/accept`)).data,
  /**
   * 가공사가 일을 거절한다 — `PATCH /processing-jobs/{jobId}/reject`
   *
   * 사유는 필수다(500자 이하). 거절하면 가공비가 환불되고 소재 잠금이 풀린다.
   *
   * ⚠️ 같은 경로의 `POST .../reject` 와 **다른 요청이다.** 그쪽은 브랜드가 가공 결과를
   * 반려하는 것이고(`rejectResult`), 이쪽은 가공사가 일 자체를 거절하는 것이다 —
   * 메서드와 역할로 갈린다.
   */
  decline:async(jobId:number,reason:string)=>(await apiClient.patch<ProcessingJobDetail>(`${root}/${jobId}/reject`,{reason})).data,
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
