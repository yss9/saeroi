import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { registerProcessingChainJob } from '../services/registerProcessingChainJob.ts'

export type ChainRegistrationPhase = 'IDLE' | 'REGISTERING' | 'REGISTERED' | 'FAILED'

/*
  결제가 끝난 작업을 **곧바로** 체인에 올린다. 가공업체의 수락이 온체인 서명이라 서명할 대상이
  먼저 체인에 있어야 하는데, 그 등록은 브랜드 지갑만 할 수 있다. 예전에는 브랜드가 작업 상세에서
  "작업 등록" 을 따로 눌러야 했고, 브랜드 입장에서는 결제한 일을 한 번 더 수락하는 것처럼 보였다.
  그래서 결제 확정 바로 뒤에 같은 흐름에서 서명을 요청한다.

  서명을 거절하거나 창을 닫으면 결제는 그대로 두고 실패로 남긴다 — 다시 시도는 이 화면에서도,
  작업 상세의 등록 패널에서도 할 수 있다. 두 번 눌러도 소재가 두 번 잠기지 않는 것은
  registerProcessingChainJob 이 보장한다.
*/
export function useProcessingChainRegistration(paidJobId: number | null) {
  const queryClient = useQueryClient()
  const [phase, setPhase] = useState<ChainRegistrationPhase>('IDLE')
  const [error, setError] = useState<unknown>(null)
  const started = useRef<number | null>(null)

  const register = useCallback(async (jobId: number) => {
    setPhase('REGISTERING')
    setError(null)
    try {
      await registerProcessingChainJob(jobId)
      setPhase('REGISTERED')
    } catch (cause) {
      if ((cause as { code?: string } | null)?.code === 'CHAIN_ALREADY_DONE') {
        setPhase('REGISTERED')
      } else {
        setError(cause)
        setPhase('FAILED')
      }
    } finally {
      await queryClient.invalidateQueries({ queryKey: ['processing-jobs', jobId] })
    }
  }, [queryClient])

  useEffect(() => {
    if (paidJobId === null || started.current === paidJobId) return
    started.current = paidJobId
    void register(paidJobId)
  }, [paidJobId, register])

  const retry = useCallback(async () => {
    if (paidJobId !== null) await register(paidJobId)
  }, [paidJobId, register])

  return { phase, error, retry }
}
