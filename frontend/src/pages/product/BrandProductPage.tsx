import { Link } from 'react-router-dom'
import { BrandProductList } from '../../features/product/components/BrandProductList.tsx'

/**
 * 브랜드 제품 관리.
 *
 * **등록과 판매 사이를 잇는 화면이다.** 제품 등록은 언제나 `DRAFT` 로 저장되고 판매 시작은
 * 별도 요청인데, 그 버튼을 놓을 자리가 여태 없었다 — 등록한 제품이 어디에도 보이지 않았다.
 *
 * 마켓 목록(`/products`)과 다른 목록이다. 이쪽은 `DRAFT` 를 포함한다.
 */
export function BrandProductPage() {
  return (
    <main className="page">
      <header className="page__header">
        <h1>내 제품</h1>
        <p className="page__lead">
          등록한 제품의 판매를 시작하거나 중단합니다. 판매를 시작하려면 생산으로 개체가
          만들어져 있어야 합니다.
        </p>
      </header>

      <p className="page__lead">
        <Link to="/brand/products/new">새 제품 등록</Link>
      </p>

      <BrandProductList />
    </main>
  )
}
