import { MagnifyingGlass, X } from '@phosphor-icons/react'
import { useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { usePublicProductModels } from '../product/hooks/usePublicProductModels.ts'
import { CatalogResults } from '../shop/CatalogResults.tsx'
import { CatalogPagination } from '../shop/CatalogPagination.tsx'
import { shopQuery } from '../shop/catalog.ts'
import { isAppError } from '../../utils/errors.ts'
import { BrandLogo } from './BrandLogo.tsx'
import { brandIntroduction, usePublicBrand, usePublicBrands, type BrandSort, type PublicBrand } from './publicBrands.ts'

export function BrandCatalog() {
  const [search, setSearch] = useSearchParams()
  const keyword = (search.get('q') ?? '').trim()
  const { page } = shopQuery(search)
  const sort: BrandSort = search.get('sort') === 'NAME' ? 'NAME' : 'PRODUCT_COUNT'
  const query = usePublicBrands({ keyword: keyword || undefined, sort, page, size: 8 })
  const data = query.data
  function change(name: string, value: string) {
    const next = new URLSearchParams(search)
    if (value) next.set(name, value)
    else next.delete(name)
    if (name !== 'page') next.delete('page')
    setSearch(next)
  }
  return <section className="brand-catalog" aria-labelledby="brands-title">
    <header className="shop-catalog__heading"><p>MEET THE MAKERS</p><h1 id="brands-title">BRANDS</h1></header>
    <BrandSearch key={search.get('q') ?? ''} keyword={search.get('q') ?? ''} onSubmit={(value) => change('q', value.trim())} />
    <div className="shop-catalog__toolbar">
      <p aria-live="polite">{data && !query.isError ? `${new Intl.NumberFormat('ko-KR').format(data.totalElements)}개 브랜드` : ''}</p>
      <label>정렬<select aria-label="브랜드 정렬" value={sort} onChange={(event) => change('sort', event.target.value)}><option value="PRODUCT_COUNT">상품 많은순</option><option value="NAME">이름순</option></select></label>
    </div>
    <div aria-busy={query.isFetching}>
      {query.isPending ? <BrandLoading /> : query.isError ? <BrandError retry={() => void query.refetch()} /> : data && page > 0 && page >= data.totalPages ?
        <div className="shop-catalog__state" role="status"><h2>요청한 페이지에 브랜드가 없습니다</h2><button type="button" onClick={() => change('page', '0')}>첫 페이지 보기</button></div> : !data || data.content.length === 0 ?
        <div className="shop-catalog__state" role="status"><h2>{keyword ? '검색 결과가 없습니다' : '공개된 브랜드가 없습니다'}</h2><p>{keyword ? '다른 브랜드명으로 검색해 보세요.' : '새로운 브랜드를 준비하고 있습니다.'}</p></div> :
        <ul className="brand-catalog__grid" aria-label="브랜드 목록">{data.content.map((brand) => <li key={brand.brandId}><article className="brand-card" aria-label={`${brand.brandName} 브랜드`}><Link to={`/brands/${brand.brandId}`}><BrandLogo brand={brand} /><div className="brand-card__copy"><h2>{brand.brandName}</h2><p className={brand.introduction === null ? 'brand-introduction--fallback' : undefined}>{brand.introduction ?? brandIntroduction.card}</p><span>{new Intl.NumberFormat('ko-KR').format(brand.productCount)}개 상품</span></div></Link></article></li>)}</ul>}
    </div>
    {!query.isError && data && page < data.totalPages && <CatalogPagination label="브랜드 페이지" page={data.page} totalPages={data.totalPages} hasNext={data.hasNext} onPageChange={(next) => change('page', String(next))} />}
  </section>
}

function BrandSearch({ keyword, onSubmit }: { keyword: string; onSubmit: (value: string) => void }) {
  const [draft, setDraft] = useState(keyword)
  const inputRef = useRef<HTMLInputElement>(null)
  function clear() { setDraft(''); inputRef.current?.focus() }
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); onSubmit(draft) }
  return <form className="brand-catalog__search" role="search" onSubmit={submit}><input ref={inputRef} type="search" aria-label="브랜드명 검색" placeholder="브랜드명을 입력해 주세요" value={draft} onChange={(event) => setDraft(event.target.value)} autoComplete="off" />{draft && <button className="brand-catalog__clear" type="button" aria-label="검색어 지우기" onClick={clear}><X size={22} aria-hidden="true" /></button>}<button type="submit" aria-label="브랜드 검색 실행"><MagnifyingGlass size={24} aria-hidden="true" /></button></form>
}

export function BrandDetail({ brandId }: { brandId: number | null }) {
  const query = usePublicBrand(brandId)
  if (brandId === null || (query.isError && isAppError(query.error) && query.error.status === 404)) return <section className="brand-detail"><div className="shop-catalog__state"><p>BRAND NOT FOUND</p><h1>브랜드를 찾을 수 없습니다</h1><p>현재 공개된 상품이 없거나 존재하지 않는 브랜드입니다.</p><Link to="/brands">전체 브랜드 보기</Link></div></section>
  if (query.isPending) return <section className="brand-detail"><BrandLoading /></section>
  if (query.isError) return <section className="brand-detail"><BrandError retry={() => void query.refetch()} /></section>
  return <BrandBody brand={query.data} />
}

function BrandBody({ brand }: { brand: PublicBrand }) {
  const [search, setSearch] = useSearchParams()
  const { page } = shopQuery(search)
  const products = usePublicProductModels({ brandId: brand.brandId, page, size: 12 })
  function changePage(nextPage: number) {
    const next = new URLSearchParams()
    if (nextPage > 0) next.set('page', String(nextPage))
    setSearch(next)
  }
  return <section className="brand-detail" aria-labelledby="brand-name">
    <nav className="brand-detail__breadcrumb" aria-label="브랜드 경로"><Link to="/brands">BRANDS</Link><span>/</span><span>{brand.brandName}</span></nav>
    <header className="brand-detail__hero"><BrandLogo brand={brand} /><div><p className="brand-detail__eyebrow">MEET THE MAKER</p><h1 id="brand-name">{brand.brandName}</h1><p className={`brand-detail__introduction${brand.introduction === null ? ' brand-introduction--fallback' : ''}`}>{(brand.introduction ?? brandIntroduction.detail).replace(/([.!?])\s+(?=\S)/g, '$1\n')}</p><span className="brand-detail__total">{new Intl.NumberFormat('ko-KR').format(brand.productCount)}개 상품</span></div></header>
    <section className="brand-detail__products" aria-labelledby="brand-products-title"><div className="brand-detail__products-heading"><h2 id="brand-products-title">이 브랜드의 상품</h2><p aria-live="polite">{products.data && !products.isError ? `${new Intl.NumberFormat('ko-KR').format(products.data.totalElements)}개 상품` : ''}</p></div><CatalogResults catalog={products} page={page} onPageChange={changePage} /></section>
  </section>
}

function BrandLoading() { return <div className="shop-catalog__state" role="status"><p>브랜드를 불러오는 중</p><div className="shop-catalog__skeleton" aria-hidden="true" /></div> }
function BrandError({ retry }: { retry: () => void }) { return <div className="shop-catalog__state" role="alert"><h2>브랜드를 불러오지 못했습니다</h2><p>잠시 후 다시 시도해 주세요.</p><button type="button" onClick={retry}>다시 시도</button></div> }
