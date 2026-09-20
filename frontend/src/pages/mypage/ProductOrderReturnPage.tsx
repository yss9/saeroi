import { OrderGroupPage } from '../cart/OrderGroupPage.tsx'

/** PG interruption keeps the existing order; only explicit cancellation deletes it. */
export function ProductOrderReturnPage() {
  return <OrderGroupPage single returning />
}
