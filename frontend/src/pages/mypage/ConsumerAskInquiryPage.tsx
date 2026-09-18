import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Select, type SelectOption } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { getMyProductOrders } from '../../features/user/api/getMyProductOrders.ts'
import { consumerOrderKeys } from '../../features/user/api/consumerOrderKeys.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { useCreateConsumerInquiry } from '../../features/user/hooks/useCreateConsumerInquiry.ts'
import type { InquiryType } from '../../features/user/types/consumerInquiry.types.ts'
import { toErrorMessage } from '../../utils/errors.ts'

const TYPE_OPTIONS: readonly SelectOption[] = [
  { value: 'DELIVERY', label: '배송' },
  { value: 'EXCHANGE_RETURN', label: '교환·반품' },
  { value: 'PRODUCT', label: '상품 문의' },
  { value: 'ETC', label: '기타' },
]

/** 최근 주문 중에서 고른다. 문의 대상 후보를 다 보여줄 필요는 없다. */
const ORDER_OPTION_PAGE_SIZE = 50

/**
 * 문의하기.
 *
 * **2026-09-18 — 실제 API에 연결했다.** 이전에는 프론트 로컬 state만 바꾸고 끝나
 * 제출해도 서버에 남지 않았다. 지금은 `POST /v1/consumers/me/inquiries`로 등록하고,
 * 성공하면 문의내역 쿼리를 무효화해 바로 반영되게 한다(`useCreateConsumerInquiry`).
 */
export function ConsumerAskInquiryPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null
  const createInquiry = useCreateConsumerInquiry()

  const orders = useQuery({
    queryKey: consumerOrderKeys.list({ page: 0, size: ORDER_OPTION_PAGE_SIZE }),
    queryFn: () => getMyProductOrders({ page: 0, size: ORDER_OPTION_PAGE_SIZE }),
  })

  const [productCode, setProductCode] = useState('')
  const [type, setType] = useState('')
  const [content, setContent] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const showProductSelect = type !== '' && type !== 'ETC'

  const productOptions: SelectOption[] = (orders.data?.content ?? []).map((order) => ({
    value: String(order.orderId),
    label: `${order.items[0]?.productName ?? order.orderCode} (${order.orderCode})`,
  }))

  const canSubmit =
    (!showProductSelect || productCode !== '') &&
    type !== '' &&
    content.trim() !== '' &&
    !createInquiry.isPending

  function resetForm() {
    setSubmitted(false)
    setProductCode('')
    setType('')
    setContent('')
  }

  function handleSubmit() {
    createInquiry.mutate(
      {
        type: type as InquiryType,
        productOrderId: showProductSelect ? Number(productCode) : undefined,
        content,
      },
      { onSuccess: () => setSubmitted(true) },
    )
  }

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
            <Button onClick={resetForm}>
              새 문의 작성
            </Button>
          </div>
        ) : (
          <form
            className="consumer-inquiry-form"
            onSubmit={(event) => {
              event.preventDefault()
              if (canSubmit) handleSubmit()
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
                placeholder={
                  orders.isPending ? '주문 목록을 불러오는 중…' : '상품을 선택해 주세요'
                }
                options={productOptions}
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
            {createInquiry.isError && (
              <p className="ui-field__error" role="alert">
                {toErrorMessage(createInquiry.error)}
              </p>
            )}
            <Button type="submit" disabled={!canSubmit}>
              {createInquiry.isPending ? '등록 중…' : '문의 등록'}
            </Button>
          </form>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
