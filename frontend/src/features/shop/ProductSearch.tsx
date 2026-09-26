import { MagnifyingGlass, ArrowRight, X } from '@phosphor-icons/react'
import { useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { usePublicProductModels } from '../product/hooks/usePublicProductModels.ts'
import { CatalogResults } from './CatalogResults.tsx'
import { shopCategories, shopPath, shopQuery } from './catalog.ts'

export function ProductSearch() {
  const [search, setSearch] = useSearchParams()
  const rawKeyword = search.get('q') ?? ''
  const keyword = rawKeyword.trim()
  function submit(value: string) {
    const next = new URLSearchParams()
    if (value.trim()) next.set('q', value.trim())
    else next.delete('q')
    next.delete('page')
    setSearch(next)
  }
  return <section className="product-search" aria-labelledby="search-title">
    <header className="product-search__heading"><p>FIND YOUR NEXT FAVORITE</p><h1 id="search-title">SEARCH</h1></header>
    <SearchForm key={rawKeyword} initialKeyword={rawKeyword} onSubmit={submit} />
    {keyword ? <SearchResults keyword={keyword} /> : <div className="product-search__before">
      <p className="product-search__eyebrow">EXPLORE BY CATEGORY</p>
      <nav aria-label="검색 전 카테고리 바로가기">{shopCategories.filter((item) => item.category).map((item) => <Link key={item.label} to={shopPath(item.category)}>{item.label}</Link>)}</nav>
      <Link className="text-link" to="/shop">전체 상품 둘러보기 <ArrowRight aria-hidden="true" /></Link>
    </div>}
  </section>
}

function SearchForm({ initialKeyword, onSubmit }: { initialKeyword: string; onSubmit: (keyword: string) => void }) {
  const [draft, setDraft] = useState(initialKeyword)
  const inputRef = useRef<HTMLInputElement>(null)
  function clear() { setDraft(''); inputRef.current?.focus() }
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); onSubmit(draft) }
  return <form className="product-search__form" role="search" onSubmit={submit}>
    <div><input ref={inputRef} id="product-search-input" type="search" aria-label="상품명 또는 브랜드명 검색" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="상품명 또는 브랜드명을 입력해 주세요" autoComplete="off" />{draft && <button className="product-search__clear" type="button" aria-label="검색어 지우기" onClick={clear}><X size={22} aria-hidden="true" /></button>}<button type="submit" aria-label="검색 실행"><MagnifyingGlass size={28} aria-hidden="true" /></button></div>
  </form>
}

function SearchResults({ keyword }: { keyword: string }) {
  const [search, setSearch] = useSearchParams()
  const { page } = shopQuery(search)
  const catalog = usePublicProductModels({ keyword, sort: 'LATEST', page, size: 12 })
  function changePage(page: number) {
    const next = new URLSearchParams()
    next.set('q', keyword)
    if (page > 0) next.set('page', String(page))
    setSearch(next)
  }
  return <div className="product-search__results">
    <p className="product-search__count" aria-live="polite">{catalog.data && !catalog.isError ? `${new Intl.NumberFormat('ko-KR').format(catalog.data.totalElements)}개 상품` : ''}</p>
    <CatalogResults catalog={catalog} page={page} onPageChange={changePage} emptyTitle="검색 결과가 없습니다" emptyDescription="다른 상품명이나 브랜드명으로 검색해 보세요." />
  </div>
}
