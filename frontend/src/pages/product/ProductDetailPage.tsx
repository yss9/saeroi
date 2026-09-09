import { useParams } from 'react-router-dom'
import { ErrorMessage } from '../../components/common/index.ts'
import { ProductDetail } from '../../features/product/components/ProductDetail.tsx'

/**
 * 제품 상세.
 *
 * **로그인이 필요하다.** 제품 조회와 이미지 모두 서버가 인증을 요구한다. 공개 라우트로
 * 두면 비회원에게 401 로 빈 화면이 나가므로 `ProtectedRoute` 안에 있다.
 *
 * ⚠️ 소비자가 QR 로 들어오는 입구는 이 화면이 아니라 `/qr/:qrToken` 다.
 * 그쪽은 비회원 공개여야 한다 (ARCHITECTURE §9) — 이 화면과 혼동하지 말 것.
 */
export function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>()
  const parsed = Number(productId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  if (id === null) return <ErrorMessage error="잘못된 주소입니다." />

  return (
    <main className="page">
      <ProductDetail productModelId={id} />
    </main>
  )
}
