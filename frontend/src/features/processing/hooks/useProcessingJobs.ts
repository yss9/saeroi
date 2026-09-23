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
  if (!receipt || receipt.status !== 1) throw new Error('이력 저장이 완료되지 않았습니다.')
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
    if (!chainPreparation.data) throw new Error('작업 기록 정보를 불러오지 못했습니다.')
    return chainPreparation.data
  }
  const requireChainJob = () => {
    const id = requirePreparation().chainJobId
    if (!id) throw new Error('먼저 가공 작업을 등록해 주세요.')
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
      if (values.length !== 1) throw new Error('가공 작업 기록을 확인하지 못했습니다.')
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
  /*
    발송. **서명이 먼저, 서버가 나중이다.**

    서명이 빠지면 체인의 작업이 ACCEPTED 에 머물고, 업체가 수령을 누를 때 컨트랙트가
    "Processing: invalid status" 로 거절한다 — confirmReceipt 는 markShipped 뒤에만 받는다.
    막히는 사람이 담당자가 아니라 업체라서, 담당자는 왜 멈췄는지 알 수 없다.

    **이미 서명이 남았으면 건너뛴다.** 서명이 들어간 뒤 서버 호출이 실패하는 창이 있는데
    (네트워크 끊김, 토큰 만료), 그때 다시 시도하면 markShipped 가 revert 해서 — 체인이 이미
    SHIPPED 라 require(ACCEPTED)를 어긴다 — 그 뒤의 서버 호출까지 못 간다. DB 가 영원히
    따라잡지 못하므로, 재시도는 서버만 채운다. 서버도 체인을 읽어 같은 판정을 한다
    (ChainProcessingStatusReader).
  */
  const ship = useMutation({
    mutationFn: async (input: { carrier?: string; trackingNumber?: string }) => {
      const id = requireChainJob()
      if (!(await chainAlreadyPassed(id, 'SHIPPED'))) {
        await confirmed(markProcessingShipped(id, processingEvidenceHash({
          type: 'PROCESSING_OUTBOUND_SHIPMENT',
          jobId,
          carrier: input.carrier ?? '',
          trackingNumber: input.trackingNumber ?? '',
        })))
      }
      return processingJobApi.ship(jobId, input)
    },
    onSuccess: refresh,
  })
  /*
    수령. 발송과 같은 이유로 **이미 지난 단계면 서명을 건너뛴다.**

    서명이 체인에 들어간 뒤 서버 호출이 실패하면 체인은 RECEIVED 인데 DB 는 SHIPPED 로
    남는다. 다시 누르면 confirmReceipt 가 revert 해서 그 뒤의 서버 호출까지 못 가고, DB 가
    영원히 따라잡지 못한다.

    서버는 트랜잭션 해시를 요구하지 않는다(ProcessingJobWorkflowService.receive) — 건너뛴
    경우에는 보낼 해시가 없으므로 이것이 중요하다.
  */
  const receive = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      if (await chainAlreadyPassed(id, 'RECEIVED')) {
        return processingJobApi.receive(jobId, {})
      }
      const receipt = await confirmed(confirmProcessingReceipt(id))
      return processingJobApi.receive(jobId, {
        txHash: receipt.hash,
      })
    },
    onSuccess: refresh,
  })
  /*
    가공 시작. **여기서 실제로 막혔다.**

    startProcessing 이 체인에 들어간 뒤 서버 보고가 빠져, 체인은 PROCESSING 인데 DB 는
    RECEIVED 였다. 업체가 "가공 시작" 을 누를 때마다 컨트랙트가 require(RECEIVED) 를 어겨
    "Processing: invalid status" 로 거절했고, 그 뒤의 서버 호출까지 못 가서 누를수록 같은
    오류만 났다. 체인 작업 18·19 가 그 상태로 멈춰 있었다.
  */
  const start = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      if (!(await chainAlreadyPassed(id, 'PROCESSING'))) {
        await confirmed(startProcessingOnChain(id))
      }
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
    수락·거절은 온체인 서명이 없다. 브랜드가 결제한 일을 맡을지 말지를 서버에 적는 것이고,
    온체인 작업 등록·수락은 그 뒤 발송 단계에서 따로 일어난다.
  */
  const acceptJob = useMutation({
    mutationFn: () => processingJobApi.accept(jobId),
    onSuccess: refresh,
  })
  const declineJob = useMutation({
    mutationFn: (reason: string) => processingJobApi.decline(jobId, reason),
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
    initializeChain, accept, acceptJob, declineJob, ship, receive, start, saveResult, submit, shipReturn, approve, reject, returnRejected,
    chainStatusCode: chainStatus.data ?? PROCESSING_CHAIN_STATUS.NONE,
  }
}
