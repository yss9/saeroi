import { useCallback, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { processingRequestApi, type ProcessingJob, type ProcessingPaymentReady } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'

type PaymentPhase = 'IDLE' | 'PREPARING' | 'AWAITING' | 'CONFIRMING' | 'SUCCEEDED' | 'FAILED'
export const PROCESSING_PAYMENT_JOB_KEY = 'processing.paymentJobId'

export function useProcessingPayment() {
  const queryClient = useQueryClient()
  const running = useRef(false)
  const [phase, setPhase] = useState<PaymentPhase>('IDLE')
  const [ready, setReady] = useState<ProcessingPaymentReady | null>(null)
  const [paidJob, setPaidJob] = useState<ProcessingJob | null>(null)
  const [error, setError] = useState<unknown>(null)

  const start = useCallback(async (jobId: number) => {
    if (running.current) return
    running.current = true
    setPhase('PREPARING')
    setError(null)
    try {
      const prepared = await processingRequestApi.readyPayment(jobId)
      setReady(prepared)
      setPhase('AWAITING')
      if (prepared.redirectUrl) {
        try { sessionStorage.setItem(PROCESSING_PAYMENT_JOB_KEY, String(jobId)) } catch { /* 경로에도 jobId가 있다. */ }
        window.location.assign(prepared.redirectUrl)
      }
    } catch (cause) {
      setError(cause)
      setPhase('FAILED')
    } finally {
      running.current = false
    }
  }, [])

  const approve = useCallback(async (jobId: number, pgToken = 'MOCK-PG-TOKEN') => {
    if (running.current) return
    running.current = true
    setPhase('CONFIRMING')
    setError(null)
    try {
      const paid = await processingRequestApi.confirmPayment(jobId, pgToken)
      setPaidJob(paid)
      setPhase('SUCCEEDED')
      await queryClient.invalidateQueries({ queryKey: processingKeys.all })
    } catch (cause) {
      setError(cause)
      setPhase('FAILED')
    } finally {
      running.current = false
    }
  }, [queryClient])

  return {
    phase, ready, paidJob, error,
    isBusy: phase === 'PREPARING' || phase === 'CONFIRMING',
    start, approve,
  }
}
