import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Input } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'

/**
 * 개인정보확인/수정.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 회원 정보 수정·비밀번호 변경 API가
 * 아직 없어, 저장을 눌러도 서버로 나가지 않는다 — 화면 확인용 성공 문구만 보여준다.
 * 비밀번호는 값을 어디에도 기록하지 않고 제출 순간 비운다.
 */
export function ConsumerProfilePage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const [name, setName] = useState(user?.name ?? '')
  const [phone, setPhone] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [savedMessage, setSavedMessage] = useState<string | null>(null)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  function handleProfileSubmit(event: FormEvent) {
    event.preventDefault()
    setSavedMessage('회원 정보가 저장되었습니다.')
  }

  function handlePasswordSubmit(event: FormEvent) {
    event.preventDefault()
    if (newPassword !== newPasswordConfirm) {
      setPasswordError('새 비밀번호가 서로 일치하지 않습니다.')
      return
    }
    setPasswordError(null)
    setCurrentPassword('')
    setNewPassword('')
    setNewPasswordConfirm('')
    setSavedMessage('비밀번호가 변경되었습니다.')
  }

  return (
    <ConsumerAccountLayout
      current="profile"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card" aria-labelledby="profile-edit-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">MY INFO</p>
            <h2 id="profile-edit-title">개인정보확인/수정</h2>
          </div>
        </div>

        {savedMessage && (
          <p className="consumer-profile-edit__saved" role="status">
            {savedMessage}
          </p>
        )}

        <form className="consumer-profile-edit__form" onSubmit={handleProfileSubmit}>
          <Input label="이름" value={name} onChange={(event) => setName(event.target.value)} />
          <Input label="이메일" value={profile?.email ?? ''} disabled />
          <Input
            label="연락처"
            placeholder="010-0000-0000"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
          />
          <Button type="submit">저장</Button>
        </form>

        <h3 className="consumer-profile-edit__subheading">비밀번호 변경</h3>
        <form className="consumer-profile-edit__form" onSubmit={handlePasswordSubmit}>
          <Input
            label="현재 비밀번호"
            type="password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
          <Input
            label="새 비밀번호"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <Input
            label="새 비밀번호 확인"
            type="password"
            error={passwordError ?? undefined}
            value={newPasswordConfirm}
            onChange={(event) => setNewPasswordConfirm(event.target.value)}
          />
          <Button type="submit">비밀번호 변경</Button>
        </form>
      </section>
    </ConsumerAccountLayout>
  )
}
