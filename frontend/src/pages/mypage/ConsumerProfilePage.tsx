import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button, Input } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { useConsumerMyPage } from '../../features/user/hooks/useConsumerMyPage.ts'
import { formatPhoneNumber, MOBILE_PHONE_PATTERN } from '../../utils/phone.ts'

type EditableField = 'name' | 'phone'

/**
 * 개인정보확인/수정.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 회원 정보 수정·비밀번호 변경 API가
 * 아직 없어, 저장을 눌러도 서버로 나가지 않는다 — 화면 확인용 성공 문구만 보여준다.
 * 비밀번호는 값을 어디에도 기록하지 않고 제출 순간 비운다.
 *
 * **비밀번호 확인 게이트(2026-09-17)도 같은 이유로 목업이다** — 본인 확인 API가 없어
 * 값을 서버로 보내 검증하지 않는다. 빈 값만 막고, 그 외에는 통과시킨다.
 */
export function ConsumerProfilePage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null
  const myPageProfile = user ? { id: user.id, name: user.name, email: user.email } : null
  const myPage = useConsumerMyPage(myPageProfile)
  const defaultAddress = myPage.data?.defaultAddress ?? null

  const [unlocked, setUnlocked] = useState(false)
  const [gatePassword, setGatePassword] = useState('')
  const [gateError, setGateError] = useState<string | null>(null)

  const [name, setName] = useState(user?.name ?? '')
  const [phone, setPhone] = useState('')
  const [editingField, setEditingField] = useState<EditableField | null>(null)
  const [nameDraft, setNameDraft] = useState(name)
  const [phoneDraft, setPhoneDraft] = useState(phone)
  const [phoneError, setPhoneError] = useState<string | null>(null)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [savedMessage, setSavedMessage] = useState<string | null>(null)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  function handleGateSubmit(event: FormEvent) {
    event.preventDefault()
    if (gatePassword.trim() === '') {
      setGateError('비밀번호를 입력해 주세요.')
      return
    }
    setGateError(null)
    setGatePassword('')
    setUnlocked(true)
  }

  function startEdit(field: EditableField) {
    if (field === 'name') setNameDraft(name)
    if (field === 'phone') {
      setPhoneDraft(phone)
      setPhoneError(null)
    }
    setEditingField(field)
  }

  function saveField(field: EditableField) {
    if (field === 'phone') {
      if (phoneDraft !== '' && !MOBILE_PHONE_PATTERN.test(phoneDraft)) {
        setPhoneError('올바른 휴대전화 번호를 입력해 주세요.')
        return
      }
      setPhoneError(null)
      setPhone(phoneDraft)
    }
    if (field === 'name') setName(nameDraft)
    setEditingField(null)
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

        {!unlocked ? (
          <form className="consumer-profile-edit__gate" onSubmit={handleGateSubmit}>
            <p className="consumer-profile-edit__gate-notice">
              개인정보 보호를 위해 비밀번호를 확인한 뒤에 열람할 수 있습니다.
            </p>
            <Input
              label="현재 비밀번호"
              type="password"
              error={gateError ?? undefined}
              value={gatePassword}
              onChange={(event) => setGatePassword(event.target.value)}
            />
            <Button type="submit">확인</Button>
          </form>
        ) : (
          <>
            {savedMessage && (
              <p className="consumer-profile-edit__saved" role="status">
                {savedMessage}
              </p>
            )}

            <dl className="consumer-profile-edit__field-list">
              <div className="consumer-profile-edit__field-row">
                <dt>아이디</dt>
                <dd>
                  <span>{profile?.email}</span>
                </dd>
              </div>

              <div className="consumer-profile-edit__field-row">
                <dt>이름</dt>
                {editingField === 'name' ? (
                  <dd className="consumer-profile-edit__field-edit">
                    <Input value={nameDraft} onChange={(event) => setNameDraft(event.target.value)} />
                    <div className="consumer-profile-edit__field-edit-actions">
                      <Button onClick={() => saveField('name')}>저장</Button>
                      <Button variant="secondary" onClick={() => setEditingField(null)}>
                        취소
                      </Button>
                    </div>
                  </dd>
                ) : (
                  <dd>
                    <span>{name}</span>
                    <button
                      type="button"
                      className="consumer-profile-edit__change-btn"
                      onClick={() => startEdit('name')}
                    >
                      변경
                    </button>
                  </dd>
                )}
              </div>

              <div className="consumer-profile-edit__field-row">
                <dt>전화번호</dt>
                {editingField === 'phone' ? (
                  <dd className="consumer-profile-edit__field-edit">
                    <Input
                      type="tel"
                      inputMode="tel"
                      maxLength={13}
                      autoComplete="tel"
                      placeholder="010-1234-5678"
                      error={phoneError ?? undefined}
                      value={phoneDraft}
                      onChange={(event) => setPhoneDraft(formatPhoneNumber(event.target.value))}
                    />
                    <div className="consumer-profile-edit__field-edit-actions">
                      <Button onClick={() => saveField('phone')}>저장</Button>
                      <Button variant="secondary" onClick={() => setEditingField(null)}>
                        취소
                      </Button>
                    </div>
                  </dd>
                ) : (
                  <dd>
                    <span>{phone || '등록된 연락처가 없습니다'}</span>
                    <button
                      type="button"
                      className="consumer-profile-edit__change-btn"
                      onClick={() => startEdit('phone')}
                    >
                      변경
                    </button>
                  </dd>
                )}
              </div>

              {/* 배송지도 위 행들과 같은 줄 스타일로(2026-09-17) — 따로 박스를 만들면
                  이 목록 안에서만 배송지가 유독 다르게 생겨 눈에 튀었다. */}
              <div className="consumer-profile-edit__field-row">
                <dt>배송지</dt>
                <dd>
                  <span>
                    {defaultAddress
                      ? `${defaultAddress.recipientName} · ${defaultAddress.addressLine1}`
                      : '등록된 배송지가 없습니다'}
                  </span>
                  <Link className="consumer-text-link" to="/mypage/addresses">
                    배송지 관리
                  </Link>
                </dd>
              </div>

              {/* 비밀번호 변경도 같은 좌측 라벨 + 우측 칸 구조를 따른다(2026-09-17) —
                  왼쪽엔 "비밀번호 변경"만 적고, 오른쪽 칸에 세 줄(현재/새/새 확인)을 쌓는다. */}
              <div className="consumer-profile-edit__field-row consumer-profile-edit__field-row--password">
                <dt>비밀번호 변경</dt>
                <dd>
                  <form className="consumer-profile-edit__password-form" onSubmit={handlePasswordSubmit}>
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
                </dd>
              </div>
            </dl>
          </>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
