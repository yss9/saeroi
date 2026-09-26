import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Input, Loading } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { useConsumerMyPage } from '../../features/user/hooks/useConsumerMyPage.ts'
import { useMyConsumerProfile } from '../../features/user/hooks/useMyConsumerProfile.ts'
import { useUpdateConsumerProfile } from '../../features/user/hooks/useUpdateConsumerProfile.ts'
// 비밀번호 변경은 역할과 무관한 계정 기능(PUT /v1/users/me/password)이라 사업자 화면과
// 같은 훅을 쓴다 — 콘텐츠가 소비자 전용이 아니라 두 벌로 나눌 이유가 없다.
import { useChangePassword } from '../../features/company/hooks/useChangePassword.ts'
import { formatPhoneNumber, MOBILE_PHONE_PATTERN } from '../../utils/phone.ts'
import { toErrorMessage } from '../../utils/errors.ts'

type EditableField = 'name' | 'phone'

/**
 * 개인정보확인/수정.
 *
 * **2026-09-18 — 실제 API에 연결했다.** 이전에는 프론트 로컬 state만 바꾸고 끝나 저장을
 * 눌러도 서버에 남지 않았다 — 새로고침하거나 마이페이지 홈으로 돌아가면 옛 값이
 * 그대로였다. 지금은 `PUT /v1/consumers/me`(이름·연락처)와
 * `PUT /v1/users/me/password`(비밀번호)로 실제 저장한다.
 *
 * **비밀번호 확인 게이트는 여전히 목업이다** — 본인 확인 API가 없어 값을 서버로 보내
 * 검증하지 않는다. 빈 값만 막고, 그 외에는 통과시킨다.
 */
export function ConsumerProfilePage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null
  const myPageProfile = user ? { id: user.id, name: user.name, email: user.email } : null
  const myPage = useConsumerMyPage(myPageProfile)
  const defaultAddress = myPage.data?.defaultAddress ?? null

  const myProfile = useMyConsumerProfile()
  const updateProfile = useUpdateConsumerProfile()
  const changePassword = useChangePassword()

  const [unlocked, setUnlocked] = useState(false)
  const [gatePassword, setGatePassword] = useState('')
  const [gateError, setGateError] = useState<string | null>(null)

  const [editingField, setEditingField] = useState<EditableField | null>(null)
  const [nameDraft, setNameDraft] = useState('')
  const [phoneDraft, setPhoneDraft] = useState('')
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
    if (!myProfile.data) return
    if (field === 'name') setNameDraft(myProfile.data.name)
    if (field === 'phone') {
      setPhoneDraft(myProfile.data.phone)
      setPhoneError(null)
    }
    setEditingField(field)
  }

  function saveField(field: EditableField) {
    if (!myProfile.data) return

    if (field === 'phone') {
      if (phoneDraft.trim() === '') {
        setPhoneError('전화번호를 입력해 주세요.')
        return
      }
      if (!MOBILE_PHONE_PATTERN.test(phoneDraft)) {
        setPhoneError('올바른 휴대전화 번호를 입력해 주세요.')
        return
      }
      setPhoneError(null)
    }

    // 서버가 이름·연락처를 한 요청으로만 받는다 — 한쪽만 고치는 화면이어도 나머지 칸은
    // 지금 값을 그대로 담아 보낸다.
    updateProfile.mutate(
      {
        name: field === 'name' ? nameDraft : myProfile.data.name,
        phone: field === 'phone' ? phoneDraft : myProfile.data.phone,
      },
      {
        onSuccess: () => {
          setEditingField(null)
          setSavedMessage('회원 정보가 저장되었습니다.')
        },
      },
    )
  }

  function handlePasswordSubmit(event: FormEvent) {
    event.preventDefault()
    if (newPassword !== newPasswordConfirm) {
      setPasswordError('새 비밀번호가 서로 일치하지 않습니다.')
      return
    }
    setPasswordError(null)
    changePassword.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          setCurrentPassword('')
          setNewPassword('')
          setNewPasswordConfirm('')
          setSavedMessage('비밀번호가 변경되었습니다.')
        },
      },
    )
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
        ) : myProfile.isPending ? (
          <Loading label="개인정보를 불러오는 중" variant="skeleton" rows={3} />
        ) : myProfile.isError || !myProfile.data ? (
          <ErrorMessage
            error={myProfile.error ?? new Error('개인정보를 확인할 수 없습니다.')}
            onRetry={() => void myProfile.refetch()}
          />
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
                      <Button loading={updateProfile.isPending} onClick={() => saveField('name')}>
                        저장
                      </Button>
                      <Button variant="secondary" onClick={() => setEditingField(null)}>
                        취소
                      </Button>
                    </div>
                    {updateProfile.isError && (
                      <p className="ui-field__error" role="alert">
                        {toErrorMessage(updateProfile.error)}
                      </p>
                    )}
                  </dd>
                ) : (
                  <dd>
                    <span>{myProfile.data.name}</span>
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
                      <Button loading={updateProfile.isPending} onClick={() => saveField('phone')}>
                        저장
                      </Button>
                      <Button variant="secondary" onClick={() => setEditingField(null)}>
                        취소
                      </Button>
                    </div>
                    {updateProfile.isError && (
                      <p className="ui-field__error" role="alert">
                        {toErrorMessage(updateProfile.error)}
                      </p>
                    )}
                  </dd>
                ) : (
                  <dd>
                    <span>{myProfile.data.phone || '등록된 연락처가 없습니다'}</span>
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
                    {changePassword.isError && (
                      <p className="ui-field__error" role="alert">
                        {toErrorMessage(changePassword.error)}
                      </p>
                    )}
                    <Button type="submit" loading={changePassword.isPending}>
                      비밀번호 변경
                    </Button>
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
