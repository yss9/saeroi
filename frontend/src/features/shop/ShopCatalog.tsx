import { ArrowLeft, ArrowRight } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { publicProductImageUrl } from '../product/api/publicProductImageUrl.ts'
import { usePublicProductModels } from '../product/hooks/usePublicProductModels.ts'
import type { PublicProductModel } from '../product/types/publicProduct.types.ts'
import { paginationWindow, resolveShopCategory, shopCategories, shopPath, shopQuery } from './catalog.ts'

const price = new Intl.NumberFormat('ko-KR')

export function ShopCatalog() {
  const { category, subcategory } = useParams()
  const selection = resolveShopCategory(category, subcategory)
  if (!selection) return (
    <section className="shop-catalog__state">
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>존재하지 않는 상품 분류입니다.</p>
      <Link to="/shop">전체 상품 보기</Link>
    </section>
  )
  return <Catalog selection={selection} />
}

function Catalog({ selection }: { selection: NonNullable<ReturnType<typeof resolveShopCategory>> }) {
  const [search, setSearch] = useSearchParams()
  const { page, sort } = shopQuery(search)
  const catalog = usePublicProductModels({ category: selection.category, subcategory: selection.subcategory, sort, page, size: 12 })
  const data = catalog.data

  function changePage(next: number) {
    setSearch({ sort, page: String(next) })
  }

  return (
    <section className="shop-catalog" aria-labelledby="catalog-title">
      <header className="shop-catalog__heading">
        <p>NEW LIFE, NEW FORM</p>
        <h1 id="catalog-title">SHOP</h1>
      </header>

      <nav className="shop-catalog__categories" aria-label="상품 카테고리">
        {shopCategories.map((item) => (
          <Link key={item.label} to={`${shopPath(item.category)}?sort=${sort}`} aria-current={item.category === selection.category ? 'page' : undefined}>
            {item.label}
          </Link>
        ))}
      </nav>
      {selection.children.length > 0 ? (
        <nav className="shop-catalog__subcategories" aria-label="상품 하위 카테고리">
          <Link to={`${shopPath(selection.category)}?sort=${sort}`} aria-current={!selection.subcategory ? 'page' : undefined}>ALL</Link>
          {selection.children.map((child) => (
            <Link key={child} to={`${shopPath(selection.category, child)}?sort=${sort}`} aria-current={child === selection.subcategory ? 'page' : undefined}>{child}</Link>
          ))}
        </nav>
      ) : null}

      <div className="shop-catalog__toolbar">
        <p aria-live="polite">{selection.subcategory ?? selection.label} <span>{data && !catalog.isError ? `${price.format(data.totalElements)}개 상품` : '—'}</span></p>
        <label>정렬
          <select value={sort} onChange={(event) => setSearch({ sort: event.target.value, page: '0' })}>
            <option value="LATEST">최신순</option>
            <option value="PRICE_ASC">가격 낮은순</option>
            <option value="PRICE_DESC">가격 높은순</option>
          </select>
        </label>
      </div>

      <div aria-busy={catalog.isFetching}>
        {catalog.isPending ? (
          <div className="shop-catalog__state" role="status"><p>상품을 불러오는 중</p><div className="shop-catalog__skeleton" aria-hidden="true" /></div>
        ) : catalog.isError ? (
          <div className="shop-catalog__state" role="alert"><h2>상품을 불러오지 못했습니다</h2><p>잠시 후 다시 시도해 주세요.</p><button type="button" onClick={() => void catalog.refetch()}>다시 시도</button></div>
        ) : data && page > 0 && page >= data.totalPages ? (
          <div className="shop-catalog__state" role="status"><h2>요청한 페이지에 상품이 없습니다</h2><button type="button" onClick={() => changePage(0)}>첫 페이지 보기</button></div>
        ) : !data || data.content.length === 0 ? (
          <div className="shop-catalog__state" role="status"><h2>판매 중인 상품이 없습니다</h2><p>브랜드의 새로운 상품을 준비하고 있습니다.</p></div>
        ) : (
          <ul className="shop-catalog__grid" aria-label="상품 목록">
            {data.content.map((product) => <li key={product.productModelId}><CatalogCard product={product} /></li>)}
          </ul>
        )}
      </div>

      {!catalog.isError && data && data.totalPages > 1 && page < data.totalPages ? (
        <nav className="shop-catalog__pagination" aria-label="상품 페이지">
          <button type="button" aria-label="이전 페이지" disabled={data.page === 0} onClick={() => changePage(data.page - 1)}><ArrowLeft aria-hidden="true" /></button>
          {paginationWindow(data.page, data.totalPages).map((number) => (
            <button key={number} type="button" aria-label={`${number + 1}페이지`} aria-current={number === data.page ? 'page' : undefined} onClick={() => changePage(number)}>{number + 1}</button>
          ))}
          <button type="button" aria-label="다음 페이지" disabled={!data.hasNext} onClick={() => changePage(data.page + 1)}><ArrowRight aria-hidden="true" /></button>
        </nav>
      ) : null}
    </section>
  )
}

/** 공개 상세 페이지가 준비되기 전에는 링크·클릭 효과 없이 표시만 한다. */
function CatalogCard({ product }: { product: PublicProductModel }) {
  const src = publicProductImageUrl(product.thumbnailUrl)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  return (
    <article className="shop-catalog__card" aria-label={`${product.name} 상품`}>
      <div className="shop-catalog__image">
        {src && failedSrc !== src ? <img src={src} alt={`${product.brandName} ${product.name}`} loading="lazy" onError={() => setFailedSrc(src)} /> : <span className="shop-catalog__image-fallback">이미지 준비 중</span>}
        {product.stockQuantity === 0 ? <span className="shop-catalog__sold-out">품절</span> : null}
      </div>
      <p className="shop-catalog__brand">{product.brandName}</p>
      <h2>{product.name}</h2>
      <p className="shop-catalog__price">₩ {price.format(product.price)}</p>
    </article>
  )
}
