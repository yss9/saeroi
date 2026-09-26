import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import type { AppError } from '../../../utils/errors.ts'
import { login } from '../api/authApi.ts'

/**
 * 본인 확인 — 지금 쓰는 비밀번호를 한 번 더 받는다.
 *
 * **로그인한 채로 자리를 비운 사이를 막는 칸이다.** 회사 정보는 대표자·연락처·주소처럼
 * 거래 상대가 보고 판단하는 값이라, 화면만 열려 있으면 누구나 바꿀 수 있게 두지 않는다.
 *
 * ⚠️ **확인은 로그인 요청으로 한다.** 서버에 "비밀번호만 맞는지" 를 묻는 경로가 아직 없어
 * (`/v1/auth/login` 이 유일하다) 같은 계정으로 다시 로그인해 보고 그 응답을 버린다.
 * 토큰이 새로 발급되는 부작용이 있으므로, 백엔드에 확인 전용 경로가 생기면 그쪽으로
 * 옮긴다 — `POST /v1/users/me/password/verify` 를 함께 요청해 두었다.
 */
export function PasswordConfirm({
  email,
  title = '본인 확인',
  description,
  submitLabel = '확인',
  onConfirmed,
  onCancel,
}: {
  /** 지금 로그인한 계정. 사용자가 적는 값이 아니다 — 비밀번호만 묻는다. */
  email: string
  title?: string
  description?: string
  submitLabel?: string
  onConfirmed: () => void
  onCancel?: () => void
}) {
  const [password, setPassword] = useState('')
  const verify = useMutation<unknown, AppError, string>({
    // 응답(토큰)은 쓰지 않는다. 여기서 알고 싶은 것은 "맞는가" 하나다.
    mutationFn: (value) => login({ email, password: value }),
    onSuccess: onConfirmed,
  })

  return (
    <form
      className="password-confirm"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        if (password.trim() === '') return
        verify.mutate(password)
      }}
    >
      <div className="password-confirm__heading">
        <h2>{title}</h2>
        {description !== undefined && <p>{description}</p>}
      </div>

      <Input
        label="비밀번호"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
      />

      {verify.isError && <ErrorMessage error={verify.error} />}

      <div className="password-confirm__actions">
        {onCancel !== undefined && (
          <Button type="button" variant="secondary" onClick={onCancel} disabled={verify.isPending}>
            취소
          </Button>
        )}
        <Button type="submit" loading={verify.isPending} disabled={password.trim() === ''}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
