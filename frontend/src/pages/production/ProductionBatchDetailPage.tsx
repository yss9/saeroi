import { useParams } from 'react-router-dom'
import { BackLink, ErrorMessage } from '../../components/common/index.ts'
import { ProductionBatchDetail } from '../../features/production/components/ProductionBatchDetail.tsx'

/** 생산 배치 상세 — 소재 투입과 확정. */
export function ProductionBatchDetailPage() {
  const { batchId } = useParams<{ batchId: string }>()
  const parsed = Number(batchId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  return (
    <main className="page">
      <BackLink to="/brand/production" label="제품 생산" />
      {id === null ? (
        <ErrorMessage error="잘못된 주소입니다." />
      ) : (
        <ProductionBatchDetail batchId={id} />
      )}
    </main>
  )
}
