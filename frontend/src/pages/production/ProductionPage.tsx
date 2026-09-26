import { useNavigate, useSearchParams } from 'react-router-dom'
import { Loading } from '../../components/common/index.ts'
import { useMyMaterials } from '../../features/inventory/hooks/useMyMaterials.ts'
import { ProductionBatchForm } from '../../features/production/components/ProductionBatchForm.tsx'
import { useMyProductionBatches } from '../../features/production/hooks/useMyProductionBatches.ts'
import type { ProductionBatch } from '../../features/production/types/production.types.ts'
import type { DraftInput } from '../../features/production/utils/productionInputDraft.ts'

/**
 * 제품 생산 — 생산 시작.
 *
 * 생산 이름과 개수, 투입할 소재를 여기서 모두 받는다. 시작하면 곧바로 상세로 보낸다 —
 * 다음 할 일(확정)이 거기 있고, 목록으로 돌려보내면 방금 만든 생산을 다시 찾아야 한다.
 *
 * **`?product=` 로 들어오면 지난 생산을 베껴 채운다.** 내 제품의 "추가 생산" 이 그 주소로
 * 보낸다 — 같은 제품을 또 만드는 일이라 이름·개수·투입 소재가 대개 같고, 그것을 매번 다시
 * 적게 하면 옮겨 적다가 수량을 틀린다.
 */
export function ProductionPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const productModelId = Number(params.get('product'))
  const copying = Number.isInteger(productModelId) && productModelId > 0

  const batches = useMyProductionBatches()
  const materials = useMyMaterials()

  // **값이 준비된 뒤에만 폼을 그린다.** 나중에 채우면 사용자가 이미 고친 칸을 덮어쓴다.
  if (copying && (batches.isPending || materials.isPending)) {
    return (
      <main className="page">
        <h1 className="ui-visually-hidden">제품 생산</h1>
        <Loading label="지난 생산을 불러오는 중" />
      </main>
    )
  }

  const template = copying
    ? toTemplate(latestOf(batches.data ?? [], productModelId), materials.data ?? [])
    : undefined

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 생산</h1>

      <ProductionBatchForm
        initial={template}
        onOpened={(batch) => void navigate(`/brand/production/${batch.productionBatchId}`)}
      />
    </main>
  )
}

/** 그 제품으로 만든 가장 마지막 생산. 없으면 베낄 것이 없다. */
function latestOf(
  batches: readonly ProductionBatch[],
  productModelId: number,
): ProductionBatch | undefined {
  return batches
    .filter((batch) => batch.productModelId === productModelId)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0]
}

/**
 * 지난 생산을 폼이 받는 모양으로.
 *
 * **지금 가진 소재만 담는다.** 그때 쓴 소재가 다 소진됐을 수 있고, 없는 소재를 초안에
 * 넣으면 가용 수량을 알 수 없어 검사도 못 한다 — 빠진 것은 사용자가 보고 다시 고른다.
 *
 * 수량은 그대로 베낀다. 지금 가용량보다 많으면 투입 칸이 그 자리에서 이유를 적는다.
 */
function toTemplate(
  batch: ProductionBatch | undefined,
  materials: readonly { materialId: number }[],
): { batchName: string; producedQuantity: number; drafts: DraftInput[] } | undefined {
  if (batch === undefined) return undefined

  const drafts = batch.inputs.flatMap((input) => {
    const material = materials.find((item) => item.materialId === input.materialId)
    if (material === undefined) return []
    return [
      {
        material,
        productUsed: input.productUsedQuantity,
        reusableScrap: input.reusableScrapQuantity,
        waste: input.wasteQuantity,
        wasteReason: input.wasteReason ?? '',
      } as DraftInput,
    ]
  })

  return {
    batchName: batch.batchName,
    producedQuantity: batch.producedQuantity,
    drafts,
  }
}
