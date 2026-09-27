import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { productKeys } from '../../product/api/productKeys.ts'
import { productionKeys } from '../api/productionKeys.ts'
import { runProductionFlow, type ProductionFlowStep } from '../services/productionFlow.ts'
import type { ProductionBatch } from '../types/production.types.ts'

export interface ProductionFlowState {
  run: () => Promise<void>
  running: boolean
  /** 지금 진행 중인(또는 멈춘) 단계. 시작 전이면 null */
  currentStep: ProductionFlowStep | null
  error: string | null
}

/*
  생산 확정 한 번으로 서버 확정과 서명 네 번을 차례로 진행한다(runProductionFlow).

  캐시는 **끝난 뒤에만** 비운다. 중간에 비우면 배치가 DRAFT → CONFIRMED 로 바뀌는 순간 확정
  영역이 사라지고 진행 표시도 함께 사라진다 — 사용자는 서명 창만 연달아 보고 무엇이 진행 중인지 모른다.
*/
export function useProductionFlow(batch: ProductionBatch): ProductionFlowState {
  const queryClient = useQueryClient()
  const [running, setRunning] = useState(false)
  const [currentStep, setCurrentStep] = useState<ProductionFlowStep | null>(null)
  const [error, setError] = useState<string | null>(null)
  const inFlight = useRef(false)

  const run = useCallback(async () => {
    if (inFlight.current) return
    inFlight.current = true
    setRunning(true)
    setError(null)
    try {
      await runProductionFlow(batch, setCurrentStep)
      setCurrentStep(null)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      inFlight.current = false
      setRunning(false)
      void queryClient.invalidateQueries({ queryKey: productionKeys.detail(batch.productionBatchId) })
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: ['chain', 'production-batch'] })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
      void queryClient.invalidateQueries({ queryKey: productKeys.all })
    }
  }, [batch, queryClient])

  return { run, running, currentStep, error }
}

function messageOf(cause: unknown): string {
  const error = cause as { code?: unknown; message?: unknown } | null
  // 지갑에서 취소한 것은 실패가 아니다. 다시 누르면 남은 단계부터 이어진다는 것을 알린다.
  if (error?.code === 'ACTION_REJECTED' || error?.code === 4001 || /user rejected|user denied/i.test(String(error?.message ?? ''))) {
    return '서명을 취소했어요. 다시 누르면 남은 단계부터 이어서 진행해요.'
  }
  return typeof error?.message === 'string' && error.message ? error.message : '생산 기록을 마치지 못했어요. 다시 누르면 남은 단계부터 이어서 진행해요.'
}
