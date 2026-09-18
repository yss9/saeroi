import { createQueryKeys } from '../../../utils/queryKeys.ts'

/**
 * 소비자 개인정보 Query Key. `me`는 "내 개인정보" 단일 리소스라 `detail(id)` 대신
 * 별도 키를 둔다 (`companyKeys.me()`와 같은 이유).
 */
const keys = createQueryKeys('consumerProfile')

export const consumerProfileKeys = {
  ...keys,
  me: () => [...keys.all, 'me'] as const,
} as const
