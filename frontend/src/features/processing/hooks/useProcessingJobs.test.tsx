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
  createProcessingJob: vi.fn(),
  getProcessingBrandLockId: vi.fn(), readProcessingEventValues: vi.fn(),
}))
vi.mock('../services/processingContractService.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/processingContractService.ts')>()
  return {
    ...actual,
    acceptProcessingJob: contract.acceptProcessingJob,
    getProcessingChainStatus: contract.getProcessingChainStatus,
    createProcessingJob: contract.createProcessingJob,
    getProcessingBrandLockId: contract.getProcessingBrandLockId,
    readProcessingEventValues: contract.readProcessingEventValues,
  }
})

const api = vi.hoisted(() => ({ detail: vi.fn(), chainPreparation: vi.fn(), accept: vi.fn(), list: vi.fn(), linkChain: vi.fn() }))
vi.mock('../api/processingJobApi.ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/processingJobApi.ts')>()
  return { ...actual, processingJobApi: { ...actual.processingJobApi, detail: api.detail, chainPreparation: api.chainPreparation, accept: api.accept, list: api.list, linkChain: api.linkChain } }
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
