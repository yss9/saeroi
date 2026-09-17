import { Link } from 'react-router-dom'
import { Button, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyProductionBatches } from '../hooks/useMyProductionBatches.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import './production.css'

export interface ProductionBatchChooserProps {
  onChoose: (batch: ProductionBatch) => void
}

/**
 * 어느 배치로 만든 제품인가 — 고르는 화면.
 *
 * **칸 하나로 고르게 하지 않는다.** 이름만으로는 그 배치가 무엇을 얼마나 만든 것인지 알 수
 * 없어, 사용자가 배치 목록을 따로 열어 확인하고 돌아와야 했다. 수량과 투입량을 함께
 * 보여주면 여기서 끝난다.
 *
 * **아직 제품이 붙지 않은 생산만** 고를 수 있다. 확정 여부는 상관없다 — 브랜드의 순서는
 * "생산을 끝내고 → 그 결과물에 제품 정보를 붙인다" 이고, 서버도 확정된 생산에 제품을 붙일
 * 때 그 생산이 만든 개체까지 함께 채운다.
 *
 * 이미 제품이 붙은 생산은 바꿀 수 없다(`PRODUCTION_PRODUCT_MODEL_ALREADY_SET`, 409) —
 * 개체가 팔린 뒤 제품이 바뀌면 그 주문이 산 것과 재고가 가리키는 제품이 달라진다.
 */
export function ProductionBatchChooser({ onChoose }: ProductionBatchChooserProps) {
  const batches = useMyProductionBatches()

  if (batches.isPending) return <Loading variant="skeleton" rows={3} label="생산 내역을 불러오는 중" />
  if (batches.isError) {
    return <ErrorMessage error={batches.error} onRetry={() => void batches.refetch()} />
  }

  return (
    <section className="batch-chooser">
      {batches.data.length === 0 ? (
        <p className="batch-chooser__empty">
          생산 내역이 없습니다. <Link to="/brand/production">생산을 시작하고</Link> 소재를
          투입하면 그 생산으로 만들 제품을 등록할 수 있습니다.
        </p>
      ) : (
        // 보이는 제목이 없으므로 목록 이름은 여기서 준다. 화면을 소리로 읽는 사람에게
        // "목록" 하나만 남으면 무엇의 목록인지 알 수 없다.
        <ul className="batch-chooser__list" aria-label="내 생산">
          {sortForChoosing(batches.data).map((batch) => {
            // 고를 수 없는 배치도 지우지 않고 왜 못 고르는지 적는다. 빼버리면 "내 배치가
            // 사라졌다" 로 읽힌다.
            const blocked = blockedReason(batch)
            return (
              <li
                key={batch.productionBatchId}
                className={
                  blocked === null
                    ? 'batch-chooser__row'
                    : 'batch-chooser__row batch-chooser__row--locked'
                }
              >
                {/*
                  칸은 생산 상세로 간다 — 소재를 더 담거나 확정하는 일이 거기 있다.
                  제품 등록은 오른쪽 버튼이 따로 맡는다. 둘을 한 곳에 묶으면 상세를 보러
                  누른 사람에게 등록 창이 열린다.
                */}
                <Link
                  className="batch-chooser__link"
                  to={`/brand/production/${batch.productionBatchId}`}
                >
                  {/*
                    못 고르는 이유는 맨 위 왼쪽이다 — 이름을 읽기 전에 눈에 들어와야
                    "왜 등록 버튼이 없지" 를 찾지 않는다. 지우지 않는 이유는 빼버리면
                    "내 생산이 사라졌다" 로 읽히기 때문이다.
                  */}
                  {blocked !== null && <small className="batch-chooser__blocked">{blocked}</small>}
                  <strong>{batch.batchName}</strong>
                  <span>
                    {batch.producedQuantity}개 생산 · 투입 {formatKg(usedOf(batch))} ·{' '}
                    {batch.inputs.length}건
                  </span>
                  <small className="batch-chooser__code">{batch.batchCode}</small>
                </Link>

                {/*
                  **확정 전에는 확정이 먼저다.** 제품은 확정된 생산의 개체에 붙는 것이라,
                  아직 확정하지 않은 생산에 제품을 걸어 두면 재고 0 인 제품이 남는다.

                  확정 자체는 여기서 하지 않는다 — 되돌릴 수 없는 동작이라 무엇이 얼마나
                  소비되는지 읽고 눌러야 하고, 그 안내는 생산 상세에 있다.
                */}
                {blocked === null &&
                  (batch.status === 'CONFIRMED' ? (
                    <Button size="sm" onClick={() => onChoose(batch)}>
                      제품 등록
                    </Button>
                  ) : (
                    <Link
                      className="ui-button ui-button--sm batch-chooser__confirm"
                      to={`/brand/production/${batch.productionBatchId}`}
                    >
                      생산 확정
                    </Link>
                  ))}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

/**
 * 고르는 순서.
 *
 * **서버가 순서를 정해 주지 않는다** (`findAllByProducerId` 에 정렬이 없다). 그대로 그리면
 * DB 가 주는 순서 = 사실상 만든 순서라, 방금 확정하고 제품을 붙이러 온 사람이 목록 끝까지
 * 내려가야 방금 그 생산을 만난다.
 *
 * 그래서 두 가지로 줄을 세운다.
 *
 *   1) **고를 수 있는 것이 먼저.** 이 화면에 온 목적이 그것이고, 잠긴 줄은 "왜 없지" 를
 *      막기 위해 남겨 둔 참고 자료다.
 *   2) 고를 수 있는 것 안에서는 **최신순** — 제품을 붙이는 대상은 거의 항상 방금 끝낸
 *      생산이다. 잠긴 것은 **번호순**으로 둔다. 그쪽은 찾아보는 목록이라 자리가 매번
 *      바뀌지 않는 편이 낫다.
 */
function sortForChoosing(batches: readonly ProductionBatch[]): ProductionBatch[] {
  const open = batches.filter((batch) => blockedReason(batch) === null)
  const blocked = batches.filter((batch) => blockedReason(batch) !== null)

  return [
    ...open.sort((left, right) => right.createdAt.localeCompare(left.createdAt)),
    ...blocked.sort((left, right) => left.productionBatchId - right.productionBatchId),
  ]
}

/**
 * 이 생산에 제품을 붙일 수 없는 이유.
 *
 * 서버가 받는 상태는 `DRAFT` 와 `CONFIRMED` 다. 서명 중(`SIGNING`)은 막는다 — 서명하는
 * 도중에 내용이 바뀌면 서명한 것과 저장된 것이 어긋난다. 실패·취소된 생산은 만들어진
 * 개체가 없어 붙일 곳이 없다.
 *
 * 제품이 이미 붙은 생산도 막는다. 확정된 뒤에는 서버가 거절하고
 * (`PRODUCTION_PRODUCT_MODEL_ALREADY_SET`), 그 전이라도 다른 제품으로 바꾸면 먼저 고른
 * 제품이 조용히 재고를 잃는다.
 */
function blockedReason(batch: ProductionBatch): string | null {
  if (batch.productModelId !== null) return '이미 제품이 연결된 생산입니다'
  if (batch.status === 'SIGNING') return '서명이 끝난 뒤에 연결할 수 있습니다'
  if (batch.status !== 'DRAFT' && batch.status !== 'CONFIRMED') {
    return '작성 중이거나 확정된 생산에만 연결할 수 있습니다'
  }
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
