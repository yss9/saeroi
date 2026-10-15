import { Button, ErrorMessage } from '../../../components/common/index.ts'
import type { ProductionFlowState } from '../hooks/useProductionFlow.ts'
import { useProductionChainStatus } from '../hooks/useProductionChainStatus.ts'
import { PRODUCTION_FLOW_STEPS, type ProductionFlowStep } from '../services/productionFlow.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import { ProductionFlowProgress } from './ProductionFlowProgress.tsx'
import './production.css'

export interface ProductionChainStepsProps {
  batch: ProductionBatch
  flow: ProductionFlowState
  /** 서명할 수 있는가(지갑·계약 설정). 못 하면 그 이유 */
  blockedReason?: string
}

/**
 * 확정된 생산의 온체인 기록 — 진행 상황과 **버튼 하나.**
 *
 * 예전에는 단계마다 "진행" 버튼이 있어 생산 하나에 다섯 번을 눌러야 했다. 이제는 남은 단계를
 * 한 번에 이어서 진행한다(runProductionFlow). 서명 창은 단계마다 뜨지만 누르는 것은 한 번이다.
 *
 * **어디까지 끝났는지는 체인이 정한다.** 서버 응답으로는 3·4단계가 끝났는지 알 수 없고, 서명은
 * 됐는데 서버 보고가 실패한 경우 서버 기준으로는 영원히 "아직" 이다.
 */
export function ProductionChainSteps({ batch, flow, blockedReason }: ProductionChainStepsProps) {
  const chain = useProductionChainStatus(batch.chainBatchId)

  if (batch.status !== 'CONFIRMED') return null

  if (batch.chainBatchId !== null && chain.isPending) {
    return <p className="chain-steps__loading">생산 이력을 확인하는 중…</p>
  }
  if (batch.chainBatchId !== null && chain.isError) {
    return (
      <ErrorMessage
        error="생산 이력을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
        onRetry={() => void chain.refetch()}
      />
    )
  }

  const done = doneSteps(batch, chain.data, flow.currentStep)
  const allDone = done.size === PRODUCTION_FLOW_STEPS.length

  return (
    <div className="production-flow__area">
      <ProductionFlowProgress doneSteps={done} currentStep={flow.currentStep} running={flow.running} />

      {allDone && (
        <p className="chain-steps__complete" role="status">
          생산 이력 기록이 완료되었습니다. 제품 QR에서 소재 이력을 확인할 수 있습니다.
        </p>
      )}

      {!allDone && (
        <>
          {blockedReason && !flow.running && <p className="chain-steps__blocked">{blockedReason}</p>}
          <p className="chain-steps__description">
            남은 단계를 차례로 진행합니다. 단계마다 지갑 서명 창이 뜨고, 중간에 멈추면 다시 눌러 이어서 진행할 수 있습니다.
            소재 차감 단계부터는 되돌릴 수 없습니다.
          </p>
          <Button onClick={() => void flow.run()} disabled={Boolean(blockedReason)} loading={flow.running}>
            {batch.chainBatchId === null ? '생산 이력 기록' : '남은 기록 이어서 진행'}
          </Button>
        </>
      )}

      {flow.error && !flow.running && <ErrorMessage error={flow.error} />}
    </div>
  )
}

/** 끝난 단계. 체인 상태와, 지금 진행 중인 단계보다 앞선 단계를 함께 본다. */
function doneSteps(
  batch: ProductionBatch,
  chain: { status: string; linkedItemCount: number } | undefined,
  currentStep: ProductionFlowStep | null,
): Set<ProductionFlowStep> {
  const done = new Set<ProductionFlowStep>(['CONFIRM'])
  if (batch.chainBatchId !== null) done.add('CREATE_BATCH')
  if (chain && chain.status !== 'NONE' && chain.status !== 'DRAFT') done.add('PLAN')
  if (chain?.status === 'CONFIRMED') done.add('CONSUME')
  if (chain && chain.linkedItemCount > 0) done.add('LINK_ITEMS')
  if (currentStep) {
    for (const { step } of PRODUCTION_FLOW_STEPS) {
      if (step === currentStep) break
      done.add(step)
    }
  }
  return done
}
