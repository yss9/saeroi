import { useNavigate } from 'react-router-dom'
import { ConsumerMyPageView } from '../../features/user/components/ConsumerMyPageView.tsx'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { useConsumerProfile } from '../../features/user/hooks/useConsumerProfile.ts'

/**
 * 소비자 마이페이지.
 *
 * 로그인 정보와 기본 배송지만 조회한다. 주문 조회 실패는 개인정보를 막지 않는다.
 */
export function ConsumerMyPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { id: user.id, name: user.name, email: user.email } : null
  const myPage = useConsumerProfile(profile)

  if (!user) {
    return (
      <ConsumerMyPageView
        state={{ status: 'error', error: new Error('로그인 정보를 확인할 수 없습니다.') }}
      />
    )
  }

  if (myPage.isPending) {
    return <ConsumerMyPageView state={{ status: 'loading' }} />
  }

  if (myPage.isError || !myPage.data) {
    return (
      <ConsumerMyPageView
        state={{
          status: 'error',
          error: myPage.error ?? new Error('주문 정보를 확인할 수 없습니다.'),
          onRetry: () => void myPage.refetch(),
        }}
      />
    )
  }

  return (
    <ConsumerMyPageView
      state={{ status: 'ready', data: myPage.data }}
      onSignOut={() => logout.mutate(undefined, {
        onSuccess: () => navigate('/', { replace: true }),
      })}
      signOutPending={logout.isPending}
      signOutError={logout.error}
    />
  )
}
