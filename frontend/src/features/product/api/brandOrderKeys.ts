import { createQueryKeys } from '../../../utils/queryKeys.ts'

/**
 * 브랜드가 보는 제품 주문 Query Key.
 *
 * 구매자 목록(`consumerOrderKeys`)과 나눈다. 같은 주문이라도 응답이 다르고(배송지 유무)
 * 보는 사람이 다르다 — 키를 합치면 한쪽을 비울 때 다른 쪽까지 다시 받는다.
 */
export const brandOrderKeys = createQueryKeys('brand-product-order')
