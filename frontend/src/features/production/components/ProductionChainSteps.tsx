import { Button, ErrorMessage, TransactionStatus } from '../../../components/common/index.ts'
import type { AppError } from '../../../utils/errors.ts'
import {
  useConfirmProductionOnChain,
  useLinkProductionItems,
  useSubmitProductionPlan,
  type ChainStepAction,
} from '../hooks/useProductionChainSteps.ts'
import { useProductionChainStatus } from '../hooks/useProductionChainStatus.ts'
import { useProductionItems } from '../hooks/useProductionItems.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import './production.css'

/** TransactionStatus 가 받는 에러 모양. 훅이 unknown 으로 내주는 값을 좁힌다. */
type TransactionStatusError = AppError | null

export interface ProductionChainStepsProps {
  batch: ProductionBatch
}

/**
 * 온체인 2~4단계.
 *
 * 1단계(배치 생성)는 `ProductionChainPanel` 이 맡는다. 그쪽은 번호를 채번하는 단계라
 * 모양이 다르고, 이 컴포넌트는 **이미 있는 배치를 앞으로 미는 세 단계**만 다룬다.
 *
 * **다음에 할 수 있는 일은 체인이 정한다.** 서버 응답으로는 3·4단계가 끝났는지 알 수 없고,
 * 서명은 됐는데 서버 보고가 실패한 경우 서버 기준으로는 영원히 "아직" 으로 보인다. 그
 * 상태에서 다시 서명하면 컨트랙트가 거절해 화면이 막다른 길이 된다.
 *
 * 각 단계는 **되돌릴 수 없다.** 특히 3단계는 소재를 실제로 태운다.
 */
export function ProductionChainSteps({ batch }: ProductionChainStepsProps) {
  const chain = useProductionChainStatus(batch.chainBatchId)
  const items = useProductionItems(batch.productionBatchId, batch.status === 'CONFIRMED')

  const plan = useSubmitProductionPlan(batch)
  const confirm = useConfirmProductionOnChain(batch)
  const link = useLinkProductionItems(batch, items.data ?? [])

  // 1단계를 마치기 전에는 뒤의 단계가 존재하지 않는다. 그 사실은 패널이 이미 말한다.
  if (batch.chainBatchId === null) return null

  if (chain.isPending) {
    return <p className="chain-steps__loading">온체인 상태를 확인하는 중…</p>
  }

  if (chain.isError) {
    return (
      <ErrorMessage
        error="온체인 상태를 읽지 못했습니다. 다음 단계를 진행할 수 있는지 확인할 수 없습니다."
        onRetry={() => void chain.refetch()}
      />
    )
  }

  const { status, linkedItemCount } = chain.data
  const itemCount = items.data?.length ?? 0

  return (
    <ol className="chain-steps chain-steps--actions">
      <li className="chain-steps__done">온체인 생산 기록</li>

      <Step
        label="투입 잠금·계획 기록"
        description="투입한 소재를 체인에 적고 그만큼을 잠급니다. 잠긴 소재는 다른 생산에 쓸 수 없습니다."
        action={plan}
        done={status !== 'DRAFT'}
        ready={status === 'DRAFT'}
        blockedReason={status === 'DRAFT' ? undefined : '이미 기록된 단계입니다.'}
      />

      <Step
        label="소재 소각·자투리 발행"
        description="잠긴 소재가 실제로 사라지고, 자투리가 있으면 새 소재로 발행되어 다시 팔 수 있습니다. 되돌릴 수 없습니다."
        action={confirm}
        done={status === 'CONFIRMED'}
        ready={status === 'SIGNING'}
        blockedReason={
          status === 'DRAFT' ? '투입 잠금을 먼저 기록해 주세요.' : undefined
        }
      />

      <Step
        label="제품 개체 연결"
        description="만들어진 개체를 생산에 묶습니다. 이 단계가 끝나야 QR 에서 소재 이력이 이어집니다."
        action={link}
        done={linkedItemCount > 0}
        ready={status === 'CONFIRMED' && linkedItemCount === 0 && itemCount === batch.producedQuantity}
        blockedReason={blockedItemsReason({
          status,
          linkedItemCount,
          itemCount,
          producedQuantity: batch.producedQuantity,
        })}
      />
    </ol>
  )
}

/**
 * 개체 연결이 막힌 이유.
 *
 * **개수가 어긋나면 컨트랙트가 revert 한다.** 목록을 아직 다 받지 못한 상태로 누르면
 * 서명까지 하고 실패하므로, 그 전에 막고 이유를 적는다.
 */
function blockedItemsReason({
  status,
  linkedItemCount,
  itemCount,
  producedQuantity,
}: {
  status: string
  linkedItemCount: number
  itemCount: number
  producedQuantity: number
}): string | undefined {
  if (linkedItemCount > 0) return undefined
  if (status !== 'CONFIRMED') return '소재 소각을 먼저 마쳐 주세요.'
  if (itemCount === 0) return '개체 목록을 불러오는 중입니다.'
  if (itemCount !== producedQuantity) {
    return `연결할 개체 수가 맞지 않습니다 (${itemCount} / ${producedQuantity}개). 목록을 새로 고친 뒤 다시 시도해 주세요.`
  }
  return undefined
}

function Step({
  label,
  description,
  action,
  done,
  ready,
  blockedReason,
}: {
  label: string
  description: string
  action: ChainStepAction
  done: boolean
  ready: boolean
  blockedReason?: string
}) {
  return (
    <li className={done ? 'chain-steps__done' : undefined}>
      <div className="chain-steps__head">
        <span>{label}</span>
        {done ? (
          <span className="chain-steps__badge">완료</span>
        ) : (
          <Button
            size="sm"
            disabled={!ready}
            loading={action.isBusy}
            onClick={() => void action.run()}
          >
            서명
          </Button>
        )}
      </div>

      {!done && <p className="chain-steps__description">{description}</p>}

      {/* 못 누르는 이유를 적는다. 잠긴 버튼은 이유를 말하지 않는다. */}
      {!done && !ready && blockedReason && (
        <p className="chain-steps__blocked">{blockedReason}</p>
      )}

      {action.status !== 'IDLE' && (
        <TransactionStatus
          status={action.status}
          hash={action.hash}
          error={action.error as TransactionStatusError}
        />
      )}

      {/* 사용자가 지갑에서 취소한 것은 실패가 아니다 — 에러로 덮지 않는다. */}
      {action.error !== null && !action.isRejected && <ErrorMessage error={action.error} />}
    </li>
  )
}
