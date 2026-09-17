import { Link, useParams, useSearchParams } from 'react-router-dom'
import { usePublicProductModels } from '../product/hooks/usePublicProductModels.ts'
import { resolveShopCategory, shopCategories, shopPath, shopQuery } from './catalog.ts'
import { CatalogResults } from './CatalogResults.tsx'

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

      <CatalogResults catalog={catalog} page={page} onPageChange={changePage} />
    </section>
  )
}
