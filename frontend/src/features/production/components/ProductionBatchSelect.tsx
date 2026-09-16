import { Link } from 'react-router-dom'
import { ErrorMessage, Loading, Select } from '../../../components/common/index.ts'
import { useMyProductionBatches } from '../hooks/useMyProductionBatches.ts'

export interface ProductionBatchSelectProps {
  value: number | null
  onChange: (batchId: number | null) => void
}

/**
 * 어느 배치로 만든 제품인가.
 *
 * **왜 고르는가** — 배치를 확정하면 서버가 제품 개체를 생산 개수만큼 만들고
 * (`ProductItem.produce(batch.productModelId, …)`), 그 개체마다 QR 이 붙는다. 배치가 어느
 * 제품인지 모르면 만들어진 개체가 아무 상품에도 속하지 않아 QR 을 찍어도 나올 제품이 없고,
 * 쇼핑몰 재고(`stockQuantity`)도 오르지 않는다.
 *
 * **아직 제품이 없는 작성 중 배치만 고른다.** 확정된 배치는 이미 개체를 만들어 버려 바꿀
 * 수 없고(서버도 `DRAFT` 에서만 받는다), 이미 제품이 붙은 배치를 고르면 그 제품의 재고를
 * 빼앗는 꼴이 된다.
 *
 * 고르지 않아도 등록은 된다. 배치는 나중에 이어도 되고, 그때까지 그 배치를 확정할 수
 * 없을 뿐이다.
 */
export function ProductionBatchSelect({ value, onChange }: ProductionBatchSelectProps) {
  const batches = useMyProductionBatches()

  if (batches.isPending) return <Loading label="생산 배치를 불러오는 중" />
  if (batches.isError) {
    return <ErrorMessage error={batches.error} onRetry={() => void batches.refetch()} />
  }

  const linkable = batches.data.filter(
    (batch) => batch.status === 'DRAFT' && batch.productModelId === null,
  )

  // **고를 것이 없어도 칸은 남긴다.** 칸이 사라지면 다른 칸들의 자리가 밀리고, 무엇보다
  // "제품은 배치에 붙는다" 는 사실 자체가 화면에서 없어진다 — 이을 배치가 없는 사람이야말로
  // 그 사실을 모르는 사람이다.
  const empty = linkable.length === 0

  return (
    <>
      <Select
        label="생산 배치"
        placeholder={empty ? '연결할 배치 없음' : '나중에 연결'}
        disabled={empty}
        hint="이 제품을 만든 배치입니다. 연결한 배치를 확정하면 개체와 QR 이 만들어집니다."
        value={value === null ? '' : String(value)}
        options={linkable.map((batch) => ({
          value: String(batch.productionBatchId),
          label: `${batch.batchName} · ${batch.producedQuantity}개`,
        }))}
        onChange={(event) => {
          const next = Number(event.target.value)
          onChange(Number.isInteger(next) && next > 0 ? next : null)
        }}
      />

      {/* 잠긴 칸은 이유를 말하지 않는다 — 어디서 배치를 여는지까지 적는다. */}
      {empty && (
        <p className="batch-link__note">
          작성 중이고 아직 제품이 붙지 않은 배치가 없습니다. 등록은 지금 해도 되고,{' '}
          <Link to="/brand/production">배치를 연 뒤</Link> 이어도 됩니다.
        </p>
      )}
    </>
  )
}
