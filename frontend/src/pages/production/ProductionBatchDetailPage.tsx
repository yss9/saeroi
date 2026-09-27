import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
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
  const failure = useProductionFailure()
  const parsed = Number(batchId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  return (
    <main className="page">
      <BackLink to="/brand/products/new" label="생산 목록" />
      {id === null ? (
        <ErrorMessage error="잘못된 주소입니다." />
      ) : (
        <>
          {/* 생산 화면에서 배치를 연 뒤 멈춘 경우다. 배치가 사라진 것이 아니라 여기서 이어간다. */}
          {failure !== null && (
            <ErrorMessage error={`생산을 끝까지 기록하지 못했어요. ${failure} 아래에서 이어서 진행해 주세요.`} />
          )}
          <ProductionBatchDetail batchId={id} />
        </>
      )}
    </main>
  )
}

/**
 * 생산 화면이 넘긴 멈춘 이유. **한 번만 보여준다** — 주소 기록에 남겨 두면 새로 고치거나 뒤로
 * 왔을 때, 이미 이어서 끝낸 생산에도 같은 경고가 다시 뜬다.
 */
function useProductionFailure(): string | null {
  const location = useLocation()
  const navigate = useNavigate()
  const [failure] = useState<string | null>(() => {
    const state = location.state as { productionFailure?: unknown } | null
    return typeof state?.productionFailure === 'string' ? state.productionFailure : null
  })

  useEffect(() => {
    if (location.state !== null) void navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, navigate])

  return failure
}
