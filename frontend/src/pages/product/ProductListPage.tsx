import { ProductList } from '../../features/product/components/ProductList.tsx'

/**
 * 제품 마켓.
 *
 * **로그인이 필요하다.** 제품 조회와 대표 이미지 모두 서버가 인증을 요구한다.
 *
 * 브랜드 본인의 제품 관리는 `/brand/products` 다 — 이 화면은 판매 중인 제품만 보여주므로
 * 방금 등록한 제품(DRAFT)은 여기 없다.
 */
export function ProductListPage() {
  return (
    <main className="page">
      <header className="page__header">
        <h1>제품</h1>
        <p className="page__lead">
          업사이클 소재로 만든 제품입니다. 제품을 열면 어떤 소재로 만들어졌는지 볼 수 있습니다.
        </p>
      </header>

      <ProductList />
    </main>
  )
}
