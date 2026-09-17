import { describe, expect, it } from 'vitest'
import { orderGroupRedirect } from './orderGroupRedirect.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
const error = (details: unknown, code = 'PRODUCT_ORDER_BELONGS_TO_GROUP') => createAppError(ERROR_KIND.API_ERROR, { status: 409, code, details })
describe('single-order server group redirect', () => {
  it('targets the normal group screen, never the automatic payment return', () => {
    expect(orderGroupRedirect(error({ orderGroupId: 3, orderGroupCode: 'ORG-3' }))).toBe('/order-groups/3')
  })
  it.each([null, [], {}, { orderGroupId: '3' }, { orderGroupId: -1 }, { orderGroupId: 0 }, { orderGroupId: 1.5 }])('ignores malformed details %j', details => {
    expect(orderGroupRedirect(error(details))).toBeNull()
  })
  it('does not redirect other business failures', () => {
    expect(orderGroupRedirect(error({ orderGroupId: 3 }, 'PAYMENT_STATE_INVALID'))).toBeNull()
  })
})
