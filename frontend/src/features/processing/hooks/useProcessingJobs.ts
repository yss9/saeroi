import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ContractTransactionResponse } from 'ethers'
import { processingJobApi, type ProcessingSignatureAction, type ResultDraftInput } from '../api/processingJobApi.ts'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { signMessage } from '../../wallet/services/walletService.ts'
import { getEvidence } from '../../evidence/api/getEvidence.ts'
import { evidenceKeys } from '../../evidence/api/evidenceKeys.ts'
import {
  PROCESSING_CHAIN_STATUS,
  acceptProcessingJob,
  approveProcessingResultOnChain,
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
  const chainPreparation = useQuery({
    queryKey: keys.chainPreparation(jobId),
    queryFn: () => processingJobApi.chainPreparation(jobId),
    enabled: detail.data?.status !== 'CREATED' && detail.data?.status !== 'EXPIRED',
  })
  const chainJobId = chainPreparation.data?.chainJobId
  const chainStatus = useQuery({
    queryKey: keys.chainStatus(jobId),
    queryFn: () => getProcessingChainStatus(chainJobId!),
    enabled: chainJobId !== null && chainJobId !== undefined,
    refetchInterval: 30_000,
  })
  const refresh = async () => Promise.all([
    client.invalidateQueries({ queryKey: keys.detail(jobId) }),
    client.invalidateQueries({ queryKey: keys.all }),
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
  const accept = useMutation({
    mutationFn: async () => {
      await confirmed(acceptProcessingJob(requireChainJob()))
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
    initializeChain, accept, ship, receive, start, saveResult, submit, shipReturn, approve, reject, returnRejected,
    chainStatusCode: chainStatus.data ?? PROCESSING_CHAIN_STATUS.NONE,
  }
}
