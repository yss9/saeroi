import { useParams } from 'react-router-dom'

/**
 * QR Passport 공개 페이지.
 *
 * ⚠️ 이 라우트는 **비회원도 접근 가능한 Public Route** 다.
 * ProtectedRoute 안에 넣지 말 것 — 소비자가 제품의 QR 을 찍고 바로 들어오는
 * 진입점이라 로그인이나 지갑 연결을 요구하면 서비스의 목적 자체가 무너진다.
 *
 * 따라서 이 화면이 호출하는 API 도 인증을 요구하지 않아야 한다.
 * TODO(6번/Traceability 담당): 공개 조회 API 가 나오면 usePassport() 훅으로 연결한다.
 */
export function PassportPage() {
  const { productId } = useParams<{ productId: string }>()

  return (
    <main className="page">
      <h1>제품 이력</h1>
      <p>제품 번호: {productId}</p>
      <p>
        원재료의 출처부터 가공·생산까지의 Material Journey가 여기에 표시됩니다.
        {/* 구조 확인용 자리표시자 — 실제 타임라인은 features/traceability 에서 구현한다. */}
      </p>
    </main>
  )
}
