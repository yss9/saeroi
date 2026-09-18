import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Select, type SelectOption } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'

/** 문의 대상 후보. 실제로는 내 주문 목록에서 골라야 하지만, 조회 API가 없어 이름만 목데이터. */
const PRODUCT_OPTIONS: readonly SelectOption[] = [
  { value: 'ORD-00000012', label: '업사이클 데님 토트백 (ORD-00000012)' },
  { value: 'ORD-00000009', label: '스컬그레이몬 (ORD-00000009)' },
  { value: 'ORD-00000005', label: '재생 울 니트 (ORD-00000005)' },
]

const TYPE_OPTIONS: readonly SelectOption[] = [
  { value: 'DELIVERY', label: '배송' },
  { value: 'EXCHANGE_RETURN', label: '교환·반품' },
  { value: 'PRODUCT', label: '상품 문의' },
  { value: 'ETC', label: '기타' },
]

/**
 * 문의하기.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 문의 접수 API·테이블이 아직 없어
 * 제출해도 서버로 나가지 않는다 — 폼 아래 확인 문구만 보여주고 끝난다.
 */
export function ConsumerAskInquiryPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const [productCode, setProductCode] = useState('')
  const [type, setType] = useState('')
  const [content, setContent] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const showProductSelect = type !== '' && type !== 'ETC'

  const canSubmit =
    (!showProductSelect || productCode !== '') && type !== '' && content.trim() !== ''

  return (
    <ConsumerAccountLayout
      current="askInquiry"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card" aria-labelledby="ask-inquiry-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">ASK</p>
            <h2 id="ask-inquiry-title">문의하기</h2>
          </div>
        </div>

        {submitted ? (
          <div className="consumer-inquiry-submitted">
            <h3>문의가 접수되었습니다.</h3>
            <p>답변은 문의내역에서 확인하실 수 있습니다.</p>
            <Button
              onClick={() => {
                setSubmitted(false)
                setProductCode('')
                setType('')
                setContent('')
              }}
            >
              새 문의 작성
            </Button>
          </div>
        ) : (
          <form
            className="consumer-inquiry-form"
            onSubmit={(event) => {
              event.preventDefault()
              if (canSubmit) setSubmitted(true)
            }}
          >
            <Select
              label="문의 유형"
              placeholder="유형을 선택해 주세요"
              options={TYPE_OPTIONS}
              value={type}
              onChange={(event) => {
                const nextType = event.target.value
                setType(nextType)
                if (nextType === '' || nextType === 'ETC') setProductCode('')
              }}
            />
            {showProductSelect && (
              <Select
                label="문의 상품"
                placeholder="상품을 선택해 주세요"
                options={PRODUCT_OPTIONS}
                value={productCode}
                onChange={(event) => setProductCode(event.target.value)}
              />
            )}
            <div className="ui-field">
              <label className="ui-field__label" htmlFor="inquiry-content">
                문의 내용
              </label>
              <textarea
                id="inquiry-content"
                className="ui-field__control consumer-inquiry-form__textarea"
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
    </ConsumerAccountLayout>
  )
}
