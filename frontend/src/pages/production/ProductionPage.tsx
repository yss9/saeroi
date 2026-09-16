import { useNavigate } from 'react-router-dom'
import { ProductionBatchForm } from '../../features/production/components/ProductionBatchForm.tsx'
import { ProductionBatchList } from '../../features/production/components/ProductionBatchList.tsx'

/**
 * 제품 생산.
 *
 * 배치를 열고, 작성 중인 배치를 이어서 하러 오는 화면이다. 소재 투입과 확정은 배치 상세
 * (`/brand/production/:batchId`)에서 한다 — 투입은 배치 하나에 속하는 일이라 목록 화면에
 * 두면 어느 배치에 담는지가 흐려진다.
 *
 * 무엇을 만들지(완성 제품)도 상세에서 정한다 — 서버가 확정 전까지만 요구한다.
 */
export function ProductionPage() {
  const navigate = useNavigate()

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 생산</h1>

      <section className="page__section">
        <h2>배치 열기</h2>
        {/* 열자마자 상세로 보낸다. 다음 할 일이 소재 투입이고 그건 상세에서만 할 수 있다 —
            목록으로 돌려보내면 방금 만든 배치를 다시 찾아 눌러야 한다. */}
        <ProductionBatchForm
          onOpened={(batch) => void navigate(`/brand/production/${batch.productionBatchId}`)}
        />
      </section>

      <section className="page__section">
        <h2>내 생산 배치</h2>
        <ProductionBatchList />
      </section>
    </main>
  )
}
