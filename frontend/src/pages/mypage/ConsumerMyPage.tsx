import { useNavigate } from 'react-router-dom'
import { ConsumerMyPageView } from '../../features/user/components/ConsumerMyPageView.tsx'
import { useConsumerAddresses } from '../../features/user/hooks/useConsumerAddresses.ts'
import { createConsumerMyPageMock } from '../../features/user/mocks/consumerMyPage.mock.ts'
import type { ConsumerAddress } from '../../features/user/types/consumerAddress.types.ts'
import type { ConsumerAddressSummary } from '../../features/user/types/consumerMyPage.types.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'

/**
 * 소비자 마이페이지.
 *
 * **기본 배송지만 실제 데이터다.** 주문 현황·최근 주문은 아직 서버에 API 가 없어 목데이터를
 * 쓴다 — 배송지는 API 가 생겼으므로 그 부분만 진짜로 바꾼다. 목데이터가 남은 자리를 함께
 * 지우려고 기다리면, 이미 동작하는 기능이 가짜 주소 뒤에 계속 가려진다.
 */
export function ConsumerMyPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const addresses = useConsumerAddresses()

  if (!user) {
    return (
      <ConsumerMyPageView
        state={{ status: 'error', error: new Error('로그인 정보를 확인할 수 없습니다.') }}
      />
    )
  }

  const data = {
    ...createConsumerMyPageMock({ id: user.id, name: user.name, email: user.email }),
    // 아직 못 받았거나 실패했으면 null 이다. 카드는 그때 "등록된 배송지가 없습니다" 를
    // 보여주는데, 화면 전체를 막는 것보다 낫다 — 주문 현황을 보러 온 사용자를 배송지
    // 조회 실패로 세우지 않는다.
    defaultAddress: toSummary(addresses.data?.find((item) => item.isDefault)),
  }

  return (
    <ConsumerMyPageView
      state={{ status: 'ready', data }}
      onSignOut={() => logout.mutate(undefined, {
        onSuccess: () => navigate('/', { replace: true }),
      })}
      signOutPending={logout.isPending}
      signOutError={logout.error}
    />
  )
}

/**
 * 서버 응답을 화면 View Model 로.
 *
 * 필드 이름이 다르다 (`receiverName` ↔ `recipientName`). 화면이 Backend DTO 를 직접 쓰지
 * 않기로 한 규칙 때문인데, 그 덕에 응답이 바뀌어도 카드 컴포넌트는 그대로다.
 */
function toSummary(address: ConsumerAddress | undefined): ConsumerAddressSummary | null {
  if (!address) return null
  return {
    id: address.addressId,
    recipientName: address.receiverName,
    phone: address.receiverPhone,
    postalCode: address.zipCode,
    addressLine1: address.address,
    addressLine2: address.addressDetail ?? undefined,
    isDefault: address.isDefault,
  }
}
