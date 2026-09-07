import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Input } from '../../components/common/index.ts'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'
import { useLogin } from '../../features/auth/hooks/useLogin.ts'
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
    <AuthLayout
      eyebrow="WELCOME BACK"
      title="다시 만나 반가워요"
      description="SAEROI 계정으로 로그인하고 나의 순환 소비를 이어가세요."
    >
      <form className="auth-entry-form" onSubmit={onSubmit} noValidate>
        <Input
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="이메일 주소를 입력해 주세요."
          error={errors.email?.message}
          {...register('email', { required: '이메일을 입력해 주세요.' })}
        />
        <Input
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          placeholder="비밀번호를 입력해 주세요."
          error={errors.password?.message}
          {...register('password', { required: '비밀번호를 입력해 주세요.' })}
        />

        {isError && <ErrorMessage error={error} />}

        <Button type="submit" loading={isPending} fullWidth>
          로그인
        </Button>
      </form>

      <p className="auth-entry__switch">
        아직 계정이 없으신가요? <Link to="/signup">회원가입</Link>
      </p>
    </AuthLayout>
  )
}
