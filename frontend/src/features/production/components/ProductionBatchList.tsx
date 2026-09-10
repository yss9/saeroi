import { Link } from 'react-router-dom'
import {
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyProductionBatches } from '../hooks/useMyProductionBatches.ts'
import type { ProductionBatch, ProductionBatchStatus } from '../types/production.types.ts'
import './production.css'

const STATUS_LABEL: Record<ProductionBatchStatus, string> = {
  DRAFT: '작성 중',
  SIGNING: '서명 중',
  CONFIRMED: '확정',
  FAILED: '실패',
  CANCELED: '취소',
}

/**
 * `DRAFT` 를 강조하지 않는다. 작성 중은 "할 일이 남았다" 는 뜻이지 문제가 아니고,
 * 확정만이 끝난 상태다.
 */
const TONE: Record<ProductionBatchStatus, StatusTone> = {
  DRAFT: 'warning',
  SIGNING: 'info',
  CONFIRMED: 'success',
  FAILED: 'danger',
  CANCELED: 'neutral',
}

/**
 * 내 생산 배치 목록.
 *
 * **작성 중인 배치를 이어서 하러 오는 화면이다.** 배치는 열고 투입을 담고 확정하는 여러
 * 단계라 한 번에 끝나지 않는다 — 중간에 나갔다가 돌아올 곳이 필요하다.
 *
 * 투입 합계를 여기서 계산한다. 배치의 `totalUsedQuantity` 는 확정 전까지 0 이라
 * 그대로 보여주면 "투입했는데 0" 이 된다 (`ProductionInputList` 와 같은 이유).
 */
export function ProductionBatchList() {
  const { data, isPending, isError, error, refetch } = useMyProductionBatches()

  if (isPending) return <Loading variant="skeleton" rows={3} label="생산 배치를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  if (data.length === 0) {
    return (
      <EmptyState
        title="생산 배치가 없습니다"
        description="위에서 배치를 열고 보유 소재를 투입하면 제품을 만들 수 있습니다."
      />
    )
  }

  return (
    <ul className="batch-list">
      {data.map((batch) => (
        <li key={batch.productionBatchId} className="batch-list__item">
          <Link to={`/brand/production/${batch.productionBatchId}`} className="batch-list__link">
            <div className="batch-list__main">
              <span className="batch-list__code">{batch.batchCode}</span>
              <strong className="batch-list__name">{batch.batchName}</strong>
              <span className="batch-list__meta">
                {batch.producedQuantity}개 생산 · 투입 {formatKg(usedOf(batch))}
              </span>
            </div>
            <div className="batch-list__side">
              <StatusBadge tone={TONE[batch.status]}>{STATUS_LABEL[batch.status]}</StatusBadge>
              {/* 작성 중은 사용자가 이어서 해야 하는 상태다. 목록이 길어지면
                  "어디까지 했지" 를 찾기 어려워진다. */}
              {batch.status === 'DRAFT' && (
                <span className="batch-list__hint">
                  {batch.inputs.length === 0 ? '소재 투입 필요' : '확정 필요'}
                </span>
              )}
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}

/** 투입 합계. 확정 전에는 배치 필드가 0 이므로 투입에서 계산한다. */
function usedOf(batch: ProductionBatch): number {
  return batch.inputs.reduce((total, input) => total + input.inputQuantity, 0)
}
