import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ContractTransactionResponse } from 'ethers'
import { processingJobApi, type ProcessingSignatureAction, type ResultDraftInput } from '../api/processingJobApi.ts'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { signMessage } from '../../wallet/services/walletService.ts'
import { getEvidence } from '../../evidence/api/getEvidence.ts'
import { evidenceKeys } from '../../evidence/api/evidenceKeys.ts'
import {
  PROCESSING_CHAIN_STATUS,
  acceptProcessingJob,
  approveProcessingResultOnChain,
  cancelProcessingJob,
  confirmProcessingReceipt,
  createProcessingJob,
  getProcessingBrandLockId,
  getProcessingChainStatus,
  markProcessingReturnShipped,
  markProcessingShipped,
  processingEvidenceHash,
  readProcessingEventValues,
  returnRejectedProcessingInput,
  rejectProcessingResultOnChain,
  startProcessingOnChain,
  submitProcessingResultOnChain,
} from '../services/processingContractService.ts'

const keys = {
  all: ['processing-jobs'] as const,
  detail: (id: number) => ['processing-jobs', id] as const,
  chainPreparation: (id: number) => ['processing-jobs', id, 'chain-preparation'] as const,
  chainStatus: (id: number) => ['processing-jobs', id, 'chain-status'] as const,
}

async function confirmed(response: Promise<ContractTransactionResponse>) {
  const tx = await response
  const receipt = await tx.wait()
  if (!receipt || receipt.status !== 1) throw new Error('블록체인 트랜잭션이 확정되지 않았습니다.')
  return receipt
}

export function useProcessingJobs() {
  return useQuery({ queryKey: keys.all, queryFn: processingJobApi.list })
}

export function useProcessingJob(jobId: number) {
  const client = useQueryClient()
  const detail = useQuery({ queryKey: keys.detail(jobId), queryFn: () => processingJobApi.detail(jobId) })
  const evidence = useQuery({
    queryKey: evidenceKeys.list('PROCESSING_JOB', jobId),
    queryFn: () => getEvidence('PROCESSING_JOB', jobId),
  })
  const requestId = detail.data?.requestId
  const request = useQuery({
    queryKey: processingKeys.detail(requestId ?? 0),
    queryFn: ({ signal }) => processingRequestApi.detail(requestId!, signal),
    enabled: requestId !== undefined,
  })
  /*
    EXPIRED 도 켜 둔다 — 결제 전 취소(CREATED)만 정말 체인에 아무것도 없다. 만료된
    작업은 이미 온체인 잠금이 있을 수 있고, 그 상태를 알아야 브랜드에게 해제 패널을
    보여줄 수 있다(release-lock 게이트가 chainStatusCode 를 본다).
  */
  const chainPreparation = useQuery({
    queryKey: keys.chainPreparation(jobId),
    queryFn: () => processingJobApi.chainPreparation(jobId),
    enabled: detail.data?.status !== 'CREATED',
  })
  const chainJobId = chainPreparation.data?.chainJobId
  const chainStatus = useQuery({
    queryKey: keys.chainStatus(jobId),
    queryFn: () => getProcessingChainStatus(chainJobId!),
    enabled: chainJobId !== null && chainJobId !== undefined,
    refetchInterval: 30_000,
  })
  /*
    보유 소재까지 함께 비운다. 수령 확인(approve)이 산출 소재를 브랜드 잔액으로 발행하므로,
    소재 캐시를 그대로 두면 방금 받은 소재가 제품 생산의 투입 후보에 뜨지 않는다 — 사용자는
    소재가 오지 않았다고 읽는다.
  */
  const refresh = async () => Promise.all([
    client.invalidateQueries({ queryKey: keys.detail(jobId) }),
    client.invalidateQueries({ queryKey: keys.all }),
    client.invalidateQueries({ queryKey: inventoryKeys.all }),
    client.invalidateQueries({ queryKey: materialKeys.all }),
    client.invalidateQueries({ queryKey: keys.chainPreparation(jobId) }),
    client.invalidateQueries({ queryKey: keys.chainStatus(jobId) }),
  ])
  const requirePreparation = () => {
    if (!chainPreparation.data) throw new Error('온체인 작업 정보를 불러오지 못했습니다.')
    return chainPreparation.data
  }
  const requireChainJob = () => {
    const id = requirePreparation().chainJobId
    if (!id) throw new Error('먼저 온체인 작업을 등록해 주세요.')
    return id
  }

  const initializeChain = useMutation({
    mutationFn: async () => {
      const prep = requirePreparation()
      const receipt = await confirmed(createProcessingJob(
        prep.processorWallet,
        prep.inputTokenId,
        prep.inputQuantity,
        prep.processTypes,
      ))
      const values = readProcessingEventValues(receipt, 'ProcessingJobCreated', 'jobId')
      if (values.length !== 1) throw new Error('온체인 작업 번호를 확인하지 못했습니다.')
      const onChainId = Number(values[0])
      const chainLockId = await getProcessingBrandLockId(onChainId)
      return processingJobApi.linkChain(jobId, {
        onChainId,
        chainLockId,
        txHash: receipt.hash,
        blockNumber: receipt.blockNumber,
      })
    },
    onSuccess: refresh,
  })
  /*
    수락은 온체인 서명 한 번이다 — 가공사가 지갑으로 서명해 체인에 동의를 남기고,
    그 서명이 성공해야만 서버에 수락을 기록한다. 서명 없이 DB 만 바뀌는 경로는 없다
    (서버도 chainJobId 없는 작업의 accept 를 막는다).

    **서명과 기록 사이에서 끊기면 재시도가 두 번째 서명이 되면 안 된다.** 서명은
    성공했는데 이어지는 PATCH 가 실패하면(네트워크 끊김, 500, 탭 닫힘) 작업은 여전히
    PAID 로 남고 가공사는 같은 버튼을 다시 본다 — 이때 그대로 다시 서명하면 컨트랙트가
    `REQUESTED` 를 요구하는데 이미 `ACCEPTED` 라서 되돌린다. 그래서 매번 온체인 상태를
    먼저 읽어 이미 수락돼 있으면 서명을 건너뛰고 PATCH 만 다시 부른다.
  */
  const accept = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      const chainState = await getProcessingChainStatus(id)
      if (chainState === PROCESSING_CHAIN_STATUS.REQUESTED) await confirmed(acceptProcessingJob(id))
      await processingJobApi.accept(jobId)
    },
    onSuccess: refresh,
  })
  const ship = useMutation({
    mutationFn: async (input: { carrier?: string; trackingNumber?: string }) => {
      const id = requireChainJob()
      const receipt = await confirmed(markProcessingShipped(id, processingEvidenceHash({
        type: 'PROCESSING_OUTBOUND_SHIPMENT',
        jobId,
        carrier: input.carrier ?? '',
        trackingNumber: input.trackingNumber ?? '',
      })))
      await processingJobApi.ship(jobId, input)
      return receipt
    },
    onSuccess: refresh,
  })
  const receive = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      const receipt = await confirmed(confirmProcessingReceipt(id))
      return processingJobApi.receive(jobId, {
        txHash: receipt.hash,
      })
    },
    onSuccess: refresh,
  })
  const start = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      await confirmed(startProcessingOnChain(id))
      return processingJobApi.start(jobId)
    },
    onSuccess: refresh,
  })
  const saveResult = useMutation({
    mutationFn: (input: ResultDraftInput) => processingJobApi.saveResult(jobId, input),
    onSuccess: refresh,
  })

  async function signature(action: ProcessingSignatureAction, reason?: string) {
    const challenge = await processingJobApi.challenge(jobId, action, reason)
    return { challenge, signature: await signMessage(challenge.message) }
  }
  const submit = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      const job = detail.data
      if (!job || job.outputs.length === 0) throw new Error('먼저 가공 결과를 저장해 주세요.')
      const evidence = await getEvidence('PROCESSING_JOB', jobId)
      const files = evidence.files ?? []
      if (files.length === 0) throw new Error('가공 증빙을 한 개 이상 등록해 주세요.')
      const signed = await signature('RESULT_SUBMIT')
      const outputHashes = job.outputs.map((output, index) =>
        files[index % files.length]?.fileHash ?? processingEvidenceHash(output))
      const resultEvidenceHash = processingEvidenceHash({
        jobId,
        revision: job.resultRevision,
        outputHashes,
        evidenceHashes: files.map(file => file.fileHash),
      })
      await confirmed(submitProcessingResultOnChain(id, job.outputs.map((output, index) => ({
        amount: output.quantity,
        grade: output.grade,
        metadataUri: `saeroi://processing/${jobId}/output/${output.outputId}`,
        evidenceHash: outputHashes[index],
      })), resultEvidenceHash))
      return processingJobApi.submitResult(jobId, signed.challenge.challengeId, signed.signature)
    },
    onSuccess: refresh,
  })
  const reject = useMutation({
    mutationFn: async (reason: string) => {
      const id = requireChainJob()
      const signed = await signature('REJECT', reason)
      await confirmed(rejectProcessingResultOnChain(id))
      return processingJobApi.reject(jobId, signed.challenge.challengeId, signed.signature, reason)
    },
    onSuccess: refresh,
  })
  const shipReturn = useMutation({
    mutationFn: async (input: { carrier?: string; trackingNumber?: string; evidenceFileId: number; evidenceHash: string }) => {
      const id = requireChainJob()
      const receipt = await confirmed(markProcessingReturnShipped(id, input.evidenceHash))
      return processingJobApi.shipReturn(jobId, {
        carrier: input.carrier,
        trackingNumber: input.trackingNumber,
        evidenceFileId: input.evidenceFileId,
        txHash: receipt.hash,
      })
    },
    onSuccess: refresh,
  })
  const approve = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      const receipt = await confirmed(approveProcessingResultOnChain(id))
      return processingJobApi.receiveReturn(jobId, {
        txHash: receipt.hash,
      })
    },
    onSuccess: refresh,
  })
  /*
    거절에는 온체인 서명이 없다. 가공사가 일 자체를 맡지 않기로 하는 것을 서버에
    적을 뿐이다 — 잠긴 소재를 푸는 것은 브랜드 쪽 releaseChainLock 의 몫이다.
  */
  const declineJob = useMutation({
    mutationFn: (reason: string) => processingJobApi.decline(jobId, reason),
    onSuccess: refresh,
  })
  /*
    거절·만료·취소로 끝났는데 온체인 잠금이 남아 있으면, 그 잠금을 푸는 서명은
    브랜드 지갑만 할 수 있다 — Task 4 의 cancelProcessingJob 을 그대로 쓴다.
  */
  const releaseChainLock = useMutation({
    mutationFn: async () => {
      if (!detail.data?.chainJobId) throw new Error('온체인 작업이 없습니다.')
      const tx = await cancelProcessingJob(detail.data.chainJobId)
      await tx.wait()
    },
    onSuccess: refresh,
  })
  const returnRejected = useMutation({
    mutationFn: async (input: { carrier?: string; trackingNumber?: string; evidenceFileId: number; evidenceHash: string }) => {
      const receipt = await confirmed(returnRejectedProcessingInput(requireChainJob(), input.evidenceHash))
      return processingJobApi.returnRejected(jobId, {
        carrier: input.carrier,
        trackingNumber: input.trackingNumber,
        evidenceFileId: input.evidenceFileId,
        txHash: receipt.hash,
      })
    },
    onSuccess: refresh,
  })

  return {
    detail, request, evidence, chainPreparation, chainStatus,
    initializeChain, accept, declineJob, releaseChainLock, ship, receive, start, saveResult, submit, shipReturn, approve, reject, returnRejected,
    chainStatusCode: chainStatus.data ?? PROCESSING_CHAIN_STATUS.NONE,
  }
}
