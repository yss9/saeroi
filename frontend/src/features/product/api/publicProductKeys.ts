import { createQueryKeys } from '../../../utils/queryKeys.ts'

/** 인증용 제품 관리 캐시와 섞이지 않는 공개 상품 전용 Query Key. */
export const publicProductKeys = createQueryKeys('public-product')
