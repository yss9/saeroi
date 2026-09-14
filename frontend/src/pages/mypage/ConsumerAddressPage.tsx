import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { ConsumerAddressBook } from '../../features/user/components/ConsumerAddressBook.tsx'

/**
 * 배송지 관리.
 *
 * **마이페이지와 같은 껍데기를 쓴다.** 두 화면이 다른 껍데기를 쓰면 오갈 때마다 머리말과
 * 사이드바가 바뀐다 — 사용자는 다른 사이트로 넘어간 것처럼 읽는다.
 *
 * 마이페이지 홈의 기본 배송지 카드에서 "편집" 으로 들어온다. 사이드바에도 있다.
 *
 * 주문 화면이 생기면 결제 단계에서도 이 목록을 고르게 된다 — 그때 이 화면은 그대로 두고
 * 선택 UI 만 따로 만든다. 관리와 선택은 사용자가 하려는 일이 다르다.
 */
export function ConsumerAddressPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  return (
    <ConsumerAccountLayout
      current="addresses"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card">
        <ConsumerAddressBook />
      </section>
    </ConsumerAccountLayout>
  )
}
