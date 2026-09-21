import { useState, type FormEvent } from 'react'
import { Button, Input, Select, type SelectOption } from '../../components/common/index.ts'
import { Footer } from '../landing/components/Footer.tsx'
import { Header } from '../landing/components/Header.tsx'
import './support.css'
import '../landing/landing.css'

const INQUIRY_TYPE_OPTIONS: readonly SelectOption[] = [
  { value: 'ORDER', label: '주문·결제' },
  { value: 'DELIVERY', label: '배송' },
  { value: 'EXCHANGE_RETURN', label: '교환·반품' },
  { value: 'ACCOUNT', label: '회원·계정' },
  { value: 'ETC', label: '기타' },
]

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * 공개 문의하기.
 *
 * 로그인 여부와 무관하게 누구나 볼 수 있는 안내 + 문의 폼이다. 로그인한 소비자의
 * 마이페이지 문의하기(`/mypage/inquiries/new`)와는 별개 화면이다 — 그건 내 주문
 * 건을 골라 문의하는 화면이고, 여기는 아직 계정이 없거나 로그인하지 않은 방문자도
 * 쓸 수 있어야 한다.
 *
 * `ConsumerAskInquiryPage` 와 같은 이유로 **프론트엔드 mock 이다.** 문의 접수
 * API가 없어 제출해도 서버로 나가지 않고, 로컬 상태만 바꿔 접수 완료 화면을 보여준다.
 */
export function SupportPage() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [type, setType] = useState('')
  const [content, setContent] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)

  const canSubmit = name.trim() !== '' && email.trim() !== '' && type !== '' && content.trim() !== ''

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!EMAIL_PATTERN.test(email.trim())) {
      setEmailError('올바른 이메일 주소를 입력해 주세요.')
      return
    }
    if (!canSubmit) return
    setEmailError(null)
    setSubmitted(true)
  }

  return (
    <div className="landing-page support-page">
      <Header isLogoVisible />
      <main className="support-content">
        <header className="support-content__header">
          <p className="support-content__eyebrow">SUPPORT</p>
          <h1>문의하기</h1>
          <p className="support-content__lead">
            자주 묻는 질문에서 원하는 답을 찾지 못하셨나요? 아래 양식으로 문의를 남겨주시면 확인 후 입력하신 이메일로 답변드립니다.
          </p>
          <p className="support-content__meta">
            이메일 support@saeroi.example · 운영 시간 평일 10:00 ~ 18:00 (주말·공휴일 휴무)
          </p>
        </header>

        <section className="support-content__form-section" aria-labelledby="support-form-title">
          <h2 id="support-form-title">문의 남기기</h2>

          {submitted ? (
            <div className="support-content__submitted">
              <p>문의가 접수되었습니다. 입력하신 이메일로 답변드리겠습니다.</p>
              <Button
                variant="secondary"
                onClick={() => {
                  setSubmitted(false)
                  setName('')
                  setEmail('')
                  setType('')
                  setContent('')
                }}
              >
                새 문의 작성
              </Button>
            </div>
          ) : (
            <form className="support-content__form" onSubmit={handleSubmit}>
              <Input
                label="이름"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="이름을 입력해 주세요"
              />
              <Input
                label="이메일"
                type="email"
                value={email}
                error={emailError ?? undefined}
                onChange={(event) => {
                  setEmail(event.target.value)
                  setEmailError(null)
                }}
                placeholder="답변받으실 이메일 주소"
              />
              <Select
                label="문의 유형"
                placeholder="유형을 선택해 주세요"
                options={INQUIRY_TYPE_OPTIONS}
                value={type}
                onChange={(event) => setType(event.target.value)}
              />
              <div className="ui-field">
                <label className="ui-field__label" htmlFor="support-content">
                  문의 내용
                </label>
                <textarea
                  id="support-content"
                  className="ui-field__control support-content__textarea"
                  rows={6}
                  placeholder="문의하실 내용을 자세히 적어 주세요."
                  value={content}
                  onChange={(event) => setContent(event.target.value)}
                />
              </div>
              <Button type="submit" disabled={!canSubmit}>
                문의 등록
              </Button>
            </form>
          )}
        </section>
      </main>
      <Footer />
    </div>
  )
}
