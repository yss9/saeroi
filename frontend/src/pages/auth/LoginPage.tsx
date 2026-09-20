import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Input } from '../../components/common/index.ts'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'
import { useLogin } from '../../features/auth/hooks/useLogin.ts'
import type { LoginRequest } from '../../features/auth/types/auth.types.ts'
import { getRoleHomePath } from '../../features/auth/utils/roleHomePath.ts'
import { isBusinessUserRole } from '../../features/company/types/companyAccount.types.ts'

interface LocationState {
  from?: { pathname: string }
}

/**
 * 로그인 뒤에 갈 곳.
 *
 * **사업자는 언제나 회사 정보다.** 보던 화면으로 되돌리는 것이 친절해 보이지만, 사업자가
 * 로그인 화면을 만나는 경우는 대개 세션이 끊긴 때다 — 그때 되돌아간 화면은 방금 실패한
 * 요청을 다시 부르고, 사용자는 자기가 어디에 있었는지보다 무엇을 할 차례인지를 먼저 봐야
 * 한다. 사이드바가 모든 업무를 들고 있어 한 번 더 누르는 비용도 작다.
 *
 * **소비자는 되돌린다.** 랜딩에서 "쇼핑" 을 눌렀다가 로그인으로 넘어온 경우가 그것이고
 * (`Header` · `ShopShowcase` 가 그 자리를 `state.from` 으로 넘긴다), 되돌리지 않으면
 * 사려던 물건을 다시 찾아야 한다.
 */
function landingPath(role: Parameters<typeof getRoleHomePath>[0], from?: string): string {
  const home = getRoleHomePath(role)
  return isBusinessUserRole(role) ? home : (from ?? home)
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

  const from = (location.state as LocationState | null)?.from?.pathname

  const onSubmit = handleSubmit((values) => {
    mutate(values, {
      onSuccess: ({ user }) => navigate(landingPath(user.role, from), { replace: true }),
    })
  })

  return (
    <AuthLayout
      eyebrow="LOGIN SAEROI"
      title="WELCOME BACK"
      description="로그인하고 이어서 둘러보세요."
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
