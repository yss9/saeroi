import { createQueryKeys } from '../../../utils/queryKeys.ts'

/**
 * Company 도메인의 Query Key.
 *
 * `me` 는 "내 업체 정보" 단일 리소스다 — id 로 구분되는 목록이 아니라 계정당 하나뿐이라
 * `detail(id)` 대신 별도 키를 둔다.
 */
const keys = createQueryKeys('company')

export const companyKeys = {
  ...keys,
  me: () => [...keys.all, 'me'] as const,
} as const
