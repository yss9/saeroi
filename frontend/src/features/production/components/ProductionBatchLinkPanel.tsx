import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, ErrorMessage, Loading, Select } from '../../../components/common/index.ts'
import { useAssignProductModel } from '../hooks/useAssignProductModel.ts'
import { useMyProductionBatches } from '../hooks/useMyProductionBatches.ts'
import './production.css'

export interface ProductionBatchLinkPanelProps {
  /** 방금 등록한 제품 */
  productModelId: number
  productName: string
}

/**
 * 방금 등록한 제품을 생산 배치에 잇는다.
 *
 * **왜 이 연결이 필요한가** — 배치를 확정하면 서버가 제품 개체를 생산 개수만큼 만들고
 * (`ProductItem.produce(batch.productModelId, …)`), 그 개체마다 QR 이 붙는다. 배치가
 * 어느 제품인지 모르면 만들어진 개체가 아무 상품에도 속하지 않아 QR 을 찍어도 나올 제품이
 * 없고, 쇼핑몰 재고(`stockQuantity`)도 오르지 않는다.
 *
 * **아직 제품이 없는 작성 중 배치만 고른다.** 확정된 배치는 이미 개체를 만들어 버려 바꿀
 * 수 없고(서버도 `DRAFT` 에서만 받는다), 이미 제품이 붙은 배치를 다시 고르면 남의 제품
 * 재고를 빼앗는 꼴이 된다.
 *
 * 연결하지 않아도 등록 자체는 끝났다 — 나중에 배치 상세에서 이어도 된다. 그래서 이
 * 패널은 건너뛸 수 있고, 고르지 않았다고 등록이 되돌아가지 않는다.
 */
export function ProductionBatchLinkPanel({
  productModelId,
  productName,
}: ProductionBatchLinkPanelProps) {
  const batches = useMyProductionBatches()
  const assign = useAssignProductModel()
  const [selected, setSelected] = useState('')

  if (batches.isPending) return <Loading label="생산 배치를 불러오는 중" />
  if (batches.isError) {
    return <ErrorMessage error={batches.error} onRetry={() => void batches.refetch()} />
  }

  const linkable = batches.data.filter(
    (batch) => batch.status === 'DRAFT' && batch.productModelId === null,
  )

  // 연결이 끝나면 고를 것이 아니라 결과를 보여준다.
  if (assign.isSuccess) {
    return (
      <p className="batch-link__done" role="status">
        {assign.data.batchName} 배치가 {productName}(으)로 연결되었습니다. 배치를 확정하면
        제품 개체와 QR 이 만들어집니다.{' '}
        <Link to={`/brand/production/${assign.data.productionBatchId}`}>배치로 이동</Link>
      </p>
    )
  }

  if (linkable.length === 0) {
    return (
      <p className="batch-link__note">
        연결할 배치가 없습니다. <Link to="/brand/production">생산 배치를 먼저 열어</Link>
        {' '}소재를 투입한 뒤, 그 배치에 이 제품을 연결하세요.
      </p>
    )
  }

  return (
    <div className="batch-link">
      <p className="batch-link__note">
        이 제품을 어느 배치로 만들었나요? 연결한 배치를 확정하면 제품 개체와 QR 이
        만들어지고, 그만큼 판매할 수 있게 됩니다.
      </p>

      <div className="batch-link__row">
        <Select
          label="생산 배치"
          placeholder="선택하세요"
          value={selected}
          options={linkable.map((batch) => ({
            value: String(batch.productionBatchId),
            label: `${batch.batchName} · ${batch.producedQuantity}개`,
          }))}
          onChange={(event) => setSelected(event.target.value)}
        />

        <Button
          disabled={selected === ''}
          loading={assign.isPending}
          onClick={() => assign.mutate({ batchId: Number(selected), productModelId })}
        >
          배치에 연결
        </Button>
      </div>

      {assign.isError && <ErrorMessage error={assign.error} />}
    </div>
  )
}
