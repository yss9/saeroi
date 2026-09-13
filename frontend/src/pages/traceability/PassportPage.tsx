import { useParams } from 'react-router-dom'
import { ErrorMessage } from '../../components/common/index.ts'
import { PassportView } from '../../features/traceability/components/PassportView.tsx'

/**
 * QR Passport 공개 페이지.
 *
 * ⚠️ 이 라우트는 **비회원도 접근 가능한 Public Route** 다.
 * ProtectedRoute 안에 넣지 말 것 — 소비자가 제품의 QR 을 찍고 바로 들어오는
 * 진입점이라 로그인이나 지갑 연결을 요구하면 서비스의 목적 자체가 무너진다.
 *
 * 주소는 `/passport/:productId` 가 아니라 `/qr/:qrToken` 이다 (S15P21E104-136).
 * `productId` 는 순번이라 URL 을 하나씩 바꿔가며 남의 제품 이력을 볼 수 있다 —
 * `qrToken` 이 추측 불가능한 값이라 이 경로의 유일한 접근 통제다 (§6-3, 2026-09-08).
 */
export function PassportPage() {
  const { qrToken } = useParams<{ qrToken: string }>()

  if (!qrToken) return <ErrorMessage error="잘못된 주소입니다." />

  return (
    <main className="passport-page">
      <PassportView qrToken={qrToken} />
    </main>
  )
}
