import { useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Input } from '../../components/common/index.ts'
import { useLogin } from '../../features/auth/hooks/useLogin.ts'
import { WalletButton } from '../../features/wallet/components/WalletButton.tsx'
import type { LoginRequest } from '../../features/auth/types/auth.types.ts'

interface LocationState {
  from?: { pathname: string }
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { mutate, isPending, isError, error } = useLogin()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginRequest>()

  // ProtectedRoute 가 넘겨준 원래 목적지. 없으면 홈으로.
  const from = (location.state as LocationState | null)?.from?.pathname ?? '/'

  const onSubmit = handleSubmit((values) => {
    mutate(values, { onSuccess: () => navigate(from, { replace: true }) })
  })

  return (
    <main className="page">
      <h1>로그인</h1>

      <form onSubmit={onSubmit} noValidate>
        <Input
          label="이메일"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email', { required: '이메일을 입력해 주세요.' })}
        />
        <Input
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password', { required: '비밀번호를 입력해 주세요.' })}
        />

        {isError && <ErrorMessage error={error} />}

        <Button type="submit" loading={isPending} fullWidth>
          로그인
        </Button>
      </form>

      <section>
        <h2>지갑으로 로그인</h2>
        {/* TODO(6번): 지갑 연결 후 useWalletLogin() 을 붙인다. 지금은 연결까지만 동작한다. */}
        <WalletButton />
      </section>
    </main>
  )
}
