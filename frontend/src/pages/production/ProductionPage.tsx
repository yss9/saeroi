import { useNavigate } from 'react-router-dom'
import { ProductionBatchForm } from '../../features/production/components/ProductionBatchForm.tsx'

/**
 * 제품 생산 — 배치 열기.
 *
 * 배치 이름과 생산 개수, 투입할 소재를 여기서 모두 받는다. 열면 곧바로 상세로 보낸다 —
 * 다음 할 일(확정)이 거기 있고, 목록으로 돌려보내면 방금 만든 배치를 다시 찾아야 한다.
 *
 * 만들어진 배치는 제품 등록 화면이 목록으로 보여준다 — 배치를 고르고 그 배치로 만든
 * 제품을 적는 것이 실제 순서다.
 */
export function ProductionPage() {
  const navigate = useNavigate()

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 생산</h1>

      <ProductionBatchForm
        onOpened={(batch) => void navigate(`/brand/production/${batch.productionBatchId}`)}
      />
    </main>
  )
}
