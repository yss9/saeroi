import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import { PROCESSING_CHAIN_STATUS, cancelProcessingJob, createProcessingJob, getProcessingBrandLockId, getProcessingProcessorLockId, returnRejectedProcessingInput, submitProcessingResultOnChain } from './processingContractService.ts'

const contract = vi.hoisted(() => ({
  createJob: vi.fn(),
  submitProcessingResult: vi.fn(),
  getInputs: vi.fn(),
  returnRejectedInput: vi.fn(),
  cancelJob: vi.fn(),
  getJob: vi.fn(),
  getProcessingTypes: vi.fn(),
  queryFilter: vi.fn(),
  filters: { ProcessingJobCreated: vi.fn(() => 'ProcessingJobCreated-filter') },
}))
const operationId = `0x${'01'.repeat(32)}`

vi.mock('../../../services/blockchain/contractFactory.ts', () => ({
  getWriteContract: vi.fn(),
  getReadContract: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getWriteContract).mockResolvedValue(contract as never)
  vi.mocked(getReadContract).mockResolvedValue(contract as never)
  contract.createJob.mockResolvedValue({ hash: '0xcreate' })
  contract.submitProcessingResult.mockResolvedValue({ hash: '0xresult' })
  contract.cancelJob.mockResolvedValue({ hash: '0xcancel' })
})

describe('processingContractService', () => {
  it('기존 상태 번호를 유지하고 반려 입력 반환 상태를 마지막에 추가한다', () => {
    expect(PROCESSING_CHAIN_STATUS.CONFIRMED).toBe(7)
    expect(PROCESSING_CHAIN_STATUS.RESULT_REJECTED).toBe(8)
    expect(PROCESSING_CHAIN_STATUS.DECLINED).toBe(9)
    expect(PROCESSING_CHAIN_STATUS.CANCELED).toBe(10)
    expect(PROCESSING_CHAIN_STATUS.RETURN_SHIPPED).toBe(11)
    expect(PROCESSING_CHAIN_STATUS.REJECTED_INPUT_RETURNED).toBe(12)
  })

  it('반려된 입력 소재 반환은 실제 증빙 파일 해시와 함께 호출한다', async () => {
    const hash = `0x${'cd'.repeat(32)}`
    await returnRejectedProcessingInput(12, hash, operationId)
    expect(contract.returnRejectedInput).toHaveBeenCalledWith(12, hash, operationId)
  })

  it('Processor 수령 이후 잠금 번호는 processorLockId에서 읽는다', async () => {
    contract.getInputs.mockResolvedValue([{ brandLockId: 31n, processorLockId: 47n }])

    await expect(getProcessingBrandLockId(8)).resolves.toBe(31)
    await expect(getProcessingProcessorLockId(8)).resolves.toBe(47)
  })

  it('백엔드 공정 enum 순서를 컨트랙트 enum 번호로 변환해 작업을 만든다', async () => {
    await createProcessingJob('0x0000000000000000000000000000000000000002', 9, 250, ['CLEANING', 'CUTTING'], operationId)

    expect(contract.createJob).toHaveBeenCalledWith(
      '0x0000000000000000000000000000000000000002',
      [9],
      [250],
      [0, 3],
      operationId,
    )
  })

  it('산출물 등급과 입력 인덱스를 ProcessingRegistry 계약에 맞게 전송한다', async () => {
    const hash = `0x${'ab'.repeat(32)}`
    await submitProcessingResultOnChain(4, [
      { amount: 180, grade: 'A', metadataUri: 'saeroi://processing/4/output/1', evidenceHash: hash },
      { amount: 40, grade: 'C', metadataUri: 'saeroi://processing/4/output/2', evidenceHash: hash },
    ], hash, operationId)

    expect(contract.submitProcessingResult).toHaveBeenCalledWith(
      4,
      [0, 0],
      [180, 40],
      [1, 3],
      ['saeroi://processing/4/output/1', 'saeroi://processing/4/output/2'],
      [hash, hash],
      hash,
      operationId,
    )
  })

  it('브랜드 지갑으로 온체인 잠금을 해제한다', async () => {
    await cancelProcessingJob(11, operationId)

    expect(contract.cancelJob).toHaveBeenCalledWith(11, operationId)
  })
})
