import { useNavigate } from 'react-router-dom'
import { ConsumerMyPageView } from '../../features/user/components/ConsumerMyPageView.tsx'
import { createConsumerMyPageMock } from '../../features/user/mocks/consumerMyPage.mock.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'

export function ConsumerMyPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()

  if (!user) {
    return (
      <ConsumerMyPageView
        state={{ status: 'error', error: new Error('로그인 정보를 확인할 수 없습니다.') }}
      />
    )
  }

  const data = createConsumerMyPageMock({
    id: user.id,
    name: user.name,
    email: user.email,
  })

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
