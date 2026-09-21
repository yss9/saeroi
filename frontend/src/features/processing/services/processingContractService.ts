import { Interface, keccak256, toUtf8Bytes, type ContractTransactionReceipt, type ContractTransactionResponse } from 'ethers'
import { abis } from '../../../config/contracts.ts'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import type { MaterialGrade } from '../../material/types/material.types.ts'
import type { ProcessType } from '../types/processing.types.ts'

const PROCESS_TYPE_CODE: Record<ProcessType, number> = {
  CLEANING: 0, DISASSEMBLY: 1, SORTING: 2, CUTTING: 3, SHREDDING: 4,
  COLOR_PROCESSING: 5, SURFACE_FINISHING: 6, REMANUFACTURING: 7,
}
const GRADE_CODE: Record<MaterialGrade, number> = { A: 1, B: 2, C: 3 }

export const PROCESSING_CHAIN_STATUS = {
  NONE: 0, REQUESTED: 1, ACCEPTED: 2, SHIPPED: 3, RECEIVED: 4, PROCESSING: 5,
  RESULT_SUBMITTED: 6, CONFIRMED: 7, RESULT_REJECTED: 8, DECLINED: 9,
  CANCELED: 10, RETURN_SHIPPED: 11, REJECTED_INPUT_RETURNED: 12,
} as const

export interface ChainOutput {
  amount: number
  grade: MaterialGrade
  metadataUri: string
  evidenceHash: string
}

export function processingEvidenceHash(value: unknown): string {
  return keccak256(toUtf8Bytes(JSON.stringify(value)))
}

export async function createProcessingJob(processor: string, tokenId: number, amount: number, processingTypes: ProcessType[]): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('processingRegistry')
  return contract.createJob(processor, [tokenId], [amount], processingTypes.map(type => PROCESS_TYPE_CODE[type])) as Promise<ContractTransactionResponse>
}
export async function acceptProcessingJob(jobId: number): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).acceptJob(jobId) as Promise<ContractTransactionResponse>
}
export async function markProcessingShipped(jobId: number, evidenceHash: string): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).markShipped(jobId, evidenceHash) as Promise<ContractTransactionResponse>
}
export async function confirmProcessingReceipt(jobId: number): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).confirmReceipt(jobId) as Promise<ContractTransactionResponse>
}
export async function startProcessingOnChain(jobId: number): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).startProcessing(jobId) as Promise<ContractTransactionResponse>
}
export async function submitProcessingResultOnChain(jobId: number, outputs: ChainOutput[], resultEvidenceHash: string): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('processingRegistry')
  return contract.submitProcessingResult(jobId, outputs.map(() => 0), outputs.map(v => v.amount), outputs.map(v => GRADE_CODE[v.grade]), outputs.map(v => v.metadataUri), outputs.map(v => v.evidenceHash), resultEvidenceHash) as Promise<ContractTransactionResponse>
}
export async function markProcessingReturnShipped(jobId: number, evidenceHash: string): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).markReturnShipped(jobId, evidenceHash) as Promise<ContractTransactionResponse>
}
export async function returnRejectedProcessingInput(jobId: number, evidenceHash: string): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).returnRejectedInput(jobId, evidenceHash) as Promise<ContractTransactionResponse>
}
export async function approveProcessingResultOnChain(jobId: number): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).approveProcessingResult(jobId) as Promise<ContractTransactionResponse>
}
export async function rejectProcessingResultOnChain(jobId: number): Promise<ContractTransactionResponse> {
  return (await getWriteContract('processingRegistry')).rejectProcessingResult(jobId) as Promise<ContractTransactionResponse>
}
/**
 * 거절·만료된 가공 작업의 온체인 잠금을 푼다.
 *
 * 컨트랙트는 `job.brand` 또는 DEFAULT_ADMIN_ROLE 에게만 허용한다. 서버는 지갑이
 * 없으므로 브랜드가 직접 서명한다.
 */
export async function cancelProcessingJob(chainJobId: number): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('processingRegistry')
  return contract.cancelJob(chainJobId) as Promise<ContractTransactionResponse>
}
export async function getProcessingChainStatus(jobId: number): Promise<number> {
  const job = await (await getReadContract('processingRegistry').getJob(jobId)) as { status: bigint }
  return Number(job.status)
}
async function getSingleProcessingInput(jobId: number): Promise<{ brandLockId: bigint; processorLockId: bigint }> {
  const contract = await getReadContract('processingRegistry')
  const inputs = await contract.getInputs(jobId) as Array<{ brandLockId: bigint; processorLockId: bigint }>
  if (inputs.length !== 1) throw new Error('현재 화면은 하나의 입력 소재가 있는 가공 작업만 지원합니다.')
  return inputs[0]
}
export async function getProcessingBrandLockId(jobId: number): Promise<number> {
  const input = await getSingleProcessingInput(jobId)
  if (input.brandLockId === 0n) throw new Error('Brand 소재 잠금 정보를 찾지 못했습니다.')
  return Number(input.brandLockId)
}
export async function getProcessingProcessorLockId(jobId: number): Promise<number> {
  const input = await getSingleProcessingInput(jobId)
  if (input.processorLockId === 0n) throw new Error('Processor 소재 잠금 정보를 찾지 못했습니다.')
  return Number(input.processorLockId)
}
export function readProcessingEventValues(receipt: ContractTransactionReceipt, eventName: string, argumentName: string): bigint[] {
  const iface = new Interface(abis.processingRegistry)
  const values: bigint[] = []
  for (const log of receipt.logs) {
    try {
      const parsed = iface.parseLog({ topics: [...log.topics], data: log.data })
      if (parsed?.name === eventName) values.push(parsed.args[argumentName] as bigint)
    } catch { /* 다른 컨트랙트 로그 */ }
  }
  return values
}
