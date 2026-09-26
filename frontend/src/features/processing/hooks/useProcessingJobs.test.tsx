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
  createProcessingJob: vi.fn(), confirmProcessingReceipt: vi.fn(), startProcessingOnChain: vi.fn(),
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
    getProcessingBrandLockId: contract.getProcessingBrandLockId,
    readProcessingEventValues: contract.readProcessingEventValues,
  }
})

const api = vi.hoisted(() => ({ detail: vi.fn(), chainPreparation: vi.fn(), accept: vi.fn(), list: vi.fn(), linkChain: vi.fn(), receive: vi.fn(), start: vi.fn() }))
vi.mock('../api/processingJobApi.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/processingJobApi.ts')>()
  return { ...actual, processingJobApi: { ...actual.processingJobApi, detail: api.detail, chainPreparation: api.chainPreparation, accept: api.accept, list: api.list, linkChain: api.linkChain, receive: api.receive, start: api.start } }
})

const requestApi = vi.hoisted(() => ({ detail: vi.fn() }))
vi.mock('../api/processingRequestApi.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/processingRequestApi.ts')>()
  return { ...actual, processingRequestApi: { ...actual.processingRequestApi, detail: requestApi.detail } }
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
  수령하면 곧바로 가공 결과 작성으로 간다 — "가공 시작" 을 따로 누르지 않는다. 컨트랙트는
  수령(RECEIVED)과 가공 시작(PROCESSING)을 나눠 받으므로 서명은 둘이지만, 가공업체가 누르는
  버튼은 하나다.
*/
describe('useProcessingJob 수령', () => {
  function renderShippedJob() {
    api.detail.mockResolvedValue(job({ status: 'SHIPPED' }))
    api.chainPreparation.mockResolvedValue({
      jobId: 41, chainJobId: 11, status: 'SHIPPED', brandWallet: '0x1111111111111111111111111111111111111111',
      processorWallet: '0x2222222222222222222222222222222222222222', inputTokenId: 1, inputQuantity: 1_000, processTypes: ['CLEANING'],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    function wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    }
    return renderHook(() => useProcessingJob(41), { wrapper })
  }

  it('수령 서명·기록에 이어 가공 시작 서명·기록까지 한 번에 한다', async () => {
    let chainState: number = PROCESSING_CHAIN_STATUS.SHIPPED
    contract.getProcessingChainStatus.mockImplementation(() => Promise.resolve(chainState))
    contract.confirmProcessingReceipt.mockImplementation(() => { chainState = PROCESSING_CHAIN_STATUS.RECEIVED; return Promise.resolve({ wait: () => Promise.resolve({ status: 1, hash: '0xabc', blockNumber: 1 }) }) })
    contract.startProcessingOnChain.mockImplementation(() => { chainState = PROCESSING_CHAIN_STATUS.PROCESSING; return Promise.resolve(txReceipt()) })
    api.receive.mockResolvedValue(job({ status: 'RECEIVED' }))
    api.start.mockResolvedValue(job({ status: 'PROCESSING' }))
    const { result } = renderShippedJob()
    await waitFor(() => expect(result.current.chainPreparation.isSuccess).toBe(true))

    await result.current.receive.mutateAsync()

    expect(contract.confirmProcessingReceipt).toHaveBeenCalledWith(11)
    expect(api.receive).toHaveBeenCalledWith(41, { txHash: '0xabc' })
    expect(contract.startProcessingOnChain).toHaveBeenCalledWith(11)
    expect(api.start).toHaveBeenCalledWith(41)
    // 기록이 서명보다 앞서면 안 된다 — 서버는 수령 영수증을 읽어 소재 잔액을 옮긴다.
    expect(api.receive.mock.invocationCallOrder[0]).toBeLessThan(contract.startProcessingOnChain.mock.invocationCallOrder[0])
  })

  it('가공 시작 서명이 끊겨도 수령 기록은 남는다', async () => {
    contract.getProcessingChainStatus.mockResolvedValueOnce(PROCESSING_CHAIN_STATUS.SHIPPED).mockResolvedValue(PROCESSING_CHAIN_STATUS.RECEIVED)
    contract.confirmProcessingReceipt.mockResolvedValue(txReceipt())
    contract.startProcessingOnChain.mockRejectedValue(new Error('user rejected action'))
    api.receive.mockResolvedValue(job({ status: 'RECEIVED' }))
    const { result } = renderShippedJob()
    await waitFor(() => expect(result.current.chainPreparation.isSuccess).toBe(true))

    await expect(result.current.receive.mutateAsync()).rejects.toThrow('user rejected action')

    expect(api.receive).toHaveBeenCalledOnce()
    expect(api.start).not.toHaveBeenCalled()
  })
})
