import { useNavigate } from 'react-router-dom'
import { ConsumerMyPageView } from '../../features/user/components/ConsumerMyPageView.tsx'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { useConsumerMyPage } from '../../features/user/hooks/useConsumerMyPage.ts'

/**
 * 소비자 마이페이지.
 *
 * 주문 현황·최근 주문·기본 배송지가 모두 실제 데이터다. 조립은 `useConsumerMyPage` 가
 * 한다 — 화면 하나를 그리는 데 필요한 요청이 셋이라, 그 조합을 페이지에 늘어놓으면
 * 로딩·실패 판정이 화면 쪽으로 새어 나온다.
 */
export function ConsumerMyPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { id: user.id, name: user.name, email: user.email } : null
  const myPage = useConsumerMyPage(profile)

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
