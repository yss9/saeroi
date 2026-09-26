import { useConsumerAddresses } from './useConsumerAddresses.ts'
import { useMyConsumerProfile } from './useMyConsumerProfile.ts'
import { toAddressSummary } from './useConsumerMyPage.ts'
import type { ConsumerMyPageData, ConsumerProfileSummary } from '../types/consumerMyPage.types.ts'

/**
 * Profile needs the authenticated identity and address book, not order availability.
 *
 * **계정 연락처는 여기서 따로 붙인다.** `profile` 인자(로그인 스냅샷)에는 전화번호가
 * 없다 — `/mypage/profile`에서 고친 값이 마이페이지 홈에도 곧바로 보이려면 정본을
 * 다시 읽어야 한다(`useMyConsumerProfile`). 실패해도 이 화면 전체를 막지 않는다 —
 * 연락처 칸만 비어 보인다.
 */
export function useConsumerProfile(profile: ConsumerProfileSummary | null) {
  const addresses = useConsumerAddresses()
  const myProfile = useMyConsumerProfile()
  const data: ConsumerMyPageData | null = profile ? {
    profile: { ...profile, phone: myProfile.data?.phone },
    defaultAddress: toAddressSummary(addresses.data?.find((address) => address.isDefault)),
    orderStages: [],
    recentOrders: [],
  } : null
  return { data, isPending: addresses.isPending, isError: false, error: null, refetch: addresses.refetch }
}
