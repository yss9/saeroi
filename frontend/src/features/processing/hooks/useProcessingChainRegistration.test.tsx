// 결제가 끝나면 브랜드가 따로 "작업 등록" 을 누르지 않는다 — 결제 확정 바로 뒤에 지갑 서명을
// 한 번 요청한다. 가공업체의 수락이 온체인 서명이라, 서명할 대상(체인의 작업)이 먼저 있어야 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, expect, test, vi } from 'vitest'
import { useProcessingChainRegistration } from './useProcessingChainRegistration.ts'

const mocks = vi.hoisted(() => ({ register: vi.fn() }))
vi.mock('../services/registerProcessingChainJob.ts', () => ({ registerProcessingChainJob: mocks.register }))

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>
}

afterEach(() => vi.resetAllMocks())

test('결제된 작업을 받으면 한 번만 온체인 등록을 요청한다', async () => {
  mocks.register.mockResolvedValue('0xabc')
  const { result, rerender } = renderHook(({ jobId }) => useProcessingChainRegistration(jobId), {
    wrapper, initialProps: { jobId: null as number | null },
  })
  expect(result.current.phase).toBe('IDLE')

  rerender({ jobId: 91 })
  await waitFor(() => expect(result.current.phase).toBe('REGISTERED'))
  rerender({ jobId: 91 })

  expect(mocks.register).toHaveBeenCalledTimes(1)
  expect(mocks.register).toHaveBeenCalledWith(91)
})

test('이미 등록된 작업이면 등록된 것으로 본다', async () => {
  mocks.register.mockRejectedValue(Object.assign(new Error('이미 등록'), { code: 'CHAIN_ALREADY_DONE' }))
  const { result } = renderHook(() => useProcessingChainRegistration(91), { wrapper })

  await waitFor(() => expect(result.current.phase).toBe('REGISTERED'))
})

test('서명을 거절하면 실패로 남기고 다시 시도할 수 있다', async () => {
  mocks.register.mockRejectedValueOnce(new Error('user rejected action')).mockResolvedValueOnce('0xabc')
  const { result } = renderHook(() => useProcessingChainRegistration(91), { wrapper })

  await waitFor(() => expect(result.current.phase).toBe('FAILED'))
  await act(() => result.current.retry())

  expect(result.current.phase).toBe('REGISTERED')
  expect(mocks.register).toHaveBeenCalledTimes(2)
})
