import { Eye, EyeSlash } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import { useChangePassword } from '../hooks/useChangePassword.ts'

/**
 * 서버가 가입에서 쓰는 규칙 그대로. 느슨하게 두면 다 적고 저장한 뒤에야 400 을 보고,
 * 빡빡하게 두면 서버가 받아 줄 값을 화면이 거절한다.
 */
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,}$/

interface PasswordFormValues {
  currentPassword: string
  newPassword: string
  newPasswordConfirm: string
}

/**
 * 비밀번호 변경.
 *
 * **업체 정보 저장과 따로 보낸다.** 한 번에 묶으면 비밀번호가 틀렸을 때 주소 수정까지
 * 되돌려야 하고, 반대로 주소가 틀렸을 때 비밀번호만 바뀌는 상태도 생긴다. 서버도 다른
 * 요청이다.
 *
 * **현재 비밀번호를 묻는다.** 로그인한 채로 자리를 비운 사이 누군가 계정을 가져가는 것을
 * 막는 값이라, 편의를 위해 뺄 수 있는 칸이 아니다.
 *
 * 성공한 뒤 칸을 비우고 그 사실만 알린다. 화면을 옮기지 않는다 — 비밀번호를 바꾸러 온
 * 사람은 대개 정보 수정을 하러 온 김에 들른 것이고, 여기서 화면이 바뀌면 하던 일을 잃는다.
 */
export function PasswordChangeForm() {
  const change = useChangePassword()
  const [visible, setVisible] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PasswordFormValues>({
    defaultValues: { currentPassword: '', newPassword: '', newPasswordConfirm: '' },
  })

  return (
    <form
      className="password-form"
      noValidate
      onSubmit={handleSubmit((values) => {
        change.mutate(
          { currentPassword: values.currentPassword, newPassword: values.newPassword },
          // 성공했으면 칸을 비운다. 남겨 두면 다음 사람이 화면 앞에 앉았을 때 그대로 보인다.
          { onSuccess: () => reset() },
        )
      })}
    >
      <div className="password-form__heading">
        <h2>비밀번호 변경</h2>
        <p>영문, 숫자, 특수문자를 포함해 8자 이상.</p>
      </div>

      <Input
        label="현재 비밀번호"
        type="password"
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...register('currentPassword', { required: '현재 비밀번호를 입력해 주세요.' })}
      />

      <div className="password-form__field">
        <Input
          label="새 비밀번호"
          type={visible ? 'text' : 'password'}
          autoComplete="new-password"
          error={errors.newPassword?.message}
          {...register('newPassword', {
            required: '새 비밀번호를 입력해 주세요.',
            pattern: {
              value: passwordPattern,
              message: '영문, 숫자, 특수문자를 포함해 8자 이상 입력해 주세요.',
            },
            // 서버도 같은 것을 거절하겠지만, 바꾸지 않은 값을 보내고 기다릴 이유가 없다.
            validate: (value, values) =>
              value !== values.currentPassword || '지금 쓰는 비밀번호와 다르게 정해 주세요.',
          })}
        />
        {/* 새로 정하는 값이라 눈으로 확인할 수 있어야 한다 — 현재 비밀번호는 아는 값이라 두지 않는다. */}
        <button
          className="password-form__toggle"
          type="button"
          aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'}
          onClick={() => setVisible((shown) => !shown)}
        >
          {visible ? <EyeSlash aria-hidden="true" /> : <Eye aria-hidden="true" />}
        </button>
      </div>

      <Input
        label="새 비밀번호 확인"
        type={visible ? 'text' : 'password'}
        autoComplete="new-password"
        error={errors.newPasswordConfirm?.message}
        {...register('newPasswordConfirm', {
          required: '새 비밀번호를 다시 입력해 주세요.',
          // 폼 값을 두 번째 인자로 받는다 — watch 로 구독하면 글자마다 폼 전체가 다시 그려진다.
          validate: (value, values) =>
            value === values.newPassword || '비밀번호가 일치하지 않습니다.',
        })}
      />

      {change.isError && <ErrorMessage error={change.error} />}
      {change.isSuccess && (
        <p className="password-form__done" role="status">
          비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 쓰세요.
        </p>
      )}

      <div className="password-form__actions">
        <Button type="submit" loading={change.isPending}>
          비밀번호 변경
        </Button>
      </div>
    </form>
  )
}
