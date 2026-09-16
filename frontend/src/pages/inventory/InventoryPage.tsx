import { InventoryList } from '../../features/inventory/components/InventoryList.tsx'
import { ProcessingCustodyList } from '../../features/inventory/components/ProcessingCustodyList.tsx'

/**
 * 보유 소재 화면 (My Materials).
 *
 * Page 의 책임은 조합과 배치뿐이다 — API 호출·비즈니스 로직·응답 가공이 여기 없다.
 * 전부 InventoryList → useMyMaterials → getMyMaterials → apiClient 로 내려가 있다.
 *
 * 라우트 `/inventory` 는 `ProtectedRoute` 안에 있어 비로그인 접근이 차단된다.
 * 역할 제한은 걸지 않는다 — 소재를 보유할 수 있는 역할이면 누구나 자기 보유량을
 * 봐야 하고, 서버가 JWT `sub` 로 자기 것만 돌려준다.
 */
export function InventoryPage() {
  return (
    <main className="page">
      <h1>보유 소재</h1>
      <InventoryList />
      <ProcessingCustodyList />
    </main>
  )
}
