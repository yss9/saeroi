import { Link } from 'react-router-dom'
import { BrandProductList } from '../../features/product/components/BrandProductList.tsx'

/**
 * 브랜드 제품 관리.
 *
 * **등록과 판매 사이를 잇는 화면이다.** 제품 등록은 언제나 `DRAFT` 로 저장되고 판매 시작은
 * 별도 요청인데, 그 버튼을 놓을 자리가 여태 없었다 — 등록한 제품이 어디에도 보이지 않았다.
 *
 * 업무 목록(`/business/products`)과 다른 목록이다. 이쪽은 `DRAFT` 를 포함한다.
 */
export function BrandProductPage() {
  return (
    <main className="page">
      <h1 className="ui-visually-hidden">내 제품</h1>

      {/*
        목록 위 오른쪽, 상태 거르개와 같은 줄. 버튼만 따로 한 줄을 차지하면 목록이
        시작되기 전에 줄이 둘 생긴다 — 자리는 목록이 정하고 화면은 무엇을 놓을지만 정한다.
      */}
      <BrandProductList
        actions={
          <Link className="brand-product-page__action" to="/brand/products/new">
            + 새 제품 등록
          </Link>
        }
      />
    </main>
  )
}
