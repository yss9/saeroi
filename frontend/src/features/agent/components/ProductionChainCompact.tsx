import { isContractConfigured } from '../../../config/contracts.ts'
import { env } from '../../../config/env.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useCreateChainBatch } from '../../production/hooks/useCreateChainBatch.ts'
import {
  useConfirmProductionOnChain,
  useLinkProductionItems,
  useSubmitProductionPlan,
} from '../../production/hooks/useProductionChainSteps.ts'
import { useProductionChainStatus } from '../../production/hooks/useProductionChainStatus.ts'
import { useProductionItems } from '../../production/hooks/useProductionItems.ts'
import type { ProductionBatch } from '../../production/types/production.types.ts'

/**
 * 생산 온체인 기록(서명 4번)을 **줄여 그린다.** 대화 카드 전용.
 *
 * 예전에는 생산 화면의 진행표(`ProductionChainPanel`)를 그대로 품었다. 단계마다 긴 설명과 진행 버튼,
 * 거래 상태 상자가 붙어 오른쪽 패널에서는 글자가 세로로 쌓였고, 담당자는 "글이 너무 많다, 필요한
 * 버튼만" 이라고 했다. 대화에서 담당자가 할 일은 **지금 단계의 서명 하나**다.
 *
 * **서명 흐름은 다시 만들지 않는다.** 단계마다 서명하고 서버에 보고하는 일은 생산 화면과 같은 훅이
 * 한다. 여기서 정하는 것은 어느 단계가 끝났고 지금 무엇을 누를 수 있는지뿐이고, 그 판정도 생산 화면과
 * 같은 기준을 쓴다 — **다음 단계는 서버가 아니라 체인 상태로 정한다**(`ProductionChainSteps` 참고).
 */
export function ProductionChainCompact({ batch }: { batch: ProductionBatch }) {
  const wallet = useWallet()
  const create = useCreateChainBatch(batch.productionBatchId, batch.producedQuantity)
  const chain = useProductionChainStatus(batch.chainBatchId)
  const items = useProductionItems(batch.productionBatchId, batch.status === 'CONFIRMED')
  const plan = useSubmitProductionPlan(batch)
  const confirm = useConfirmProductionOnChain(batch)
  const link = useLinkProductionItems(batch, items.data ?? [])

  const onChain = batch.chainBatchId !== null
  const status = chain.data?.status
  const linked = chain.data?.linkedItemCount ?? 0
  const itemCount = items.data?.length ?? 0

  // 1단계를 누를 수 있는가 — 생산 화면의 패널과 같은 조건이다(ProductionChainPanel).
  const walletMismatch =
    wallet.address !== null && wallet.address.toLowerCase() !== batch.producerWallet.toLowerCase()
  const firstReady =
    batch.status === 'CONFIRMED' &&
    isContractConfigured('productionRegistry') &&
    wallet.address !== null &&
    !walletMismatch

  const steps: CompactStep[] = [
    {
      label: '생산 이력 기록',
      done: onChain,
      ready: firstReady,
      blocked: firstBlocked(batch.status, wallet.address, walletMismatch, batch.producerWallet),
      run: async () => { await create.create() },
      busy: create.isBusy,
      phase: create.status,
      error: create.isRejected ? null : create.error,
      rejected: create.isRejected,
    },
    {
      label: '사용할 소재 확정',
      done: onChain && status !== undefined && status !== 'DRAFT',
      ready: status === 'DRAFT',
      run: plan.run,
      busy: plan.isBusy,
      phase: plan.status,
      error: plan.isRejected ? null : plan.error,
      rejected: plan.isRejected,
    },
    {
      label: '소재 차감·자투리 등록',
      done: status === 'CONFIRMED',
      ready: status === 'SIGNING',
      run: confirm.run,
      busy: confirm.isBusy,
      phase: confirm.status,
      error: confirm.isRejected ? null : confirm.error,
      rejected: confirm.isRejected,
    },
    {
      label: '생산 제품 등록',
      done: linked > 0,
      // 개수가 어긋나면 컨트랙트가 되돌린다. 목록을 다 받은 뒤에만 누른다.
      ready: status === 'CONFIRMED' && linked === 0 && itemCount === batch.producedQuantity,
      blocked: status === 'CONFIRMED' && itemCount !== batch.producedQuantity
        ? '제품 목록을 불러오는 중입니다.'
        : undefined,
      run: link.run,
      busy: link.isBusy,
      phase: link.status,
      error: link.isRejected ? null : link.error,
      rejected: link.isRejected,
    },
  ]
  const currentIndex = steps.findIndex((step) => !step.done)
  const current = currentIndex >= 0 ? steps[currentIndex] : null
  const needsChainSwitch =
    current !== null &&
    wallet.address !== null &&
    env.besuChainIdHex !== null &&
    wallet.chainId !== env.besuChainIdHex

  return (
    <div className="agent-chain">
      <ol className="agent-chain__steps">
        {steps.map((step, index) => (
          <li
            key={step.label}
            className={step.done ? 'is-done' : index === currentIndex ? 'is-current' : undefined}
          >
            <span className="agent-chain__mark" aria-hidden="true">
              {step.done ? '✓' : index + 1}
            </span>
            {step.label}
          </li>
        ))}
      </ol>

      {current && (
        <div className="agent-chain__action">
          {needsChainSwitch && (
            <button type="button" className="agent-card__secondary" onClick={() => void wallet.switchChain()}>
              지갑 연결 환경 변경
            </button>
          )}
          <button
            type="button"
            className="agent-card__primary"
            disabled={!current.ready || current.busy}
            onClick={() => void current.run()}
          >
            {current.busy ? '서명 진행 중…' : `${currentIndex + 1}단계 서명하기`}
          </button>
          {onChain && chain.isPending ? (
            <p className="agent-card__note">기록 상태를 확인하는 중입니다…</p>
          ) : (
            <PhaseHint step={current} />
          )}
        </div>
      )}
    </div>
  )
}

interface CompactStep {
  label: string
  done: boolean
  ready: boolean
  blocked?: string
  run: () => Promise<void>
  busy: boolean
  phase: string
  error: unknown
  rejected: boolean
}

/** 지금 단계에서 담당자가 봐야 할 한 줄. 무엇을 기다리는지 모르면 지갑을 봐야 하는지 알 수 없다. */
function PhaseHint({ step }: { step: CompactStep }) {
  if (step.phase === 'SIGNING') return <p className="agent-card__note">지갑에서 요청을 확인해 주세요.</p>
  if (step.phase === 'PENDING') return <p className="agent-card__note">기록을 저장하는 중입니다…</p>
  if (step.phase === 'LINKING') return <p className="agent-card__note">저장 결과를 확인하는 중입니다…</p>
  if (step.rejected) return <p className="agent-card__note">서명을 취소했습니다. 다시 누르면 이어서 할 수 있습니다.</p>
  if (step.error) return <p className="agent-card__error">{errorText(step.error)}</p>
  if (!step.ready && step.blocked) return <p className="agent-card__note">{step.blocked}</p>
  return null
}

function firstBlocked(
  batchStatus: string,
  address: string | null,
  walletMismatch: boolean,
  producerWallet: string,
): string | undefined {
  if (batchStatus !== 'CONFIRMED') return '생산을 확정한 뒤에 기록할 수 있습니다.'
  if (address === null) return '지갑을 연결해 주세요.'
  if (walletMismatch) return `이 생산을 만든 지갑(${producerWallet.slice(0, 6)}…${producerWallet.slice(-4)})으로 연결해 주세요.`
  return undefined
}

function errorText(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message
  }
  return '서명을 처리하지 못했습니다. 다시 시도해 주세요.'
}
