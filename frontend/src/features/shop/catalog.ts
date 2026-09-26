import type { ProductCategory } from '../product/types/product.types.ts'
import type { ProductSubcategory, PublicProductSort } from '../product/types/publicProduct.types.ts'

export const shopCategories: { label: string; category?: ProductCategory; children: ProductSubcategory[] }[] = [
  { label: 'ALL', children: [] },
  { label: 'CLOTHING', category: 'CLOTHING', children: ['TOP', 'OUTER', 'PANTS', 'SKIRT', 'DRESS'] },
  { label: 'BAG', category: 'BAG', children: ['TOTE', 'SHOULDER', 'CROSSBODY', 'BACKPACK'] },
  { label: 'WALLET', category: 'WALLET', children: [] },
  { label: 'ACCESSORY', category: 'ACCESSORY', children: [] },
  { label: 'HOME', category: 'HOME', children: [] },
]

export function shopPath(category?: ProductCategory, subcategory?: ProductSubcategory) {
  return `/shop${category ? `/${category.toLowerCase()}` : ''}${subcategory ? `/${subcategory.toLowerCase()}` : ''}`
}

/** URLは小文字のみ。ETC・未知の分類・不適切な親子の組み合わせは404。 */
export function resolveShopCategory(category?: string, subcategory?: string) {
  const entry = category === undefined ? shopCategories[0] : shopCategories.find((item) => item.category?.toLowerCase() === category)
  if (!entry) return null
  const child = subcategory === undefined ? undefined : entry.children.find((item) => item.toLowerCase() === subcategory)
  if (subcategory !== undefined && !child) return null
  return { ...entry, subcategory: child }
}

export function shopQuery(search: URLSearchParams) {
  const rawPage = search.get('page') ?? '0'
  const value = Number(rawPage)
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(value) ? value : 0
  const rawSort = search.get('sort')
  const sort: PublicProductSort = rawSort === 'PRICE_ASC' || rawSort === 'PRICE_DESC' ? rawSort : 'LATEST'
  return { page, sort }
}

export function paginationWindow(page: number, total: number) {
  const start = Math.max(0, Math.min(page - 2, total - 5))
  return Array.from({ length: Math.min(5, total) }, (_, index) => start + index)
}
