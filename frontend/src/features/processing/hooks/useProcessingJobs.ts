import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ContractTransactionResponse } from 'ethers'
import { processingJobApi, type ProcessingChainPreparation, type ProcessingSignatureAction, type ResultDraftInput } from '../api/processingJobApi.ts'
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
  findUnlinkedProcessingJob,
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
  type UnlinkedProcessingJob,
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

/** 온체인에 새 가공 작업을 만들고, 영수증에서 번호를 꺼낸다. */
async function registerChainJob(prep: ProcessingChainPreparation): Promise<UnlinkedProcessingJob> {
  const receipt = await confirmed(createProcessingJob(
    prep.processorWallet,
    prep.inputTokenId,
    prep.inputQuantity,
    prep.processTypes,
  ))
  const values = readProcessingEventValues(receipt, 'ProcessingJobCreated', 'jobId')
  if (values.length !== 1) throw new Error('온체인 작업 번호를 확인하지 못했습니다.')
  return { onChainId: Number(values[0]), txHash: receipt.hash, blockNumber: receipt.blockNumber }
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

  /*
    브랜드의 온체인 등록도 "서명 한 번 + 서버 기록 한 번" 이다 — 그리고 여기서 끊기는
    쪽이 수락보다 훨씬 나쁘다.

    `createJob` 이 확정된 뒤 `POST /processing-jobs/{id}/chain` 이 실패하면(네트워크
    끊김, 500, 탭 닫힘) 작업은 여전히 PAID · chainJobId=null 이라 등록 패널이 그대로
    돌아온다. 그대로 다시 누르면 같은 소재가 **두 번째로** 잠기는데, 그 두 번째 온체인
    작업은 DB 어디에서도 가리킬 수 없다 — `cancelJob` 은 chainJobId 를 받는데 그 번호가
    어디에도 적혀 있지 않다. 잠긴 수량은 영영 돌아오지 않는다.

    그래서 accept 와 같은 원칙을 쓴다 — **체인이 "이미 했다" 고 말할 때만 서명을 접는다.**
    등록에는 읽어 볼 상태값이 없으므로(연결 전이라 번호 자체를 모른다) 대신
    `ProcessingJobCreated` 로그에서 "내가 이 조건으로 만들었는데 아직 아무 작업에도
    연결되지 않은" 번호를 찾는다. 찾지 못하면 그대로 서명한다 — 고아를 못 보고
    지나쳐 한 번 더 잠그는 일은 남지만(findUnlinkedProcessingJob 주석 참고),
    "확실하지 않으면 서명한다" 쪽이 서명 없이 근거 없는 기록을 만드는 것보다 낫다.
  */
  const initializeChain = useMutation({
    mutationFn: async () => {
      const prep = requirePreparation()
      // 다른 작업이 이미 가져간 번호는 후보가 아니다 — 그 잠금에는 주인이 있다.
      const linkedChainJobIds = (await processingJobApi.list())
        .flatMap(item => (item.chainJobId === null ? [] : [item.chainJobId]))
      const orphan = await findUnlinkedProcessingJob({
        brandWallet: prep.brandWallet,
        processorWallet: prep.processorWallet,
        tokenId: prep.inputTokenId,
        amount: prep.inputQuantity,
        processTypes: prep.processTypes,
        linkedChainJobIds,
      })
      const registered = orphan ?? await registerChainJob(prep)
      const chainLockId = await getProcessingBrandLockId(registered.onChainId)
      return processingJobApi.linkChain(jobId, {
        onChainId: registered.onChainId,
        chainLockId,
        txHash: registered.txHash,
        blockNumber: registered.blockNumber,
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
    먼저 읽어 이미 ACCEPTED 일 때만 서명을 건너뛴다.

    **"이미 끝났다"는 쪽으로만 건너뛴다.** `NONE`(체인 리셋·재배포로 chainJobId 가
    낡았거나 잘못된 네트워크), `CANCELED`, `DECLINED`, 읽기 실패로 나온 `NaN` 까지
    ACCEPTED 가 아닌 모든 값은 그대로 서명을 시도한다 — 서명 없이 서버에만 수락을
    적으면 이 기획의 전제(서명이 곧 수락이다)가 깨진다. 예상 밖의 상태는 컨트랙트가
    시끄럽게 되돌리는 편이, 조용히 근거 없는 수락을 만드는 것보다 낫다.
  */
  const accept = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      const chainState = await getProcessingChainStatus(id)
      if (chainState !== PROCESSING_CHAIN_STATUS.ACCEPTED) await confirmed(acceptProcessingJob(id))
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
