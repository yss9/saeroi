// 가공사 수락은 온체인 서명 한 번 + 서버 기록 한 번이다.
//
// **서명과 기록 사이에서 끊기면 재시도가 두 번째 서명이 되면 안 된다.** 서명은
// 성공했는데 이어지는 PATCH 가 실패하면(네트워크 끊김, 500, 탭 닫힘) 작업은 여전히
// PAID 로 남고 가공사는 같은 버튼을 다시 본다 — 그대로 다시 서명하면 컨트랙트가
// `REQUESTED` 를 요구하는데 이미 `ACCEPTED` 라서 되돌린다(revert). 그래서 accept 는
// 서명 전에 매번 온체인 상태를 먼저 읽는다.
//
// 이 화면 목(mock)으로는 이 재시도 경로를 볼 수 없다 — ProcessingJobDetail.test.tsx
// 는 훅 전체를 vi.mock 으로 갈아 끼우기 때문에 mutationFn 내부 분기가 실행되지 않는다.
// 그래서 여기서는 훅을 직접 렌더링한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProcessingJobDetail } from '../api/processingJobApi.ts'
import { PROCESSING_CHAIN_STATUS } from '../services/processingContractService.ts'
import { useProcessingJob } from './useProcessingJobs.ts'

const contract = vi.hoisted(() => ({
  acceptProcessingJob: vi.fn(), getProcessingChainStatus: vi.fn(),
  createProcessingJob: vi.fn(), confirmProcessingReceipt: vi.fn(), startProcessingOnChain: vi.fn(), submitProcessingResultOnChain: vi.fn(),
  getProcessingBrandLockId: vi.fn(), readProcessingEventValues: vi.fn(),
}))
vi.mock('../services/processingContractService.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/processingContractService.ts')>()
  return {
    ...actual,
    acceptProcessingJob: contract.acceptProcessingJob,
    getProcessingChainStatus: contract.getProcessingChainStatus,
    createProcessingJob: contract.createProcessingJob,
    confirmProcessingReceipt: contract.confirmProcessingReceipt,
    startProcessingOnChain: contract.startProcessingOnChain,
    submitProcessingResultOnChain: contract.submitProcessingResultOnChain,
    getProcessingBrandLockId: contract.getProcessingBrandLockId,
    readProcessingEventValues: contract.readProcessingEventValues,
  }
})

const registration = vi.hoisted(() => ({ registerProcessingChainJob: vi.fn() }))
vi.mock('../services/registerProcessingChainJob.ts', () => registration)

const api = vi.hoisted(() => ({ detail: vi.fn(), chainPreparation: vi.fn(), accept: vi.fn(), list: vi.fn(), linkChain: vi.fn(), receive: vi.fn(), start: vi.fn(), challenge: vi.fn(), submitResult: vi.fn(), saveResult: vi.fn() }))
vi.mock('../api/processingJobApi.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/processingJobApi.ts')>()
  return { ...actual, processingJobApi: { ...actual.processingJobApi, detail: api.detail, chainPreparation: api.chainPreparation, accept: api.accept, list: api.list, linkChain: api.linkChain, receive: api.receive, start: api.start, challenge: api.challenge, submitResult: api.submitResult, saveResult: api.saveResult } }
})

const requestApi = vi.hoisted(() => ({ detail: vi.fn() }))
vi.mock('../api/processingRequestApi.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/processingRequestApi.ts')>()
  return { ...actual, processingRequestApi: { ...actual.processingRequestApi, detail: requestApi.detail } }
})

const wallet = vi.hoisted(() => ({ signMessage: vi.fn() }))
vi.mock('../../wallet/services/walletService.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../wallet/services/walletService.ts')>()
  return { ...actual, signMessage: wallet.signMessage }
})

const evidenceApi = vi.hoisted(() => ({ getEvidence: vi.fn() }))
vi.mock('../../evidence/api/getEvidence.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../evidence/api/getEvidence.ts')>()
  return { ...actual, getEvidence: evidenceApi.getEvidence }
})

beforeEach(() => {
  // accept 재시도 검증에는 요청 상세·증빙 내용이 필요 없다 — react-query 가
  // undefined 를 캐시값으로 받아들이지 않으므로 빈 값으로 채운다. resetAllMocks 가
  // 매 테스트마다 구현을 지우므로 여기서 다시 심는다.
  requestApi.detail.mockResolvedValue({})
  evidenceApi.getEvidence.mockResolvedValue({ files: [], summary: [] })
})

function job(overrides: Partial<ProcessingJobDetail> = {}): ProcessingJobDetail {
  return {
    jobId: 41, jobCode: 'JOB-041', requestId: 3, brandId: 2, processorId: 8,
    fee: 10_000, paymentAmount: 10_000, status: 'PAID', paymentExpiresAt: '2026-09-15T10:15:00', createdAt: '2026-09-15T10:00:00',
    chainJobId: 11, inputMaterialId: 11, inputQuantity: 1_000, lossQuantity: 0, resultRevision: 0,
    carrier: null, trackingNumber: null, returnCarrier: null, returnTrackingNumber: null, returnShippingEvidenceHash: null,
    rejectionReason: null, shippedAt: null, receivedAt: null, startedAt: null, resultSubmittedAt: null, rejectedAt: null,
    returnShippedAt: null, completedAt: null, acceptExpiresAt: null, acceptedAt: null, declinedAt: null, declineReason: null,
    refundedAt: null, chainReleaseRequired: false, outputs: [],
    ...overrides,
  }
}

function txReceipt() {
  return { hash: '0xabc', blockNumber: 1, wait: () => Promise.resolve({ status: 1 }) }
}

afterEach(() => vi.resetAllMocks())

describe('useProcessingJob accept 재시도', () => {
  it('PATCH 가 실패한 뒤 재시도하면, 이미 온체인 수락된 상태이므로 서명 없이 PATCH만 다시 부른다', async () => {
    api.detail.mockResolvedValue(job())
    api.chainPreparation.mockResolvedValue({
      jobId: 41, chainJobId: 11, status: 'PAID', brandWallet: '0x1111111111111111111111111111111111111111',
      processorWallet: '0x2222222222222222222222222222222222222222', inputTokenId: 1, inputQuantity: 1_000, processTypes: ['CLEANING'],
    })
    contract.acceptProcessingJob.mockResolvedValue(txReceipt())
    // 매 호출마다 "지금" 체인 상태를 읽는다 — 테스트가 그 값을 직접 바꿔 재시도 시점을 흉내 낸다.
    let chainState: number = PROCESSING_CHAIN_STATUS.REQUESTED
    contract.getProcessingChainStatus.mockImplementation(() => Promise.resolve(chainState))

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    function wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    }
    const { result } = renderHook(() => useProcessingJob(41), { wrapper })
    await waitFor(() => expect(result.current.chainPreparation.isSuccess).toBe(true))

    // 1차 시도 — 서명은 성공하지만 서버 기록이 실패한다(네트워크 끊김 등).
    api.accept.mockRejectedValueOnce(new Error('네트워크 오류'))
    result.current.accept.mutate()
    await waitFor(() => expect(result.current.accept.isError).toBe(true))

    expect(contract.acceptProcessingJob).toHaveBeenCalledOnce()
    expect(api.accept).toHaveBeenCalledOnce()

    // 재시도 시점 — 서명은 이미 체인에 반영돼 있다(ACCEPTED). 이번엔 서버 기록이 성공한다.
    chainState = PROCESSING_CHAIN_STATUS.ACCEPTED
    api.accept.mockResolvedValueOnce(job({ status: 'ACCEPTED' }))
    result.current.accept.mutate()
    await waitFor(() => expect(result.current.accept.isSuccess).toBe(true))

    // 두 번째 서명은 없다 — 있었다면 컨트랙트가 REQUESTED 를 요구하는데 이미
    // ACCEPTED 라서 되돌아갔을 것이다.
    expect(contract.acceptProcessingJob).toHaveBeenCalledOnce()
    expect(api.accept).toHaveBeenCalledTimes(2)
  })

  it('REQUESTED도 ACCEPTED도 아닌 상태(NONE 등 예상 밖의 값)에서는 서명을 그대로 시도한다', async () => {
    // 체인 리셋·재배포로 chainJobId 가 낡았거나, 잘못된 네트워크거나, 읽기가 이상값을
    // 내놓은 경우를 흉내 낸다 — "이미 ACCEPTED" 가 아니면 무조건 서명을 시도해야
    // 서명 없이 서버에만 수락이 적히는 일이 없다.
    api.detail.mockResolvedValue(job())
    api.chainPreparation.mockResolvedValue({
      jobId: 41, chainJobId: 11, status: 'PAID', brandWallet: '0x1111111111111111111111111111111111111111',
      processorWallet: '0x2222222222222222222222222222222222222222', inputTokenId: 1, inputQuantity: 1_000, processTypes: ['CLEANING'],
    })
    contract.acceptProcessingJob.mockResolvedValue(txReceipt())
    contract.getProcessingChainStatus.mockResolvedValue(PROCESSING_CHAIN_STATUS.NONE)
    api.accept.mockResolvedValue(job({ status: 'ACCEPTED' }))

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    function wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    }
    const { result } = renderHook(() => useProcessingJob(41), { wrapper })
    await waitFor(() => expect(result.current.chainPreparation.isSuccess).toBe(true))

    result.current.accept.mutate()
    await waitFor(() => expect(result.current.accept.isSuccess).toBe(true))

    expect(contract.acceptProcessingJob).toHaveBeenCalledOnce()
    expect(api.accept).toHaveBeenCalledOnce()
  })
})

/*
  **가공 시작 버튼은 없다.** 수령하면 곧바로 가공 결과 작성으로 간다. 서명은 수령 한 번이고,
  서버는 이어서 가공 중(PROCESSING)으로 기록한다 — 서버의 가공 시작은 서명을 요구하지 않는다.

  컨트랙트는 결과 제출 전에 PROCESSING 을 요구하므로 체인의 startProcessing 은 없앨 수 없다.
  대신 결과 제출 서명 바로 앞에서, 체인이 아직 수령(RECEIVED) 상태일 때만 함께 보낸다.
*/
function renderJob(status: ProcessingJobDetail['status'], overrides: Partial<ProcessingJobDetail> = {}) {
  api.detail.mockResolvedValue(job({ status, ...overrides }))
  api.chainPreparation.mockResolvedValue({
    jobId: 41, chainJobId: 11, status, brandWallet: '0x1111111111111111111111111111111111111111',
    processorWallet: '0x2222222222222222222222222222222222222222', inputTokenId: 1, inputQuantity: 1_000, processTypes: ['CLEANING'],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
  return renderHook(() => useProcessingJob(41), { wrapper })
}

describe('useProcessingJob 수령', () => {
  it('수령 서명 한 번 뒤에 서버가 곧바로 가공 중으로 기록한다', async () => {
    contract.getProcessingChainStatus.mockResolvedValue(PROCESSING_CHAIN_STATUS.SHIPPED)
    contract.confirmProcessingReceipt.mockResolvedValue({ wait: () => Promise.resolve({ status: 1, hash: '0xabc', blockNumber: 1 }) })
    api.receive.mockResolvedValue(job({ status: 'RECEIVED' }))
    api.start.mockResolvedValue(job({ status: 'PROCESSING' }))
    const { result } = renderJob('SHIPPED')
    await waitFor(() => expect(result.current.chainPreparation.isSuccess).toBe(true))

    await result.current.receive.mutateAsync()

    expect(contract.confirmProcessingReceipt).toHaveBeenCalledWith(11)
    expect(api.receive).toHaveBeenCalledWith(41, { txHash: '0xabc' })
    expect(api.start).toHaveBeenCalledWith(41)
    expect(api.receive.mock.invocationCallOrder[0]).toBeLessThan(api.start.mock.invocationCallOrder[0])
    // 수령할 때 두 번째 서명 창을 띄우지 않는다.
    expect(contract.startProcessingOnChain).not.toHaveBeenCalled()
  })

  it('가공 시작 기록은 서명 없이 서버만 부른다', async () => {
    api.start.mockResolvedValue(job({ status: 'PROCESSING' }))
    const { result } = renderJob('RECEIVED')
    await waitFor(() => expect(result.current.chainPreparation.isSuccess).toBe(true))

    await result.current.start.mutateAsync()

    expect(api.start).toHaveBeenCalledWith(41)
    expect(contract.startProcessingOnChain).not.toHaveBeenCalled()
  })
})

/*
  작업 상세의 등록 재시도는 결제 직후 자동 등록과 같은 함수를 쓴다. 화면에 캐시된 준비 정보를
  읽던 예전 방식은 그 조회가 끝나기 전에 누르면 "온체인 작업 정보를 불러오지 못했습니다" 로
  실패했다 — 간헐적 실패의 한 원인이었다. 등록 함수는 준비 정보를 서버에서 새로 읽는다.
*/
describe('useProcessingJob 가공 작업 등록', () => {
  function renderPaidJob() {
    api.detail.mockResolvedValue(job({ status: 'PAID', chainJobId: null }))
    // 첫 준비 조회는 늦게 끝난다 — 버튼을 그보다 먼저 누르는 상황이다.
    let finishFirst: (value: unknown) => void = () => {}
    api.chainPreparation
      .mockReturnValueOnce(new Promise(resolve => { finishFirst = resolve }))
      .mockResolvedValue({ jobId: 41, chainJobId: 11, status: 'PAID' })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    function wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    }
    const rendered = renderHook(() => useProcessingJob(41), { wrapper })
    return { ...rendered, finishFirst: () => finishFirst({ jobId: 41, chainJobId: null, status: 'PAID' }) }
  }

  it('준비 정보를 다 불러오기 전에 눌러도 등록한다', async () => {
    registration.registerProcessingChainJob.mockResolvedValue('0xabc')
    const { result, finishFirst } = renderPaidJob()
    await waitFor(() => expect(result.current.detail.isSuccess).toBe(true))
    expect(result.current.chainPreparation.isSuccess).toBe(false)

    result.current.initializeChain.mutate()
    await waitFor(() => expect(registration.registerProcessingChainJob).toHaveBeenCalledWith(41))
    finishFirst()

    await waitFor(() => expect(result.current.initializeChain.isSuccess).toBe(true))
  })

  it('이미 등록돼 있으면 성공으로 본다', async () => {
    registration.registerProcessingChainJob.mockRejectedValue(Object.assign(new Error('이미 등록'), { code: 'CHAIN_ALREADY_DONE' }))
    const { result, finishFirst } = renderPaidJob()
    await waitFor(() => expect(result.current.detail.isSuccess).toBe(true))

    result.current.initializeChain.mutate()
    await waitFor(() => expect(registration.registerProcessingChainJob).toHaveBeenCalled())
    finishFirst()

    await waitFor(() => expect(result.current.initializeChain.isSuccess).toBe(true))
  })
})

describe('useProcessingJob 결과 제출', () => {
  const INPUT = { outputs: [{ name: 'denim', grade: 'A' as const, gradeReason: '양호', quantity: 900 }] }

  function prepareSubmit(chainState: number) {
    const output = { outputId: 5, materialId: null, materialType: 'DENIM', grade: 'A', gradeReason: '양호', name: 'denim', quantity: 900 }
    contract.getProcessingChainStatus.mockResolvedValue(chainState)
    contract.startProcessingOnChain.mockResolvedValue(txReceipt())
    contract.submitProcessingResultOnChain.mockResolvedValue(txReceipt())
    evidenceApi.getEvidence.mockResolvedValue({ files: [{ fileHash: '0x' + 'e'.repeat(64) }], summary: [] })
    api.challenge.mockResolvedValue({ challengeId: 3, message: 'sign me' })
    wallet.signMessage.mockResolvedValue('0xsig')
    api.submitResult.mockResolvedValue(job({ status: 'RESULT_SUBMITTED' }))
    // 제출은 먼저 저장하고, 그 응답(번호가 매겨진 산출물)으로 서명한다.
    api.saveResult.mockResolvedValue(job({ status: 'PROCESSING', resultRevision: 1, outputs: [output] as ProcessingJobDetail['outputs'] }))
    return renderJob('PROCESSING')
  }

  it('체인이 아직 수령 상태면 결과 제출 서명 앞에서 가공 시작을 함께 보낸다', async () => {
    const { result } = prepareSubmit(PROCESSING_CHAIN_STATUS.RECEIVED)
    await waitFor(() => expect(result.current.detail.isSuccess && result.current.chainPreparation.isSuccess).toBe(true))

    await result.current.submit.mutateAsync(INPUT)

    expect(contract.startProcessingOnChain).toHaveBeenCalledWith(11)
    expect(contract.startProcessingOnChain.mock.invocationCallOrder[0])
      .toBeLessThan(contract.submitProcessingResultOnChain.mock.invocationCallOrder[0])
    expect(api.submitResult).toHaveBeenCalledWith(41, 3, '0xsig')
  })

  it('체인이 이미 가공 중이면 가공 시작을 다시 보내지 않는다', async () => {
    const { result } = prepareSubmit(PROCESSING_CHAIN_STATUS.PROCESSING)
    await waitFor(() => expect(result.current.detail.isSuccess && result.current.chainPreparation.isSuccess).toBe(true))

    await result.current.submit.mutateAsync(INPUT)

    expect(contract.startProcessingOnChain).not.toHaveBeenCalled()
    expect(contract.submitProcessingResultOnChain).toHaveBeenCalledOnce()
  })
})

describe('useProcessingJob 결과 제출은 저장부터 한다', () => {
  it('입력한 결과를 저장한 뒤 그 응답의 산출물로 체인에 제출한다', async () => {
    const saved = { outputId: 77, materialId: null, materialType: 'DENIM', grade: 'B', gradeReason: '얼룩', name: 'denim', quantity: 650 }
    contract.getProcessingChainStatus.mockResolvedValue(PROCESSING_CHAIN_STATUS.PROCESSING)
    contract.submitProcessingResultOnChain.mockResolvedValue(txReceipt())
    evidenceApi.getEvidence.mockResolvedValue({ files: [{ fileHash: '0x' + 'e'.repeat(64) }], summary: [] })
    api.challenge.mockResolvedValue({ challengeId: 3, message: 'sign me' })
    wallet.signMessage.mockResolvedValue('0xsig')
    api.saveResult.mockResolvedValue(job({ status: 'PROCESSING', resultRevision: 2, outputs: [saved] as ProcessingJobDetail['outputs'] }))
    api.submitResult.mockResolvedValue(job({ status: 'RESULT_SUBMITTED' }))
    const { result } = renderJob('PROCESSING')
    await waitFor(() => expect(result.current.chainPreparation.isSuccess).toBe(true))
    const input = { outputs: [{ name: 'denim', grade: 'B' as const, gradeReason: '얼룩', quantity: 650 }] }

    await result.current.submit.mutateAsync(input)

    expect(api.saveResult).toHaveBeenCalledWith(41, input)
    // 서명 요청은 저장이 끝난 뒤다 — 서버가 저장된 결과로 서명 문구를 만든다.
    expect(api.saveResult.mock.invocationCallOrder[0]).toBeLessThan(api.challenge.mock.invocationCallOrder[0])
    expect(contract.submitProcessingResultOnChain).toHaveBeenCalledWith(
      11, [expect.objectContaining({ amount: 650, grade: 'B', metadataUri: 'saeroi://processing/41/output/77' })], expect.any(String))
  })
})

