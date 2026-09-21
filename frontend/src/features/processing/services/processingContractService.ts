import { Interface, keccak256, toUtf8Bytes, type ContractTransactionReceipt, type ContractTransactionResponse } from 'ethers'
import { abis } from '../../../config/contracts.ts'
import { env } from '../../../config/env.ts'
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
 * <b>공정까지 대조한다.</b> 브랜드·가공사·소재·수량이 같고 공정만 다른 두 요청은
 * 흔하다(같은 원단을 세척만 맡기는 건과 세척+재단을 맡기는 건). 공정을 보지 않으면
 * 뒤의 요청이 앞의 요청을 위해 만들어진 작업을 가져다 쓰고, 체인에는 <b>하지도 않은
 * 공정</b>이 그 작업의 이력으로 영구히 남는다 — 이 계층이 존재하는 이유가 그 이력이다.
 * 순서는 보지 않는다. 체인에 적히는 순서는 등록 시점에 서버가 준 `processTypes` 의
 * 순서인데 재조회 때도 같다는 보장이 없고, 순서까지 요구했다가 놓치면 그대로 서명으로
 * 떨어져 잠금이 두 번 걸린다. 공정 묶음이 같으면 같은 일이다.
 *
 * <b>찾지 못하는 경우 — 놓치면 곧바로 두 번째 잠금이다.</b> 아래 어느 경우든 이 함수는
 * `null` 을 돌려주고, 호출자는 그대로 서명한다. 즉 <b>탐지 실패의 대가는 이 장치가
 * 막으려는 바로 그 중복 잠금</b>이다.
 * <ul>
 *   <li>`createJob` 트랜잭션이 아직 블록에 들어가지 않았다면 로그가 없다 — 탭이
 *       닫혀 영수증을 못 본 채 뒤늦게 채굴되는 경우가 여기다.</li>
 *   <li>같은 가공사·소재·수량·공정으로 고아가 둘 이상이면 최근 것만 되살아나고
 *       나머지는 그대로 남는다. 구분할 단서가 체인에 없다.</li>
 *   <li><b>노드가 로그를 조용히 잘라 돌려주면 알 방법이 없다.</b> 범위를 거절하는
 *       제공자라면 예외가 나서 등록이 막히지만(서명 전이라 잠금은 늘지 않는다),
 *       말없이 일부만 돌려주는 노드에서는 "고아가 없다" 와 구분되지 않는다 —
 *       그대로 서명해 잠금이 두 번 걸린다.</li>
 * </ul>
 *
 * 조회는 `env.chainFromBlock` 부터 본다. 이 컨트랙트보다 앞선 블록에 로그가 있을 수
 * 없고, 제네시스부터 훑으면 SSAFY 체인에서 전 구간 `eth_getLogs` 가 수십 초까지 가서
 * 그 시간이 통째로 MetaMask 서명 팝업 앞을 막는다(`env.chainFromBlock` 주석의 실측).
 * 값이 비어 있으면 0 으로 떨어져 예전처럼 전 구간을 훑는다 — 느릴 뿐 틀리지 않는다.
 */
export async function findUnlinkedProcessingJob(filter: {
  brandWallet: string
  processorWallet: string
  tokenId: number
  amount: number
  processTypes: readonly ProcessType[]
  linkedChainJobIds: readonly number[]
}): Promise<UnlinkedProcessingJob | null> {
  const contract = await getReadContract('processingRegistry')
  const logs = await contract.queryFilter(
    contract.filters.ProcessingJobCreated(null, filter.brandWallet, filter.processorWallet),
    env.chainFromBlock,
    'latest',
  ) as Array<{ args?: { jobId?: bigint }; transactionHash: string; blockNumber: number }>
  const wanted = new Set(filter.processTypes.map(type => PROCESS_TYPE_CODE[type]))
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
    // 공정 묶음이 다르면 다른 요청의 작업이다 — 가져다 쓰면 체인에 거짓 이력이 남는다.
    const codes = new Set((await contract.getProcessingTypes(onChainId) as Array<bigint | number>)
      .map(code => Number(code)))
    if (codes.size !== wanted.size || [...wanted].some(code => !codes.has(code))) continue
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
