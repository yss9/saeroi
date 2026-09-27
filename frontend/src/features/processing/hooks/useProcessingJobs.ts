import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ContractTransactionResponse } from 'ethers'
import { processingJobApi, type ProcessingSignatureAction, type ResultDraftInput } from '../api/processingJobApi.ts'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { chainAlreadyPassed } from '../services/processingChainProgress.ts'
import {
  createOrResumeProcessingChainJob,
  forgetProcessingChainCreate,
} from '../services/processingChainJobCreation.ts'
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
    if (!chainPreparation.data) throw new Error('작업 기록 정보를 불러오지 못했습니다.')
    return chainPreparation.data
  }
  const requireChainJob = () => {
    const id = requirePreparation().chainJobId
    if (!id) throw new Error('먼저 가공 작업을 등록해 주세요.')
    return id
  }

  /*
    작업 등록. **두 번 만들지 않는다.** 체인에 작업을 만든 뒤 서버 연결(linkChain)이 실패하고 다시
    누르면, 서버는 아직 체인 작업을 모르니 한 번 더 만들게 되고 앞의 작업은 소재를 잠근 채
    버려진다. 보낸 거래를 기억해 두었다가 다시 누르면 그 거래에서 이어 간다
    (processingChainJobCreation).
  */
  const initializeChain = useMutation({
    mutationFn: async () => {
      const prep = requirePreparation()
      const receipt = await createOrResumeProcessingChainJob(jobId, () => createProcessingJob(
        prep.processorWallet,
        prep.inputTokenId,
        prep.inputQuantity,
        prep.processTypes,
      ))
      const values = readProcessingEventValues(receipt, 'ProcessingJobCreated', 'jobId')
      if (values.length !== 1) throw new Error('가공 작업 기록을 확인하지 못했습니다.')
      const onChainId = Number(values[0])
      const chainLockId = await getProcessingBrandLockId(onChainId)
      const linked = await processingJobApi.linkChain(jobId, {
        onChainId,
        chainLockId,
        txHash: receipt.hash,
        blockNumber: receipt.blockNumber,
      })
      forgetProcessingChainCreate(jobId)
      return linked
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
    수령. **이미 서명이 남았으면 서명을 건너뛰고 해시 없이 보낸다.**

    서버는 수령 서명의 영수증을 읽어 소재 잔액을 브랜드에서 업체로 옮긴다
    (ProcessingChainService.receive). 서명이 체인에 들어간 뒤 서버 호출이 실패하면 화면은 해시를
    잃고, 다시 서명하려 하면 체인이 이미 수령된 작업이라 되돌린다. 그래서 체인이 이미 수령을
    지났으면 해시 없이 보내고, 서버가 체인에서 그 서명 거래를 찾아 똑같이 검증한 뒤 기록한다.

    **수령하면 곧바로 가공 중이 된다 — 가공 시작 버튼은 없다.** 서명은 수령 한 번이고, 서버는
    이어서 가공 중(PROCESSING)으로 기록한다. 서버의 가공 시작은 서명을 요구하지 않는다.
    컨트랙트는 결과 제출 전에 PROCESSING 을 요구하므로 체인의 startProcessing 은 결과 제출
    서명 바로 앞에서 함께 보낸다(submit). 서버 기록만 끊겨 RECEIVED 로 남으면 작업 상세가
    서명 없이 start 를 다시 부른다.
  */
  async function receiveOnly(id: number) {
    if (await chainAlreadyPassed(id, 'RECEIVED')) {
      return processingJobApi.receive(jobId, {})
    }
    const receipt = await confirmed(confirmProcessingReceipt(id))
    return processingJobApi.receive(jobId, {
      txHash: receipt.hash,
    })
  }
  const receive = useMutation({
    mutationFn: async () => {
      await receiveOnly(requireChainJob())
      return processingJobApi.start(jobId)
    },
    onSuccess: refresh,
    // 서버의 가공 시작 기록만 실패해도 수령은 기록됐다 — 화면이 RECEIVED 로 바뀌어야 이어 받는다.
    onError: refresh,
  })
  /*
    가공 시작 — **서버 기록만 한다.** 수령이 곧바로 부르고, 그 기록만 끊겨 RECEIVED 로 남은
    작업은 작업 상세가 서명 없이 다시 부른다. 체인의 startProcessing 은 결과 제출 앞에서
    보낸다(submit) — 예전에 여기서 서명과 기록이 갈라져 체인은 PROCESSING, DB 는 RECEIVED 로
    멈춘 일이 있었는데(체인 작업 18·19), 서명을 떼어 내 그 틈이 없어졌다.
  */
  const start = useMutation({
    mutationFn: () => processingJobApi.start(jobId),
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
      // 체인의 가공 시작은 수령 때 보내지 않는다(receive 참고). 컨트랙트가 결과 제출 전에
      // PROCESSING 을 요구하므로, 아직 수령 상태일 때만 여기서 먼저 보낸다.
      if (!(await chainAlreadyPassed(id, 'PROCESSING'))) {
        await confirmed(startProcessingOnChain(id))
      }
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
  /*
    결과 승인. 수령과 같다 — 이미 승인 서명이 남았으면 해시 없이 보내 서버가 체인에서 찾게 한다.
    승인은 새 소재를 발행하는 자리라 그 번호가 영수증에만 있어, 서버가 거래를 찾아야 발행할 수 있다.
  */
  const approve = useMutation({
    mutationFn: async () => {
      const id = requireChainJob()
      if (await chainAlreadyPassed(id, 'CONFIRMED')) {
        return processingJobApi.receiveReturn(jobId, {})
      }
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
    브랜드 지갑만 할 수 있다 — cancelProcessingJob 을 그대로 쓴다.
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
