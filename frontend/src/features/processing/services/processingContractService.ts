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
/** `ProcessingJobCreated` 로그 한 건이 가리키는, 아직 DB 에 연결되지 않은 온체인 작업. */
export interface UnlinkedProcessingJob { onChainId: number; txHash: string; blockNumber: number }

/**
 * 이 브랜드가 같은 조건으로 이미 올려 뒀지만 **아직 어디에도 연결되지 않은** 온체인 작업.
 *
 * <b>왜 필요한가</b> — `createJob` 이 확정된 뒤 `POST /processing-jobs/{id}/chain` 이
 * 실패하면 그 온체인 작업은 DB 어디에도 적히지 않는다. 등록 패널은 그대로 돌아오고,
 * 그대로 다시 누르면 같은 소재가 두 번째로 잠긴다. 두 번째 잠금은 가리킬 `chainJobId`
 * 가 없으므로 `cancelJob` 을 부를 수도 없다 — **영구 손실이다.**
 *
 * 수락(`acceptJob`)처럼 읽어 볼 상태값이 없어서(연결 전이라 번호 자체를 모른다) 대신
 * 이벤트를 뒤진다. 브랜드·가공사 주소로 걸러 온 `ProcessingJobCreated` 중
 * <b>아직 REQUESTED 이고, 투입이 이 요청과 같고, 다른 작업이 가져가지 않은</b> 번호를
 * 최근 것부터 고른다.
 *
 * <b>찾지 못하는 경우</b>
 * <ul>
 *   <li>`createJob` 트랜잭션이 아직 블록에 들어가지 않았다면 로그가 없다 — 탭이
 *       닫혀 영수증을 못 본 채 뒤늦게 채굴되는 경우가 여기다.</li>
 *   <li>같은 가공사·같은 소재·같은 수량으로 고아가 둘 이상이면 최근 것만 되살아나고
 *       나머지는 그대로 남는다. 구분할 단서가 체인에 없다.</li>
 *   <li>노드가 로그를 잘라 두었거나 조회 범위를 제한하면 여기서 예외가 난다 — 서명
 *       전이므로 잠금이 늘지는 않지만 등록이 막힌다.</li>
 * </ul>
 */
export async function findUnlinkedProcessingJob(filter: {
  brandWallet: string
  processorWallet: string
  tokenId: number
  amount: number
  linkedChainJobIds: readonly number[]
}): Promise<UnlinkedProcessingJob | null> {
  const contract = await getReadContract('processingRegistry')
  const logs = await contract.queryFilter(
    contract.filters.ProcessingJobCreated(null, filter.brandWallet, filter.processorWallet),
  ) as Array<{ args?: { jobId?: bigint }; transactionHash: string; blockNumber: number }>
  // 최근 것부터 본다 — 방금 실패한 등록이 있다면 그것이 가장 뒤에 있다.
  for (let index = logs.length - 1; index >= 0; index -= 1) {
    const log = logs[index]
    const onChainId = Number(log.args?.jobId)
    if (!Number.isSafeInteger(onChainId) || onChainId <= 0) continue
    if (filter.linkedChainJobIds.includes(onChainId)) continue
    // REQUESTED 가 아니면 누군가 이미 쓰고 있는 작업이다(수락됐거나 취소됐거나).
    const job = await contract.getJob(onChainId) as { status: bigint }
    if (Number(job.status) !== PROCESSING_CHAIN_STATUS.REQUESTED) continue
    const inputs = await contract.getInputs(onChainId) as Array<{ tokenId: bigint; amount: bigint }>
    if (inputs.length !== 1) continue
    if (Number(inputs[0].tokenId) !== filter.tokenId || Number(inputs[0].amount) !== filter.amount) continue
    return { onChainId, txHash: log.transactionHash, blockNumber: log.blockNumber }
  }
  return null
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
