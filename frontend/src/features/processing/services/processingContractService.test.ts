import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import { PROCESSING_CHAIN_STATUS, cancelProcessingJob, createProcessingJob, findUnlinkedProcessingJob, getProcessingBrandLockId, getProcessingProcessorLockId, returnRejectedProcessingInput, submitProcessingResultOnChain } from './processingContractService.ts'

const contract = vi.hoisted(() => ({
  createJob: vi.fn(),
  submitProcessingResult: vi.fn(),
  getInputs: vi.fn(),
  returnRejectedInput: vi.fn(),
  cancelJob: vi.fn(),
  getJob: vi.fn(),
  queryFilter: vi.fn(),
  filters: { ProcessingJobCreated: vi.fn(() => 'ProcessingJobCreated-filter') },
}))

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
    await returnRejectedProcessingInput(12, hash)
    expect(contract.returnRejectedInput).toHaveBeenCalledWith(12, hash)
  })

  it('Processor 수령 이후 잠금 번호는 processorLockId에서 읽는다', async () => {
    contract.getInputs.mockResolvedValue([{ brandLockId: 31n, processorLockId: 47n }])

    await expect(getProcessingBrandLockId(8)).resolves.toBe(31)
    await expect(getProcessingProcessorLockId(8)).resolves.toBe(47)
  })

  it('백엔드 공정 enum 순서를 컨트랙트 enum 번호로 변환해 작업을 만든다', async () => {
    await createProcessingJob('0x0000000000000000000000000000000000000002', 9, 250, ['CLEANING', 'CUTTING'])

    expect(contract.createJob).toHaveBeenCalledWith(
      '0x0000000000000000000000000000000000000002',
      [9],
      [250],
      [0, 3],
    )
  })

  it('산출물 등급과 입력 인덱스를 ProcessingRegistry 계약에 맞게 전송한다', async () => {
    const hash = `0x${'ab'.repeat(32)}`
    await submitProcessingResultOnChain(4, [
      { amount: 180, grade: 'A', metadataUri: 'saeroi://processing/4/output/1', evidenceHash: hash },
      { amount: 40, grade: 'C', metadataUri: 'saeroi://processing/4/output/2', evidenceHash: hash },
    ], hash)

    expect(contract.submitProcessingResult).toHaveBeenCalledWith(
      4,
      [0, 0],
      [180, 40],
      [1, 3],
      ['saeroi://processing/4/output/1', 'saeroi://processing/4/output/2'],
      [hash, hash],
      hash,
    )
  })

  it('브랜드 지갑으로 온체인 잠금을 해제한다', async () => {
    await cancelProcessingJob(11)

    expect(contract.cancelJob).toHaveBeenCalledWith(11)
  })

  /*
    **서명은 성공했는데 서버 기록이 실패한 작업을 다시 찾아낸다.**

    createJob 이 확정된 뒤 `POST /processing-jobs/{id}/chain` 이 실패하면 그 온체인
    작업은 DB 어디에도 적히지 않는다. 그대로 다시 등록하면 같은 소재가 두 번 잠기고,
    두 번째 잠금은 DB 에서 가리킬 수 없으므로 영영 풀 수 없다. 그래서 서명 전에
    `ProcessingJobCreated` 로그를 먼저 뒤져 "내가 만들었는데 아직 연결되지 않은"
    작업이 있는지 본다.
  */
  describe('findUnlinkedProcessingJob', () => {
    const brand = '0x0000000000000000000000000000000000000001'
    const processor = '0x0000000000000000000000000000000000000002'
    const log = (jobId: bigint, hash: string, block: number) =>
      ({ args: { jobId }, transactionHash: hash, blockNumber: block })

    it('같은 조건으로 만들어졌고 아직 연결되지 않은 REQUESTED 작업을 찾아 준다', async () => {
      contract.queryFilter.mockResolvedValue([log(7n, '0xorphan', 1_234)])
      contract.getJob.mockResolvedValue({ status: BigInt(PROCESSING_CHAIN_STATUS.REQUESTED) })
      contract.getInputs.mockResolvedValue([{ tokenId: 9n, amount: 250n, brandLockId: 3n, processorLockId: 0n }])

      await expect(findUnlinkedProcessingJob({
        brandWallet: brand, processorWallet: processor, tokenId: 9, amount: 250, linkedChainJobIds: [],
      })).resolves.toEqual({ onChainId: 7, txHash: '0xorphan', blockNumber: 1_234 })
      expect(contract.filters.ProcessingJobCreated).toHaveBeenCalledWith(null, brand, processor)
    })

    it('이미 다른 작업에 연결된 번호는 건너뛴다 — 그 잠금은 주인이 있다', async () => {
      contract.queryFilter.mockResolvedValue([log(7n, '0xlinked', 1_234)])
      contract.getJob.mockResolvedValue({ status: BigInt(PROCESSING_CHAIN_STATUS.REQUESTED) })
      contract.getInputs.mockResolvedValue([{ tokenId: 9n, amount: 250n, brandLockId: 3n, processorLockId: 0n }])

      await expect(findUnlinkedProcessingJob({
        brandWallet: brand, processorWallet: processor, tokenId: 9, amount: 250, linkedChainJobIds: [7],
      })).resolves.toBeNull()
    })

    it('이미 수락·취소된 작업은 후보가 아니다 — 잠금의 주인이 따로 있다는 뜻이다', async () => {
      contract.queryFilter.mockResolvedValue([log(7n, '0xaccepted', 1_234)])
      contract.getJob.mockResolvedValue({ status: BigInt(PROCESSING_CHAIN_STATUS.ACCEPTED) })

      await expect(findUnlinkedProcessingJob({
        brandWallet: brand, processorWallet: processor, tokenId: 9, amount: 250, linkedChainJobIds: [],
      })).resolves.toBeNull()
    })

    it('투입 소재나 수량이 다르면 다른 요청의 작업이다', async () => {
      contract.queryFilter.mockResolvedValue([log(7n, '0xother', 1_234)])
      contract.getJob.mockResolvedValue({ status: BigInt(PROCESSING_CHAIN_STATUS.REQUESTED) })
      contract.getInputs.mockResolvedValue([{ tokenId: 9n, amount: 400n, brandLockId: 3n, processorLockId: 0n }])

      await expect(findUnlinkedProcessingJob({
        brandWallet: brand, processorWallet: processor, tokenId: 9, amount: 250, linkedChainJobIds: [],
      })).resolves.toBeNull()
    })

    it('후보가 여럿이면 가장 최근 것을 고른다', async () => {
      contract.queryFilter.mockResolvedValue([log(5n, '0xold', 100), log(7n, '0xnew', 200)])
      contract.getJob.mockResolvedValue({ status: BigInt(PROCESSING_CHAIN_STATUS.REQUESTED) })
      contract.getInputs.mockResolvedValue([{ tokenId: 9n, amount: 250n, brandLockId: 3n, processorLockId: 0n }])

      await expect(findUnlinkedProcessingJob({
        brandWallet: brand, processorWallet: processor, tokenId: 9, amount: 250, linkedChainJobIds: [],
      })).resolves.toEqual({ onChainId: 7, txHash: '0xnew', blockNumber: 200 })
    })
  })
})
