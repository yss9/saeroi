import { isAppError } from '../../utils/errors.ts'

/** Only the single-order ready/approve/cancel callers use this server hint. */
export function orderGroupRedirect(error: unknown): string | null {
  if (!isAppError(error) || error.code !== 'PRODUCT_ORDER_BELONGS_TO_GROUP' || error.status !== 409) return null
  const details = error.details
  if (!details || typeof details !== 'object' || !('orderGroupId' in details)) return null
  const id = details.orderGroupId
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? `/order-groups/${id}` : null
}
