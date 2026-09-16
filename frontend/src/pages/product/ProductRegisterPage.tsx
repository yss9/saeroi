import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ProductCreatedSummary,
  ProductForm,
} from '../../features/product/components/ProductForm.tsx'
import type { ProductModel } from '../../features/product/types/product.types.ts'

/**
 * 제품 등록 (Brand).
 *
 * 등록이 끝나면 폼을 비우고 결과를 위에 남긴다 — 브랜드는 제품을 여러 개 연달아 올리므로
 * 등록마다 다른 화면으로 보내면 매번 돌아와야 한다.
 *
 * 등록 결과가 곧 "판매 시작" 이 아니다. 응답의 상태는 언제나 `DRAFT` 이고 판매 가능 수량은
 * 0 이다 — 그 사실을 결과 요약이 직접 말한다.
 */
export function ProductRegisterPage() {
  // 방금 등록한 제품. 여러 개를 올릴 수 있어 마지막 것만 보여준다.
  const [created, setCreated] = useState<ProductModel | null>(null)

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 등록</h1>

      {created && <ProductCreatedSummary product={created} />}

      <ProductForm onCreated={setCreated} />

      {/* 마켓(/products)이 아니라 내 제품으로 보낸다 — 방금 등록한 제품은 DRAFT 라
          마켓 목록에 없다. 거기로 보내면 "등록했는데 왜 없지" 가 된다. */}
      <p className="page__lead">
        <Link to="/brand/products">내 제품 관리로 이동</Link>
      </p>
    </main>
  )
}
