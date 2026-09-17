import { Link } from 'react-router-dom'
import { Button, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyProductionBatches } from '../hooks/useMyProductionBatches.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import './production.css'

export interface ProductionBatchChooserProps {
  onChoose: (batch: ProductionBatch) => void
  /** 배치를 걸지 않고 제품만 등록한다 */
  onSkip: () => void
}

/**
 * 어느 배치로 만든 제품인가 — 고르는 화면.
 *
 * **칸 하나로 고르게 하지 않는다.** 이름만으로는 그 배치가 무엇을 얼마나 만든 것인지 알 수
 * 없어, 사용자가 배치 목록을 따로 열어 확인하고 돌아와야 했다. 수량과 투입량을 함께
 * 보여주면 여기서 끝난다.
 *
 * **작성 중이고 아직 제품이 붙지 않은 배치만** 고를 수 있다. 확정된 배치는 이미 개체를
 * 만들어 버려 바꿀 수 없고(서버도 `DRAFT` 에서만 받는다), 제품이 붙은 배치를 고르면 그
 * 제품의 재고를 빼앗는다.
 */
export function ProductionBatchChooser({ onChoose, onSkip }: ProductionBatchChooserProps) {
  const batches = useMyProductionBatches()

  if (batches.isPending) return <Loading variant="skeleton" rows={3} label="생산 내역을 불러오는 중" />
  if (batches.isError) {
    return <ErrorMessage error={batches.error} onRetry={() => void batches.refetch()} />
  }

  return (
    <section className="batch-chooser">
      <div className="batch-chooser__head">
        <div>
          <h2>어느 생산으로 만든 제품인가요?</h2>
          <p>
            생산을 누르면 제품을 적는 창이 열립니다. 그 생산을 확정하면 이 제품의 개체와
            QR 이 만들어지고, 그만큼 판매할 수 있게 됩니다.
          </p>
        </div>

        {/* 배치 없이 등록하는 것도 정상 경로다. 제품을 먼저 올려 두고 나중에 이어도 된다. */}
        <Button variant="secondary" onClick={onSkip}>
          생산 연결 없이 등록
        </Button>
      </div>

      {batches.data.length === 0 ? (
        <p className="batch-chooser__empty">
          생산 내역이 없습니다. <Link to="/brand/production">생산을 시작하고</Link> 소재를
          투입하면 그 생산으로 만들 제품을 등록할 수 있습니다.
        </p>
      ) : (
        <ul className="batch-chooser__list">
          {batches.data.map((batch) => {
            // 고를 수 없는 배치도 지우지 않고 왜 못 고르는지 적는다. 빼버리면 "내 배치가
            // 사라졌다" 로 읽힌다.
            const blocked = blockedReason(batch)
            return (
              <li key={batch.productionBatchId}>
                <button
                  type="button"
                  disabled={blocked !== null}
                  onClick={() => onChoose(batch)}
                >
                  <strong>{batch.batchName}</strong>
                  <span>
                    {batch.producedQuantity}개 생산 · 투입 {formatKg(usedOf(batch))} ·{' '}
                    {batch.inputs.length}건
                  </span>
                  {blocked !== null && (
                    <small className="batch-chooser__blocked">{blocked}</small>
                  )}
                </button>

                {/*
                  배치 자체를 이어서 하러 가는 길. 소재를 더 담거나 확정하는 일은 상세에서
                  하는데, 이 카드를 누르면 제품 등록 창이 열리므로 따로 둔다.
                */}
                <Link to={`/brand/production/${batch.productionBatchId}`}>
                  {batch.batchCode} 생산 상세
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

/**
 * 이 배치에 제품을 붙일 수 없는 이유.
 *
 * 확정된 배치는 이미 개체를 만들어 버려 바꿀 수 없고(서버도 `DRAFT` 에서만 받는다),
 * 제품이 이미 붙은 배치를 고르면 그 제품의 재고를 빼앗는다.
 */
function blockedReason(batch: ProductionBatch): string | null {
  if (batch.productModelId !== null) return '이미 제품이 연결된 생산입니다'
  if (batch.status !== 'DRAFT') return '작성 중인 생산에만 연결할 수 있습니다'
  return null
}

/**
 * 투입 합계.
 *
 * 배치의 `totalUsedQuantity` 는 확정 시점에 채워지므로 그 전에는 0 이다 — 그대로 쓰면
 * 투입을 담아 둔 배치가 "투입 0" 으로 보인다.
 */
function usedOf(batch: ProductionBatch): number {
  return batch.inputs.reduce((total, input) => total + input.inputQuantity, 0)
}
