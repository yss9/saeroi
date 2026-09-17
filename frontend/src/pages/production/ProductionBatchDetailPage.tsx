import { useParams } from 'react-router-dom'
import { BackLink, ErrorMessage } from '../../components/common/index.ts'
import { ProductionBatchDetail } from '../../features/production/components/ProductionBatchDetail.tsx'

/**
 * 생산 상세 — 소재 투입과 확정.
 *
 * **돌아가는 곳은 생산 목록(제품 등록 화면)이다.** 이 화면에 들어오는 길이 거기고,
 * 제품 생산은 새 생산을 시작하는 빈 폼이라 "뒤로" 를 눌러 그곳에 떨어지면 방금 보던
 * 목록이 사라진다. 사이드바도 같은 판단으로 제품 등록에 머문다 (navigationMenu.ts).
 */
export function ProductionBatchDetailPage() {
  const { batchId } = useParams<{ batchId: string }>()
  const parsed = Number(batchId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  return (
    <main className="page">
      <BackLink to="/brand/products/new" label="생산 목록" />
      {id === null ? (
        <ErrorMessage error="잘못된 주소입니다." />
      ) : (
        <ProductionBatchDetail batchId={id} />
      )}
    </main>
  )
}
