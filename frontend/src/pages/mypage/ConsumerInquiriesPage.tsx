import { CaretDown } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { useConsumerInquiries } from '../../features/user/hooks/useConsumerInquiries.ts'
import type { ConsumerInquiry, InquiryType } from '../../features/user/types/consumerInquiry.types.ts'
import { formatDate } from '../../features/user/utils/formatDate.ts'

const STATUS_META: Record<'WAITING' | 'ANSWERED', { label: string; tone: StatusTone }> = {
  WAITING: { label: '답변 대기', tone: 'warning' },
  ANSWERED: { label: '답변 완료', tone: 'success' },
}

const TYPE_LABELS: Record<InquiryType, string> = {
  DELIVERY: '배송',
  EXCHANGE_RETURN: '교환·반품',
  PRODUCT: '상품 문의',
  ETC: '기타',
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

/**
 * 문의내역.
 *
 * **2026-09-18 — 실제 API에 연결했다.** 이전에는 목데이터라 "문의하기"에서 새로 등록해도
 * 이 목록에 반영되지 않았다. 지금은 `GET /v1/consumers/me/inquiries`로 조회하고,
 * 등록 쪽(`useCreateConsumerInquiry`)이 성공 시 같은 쿼리를 무효화해 바로 보인다.
 *
 * `answer`는 이 프로젝트에 관리자/CS 도메인이 없어 서버가 항상 `null`로 준다 — 그래서
 * 지금은 모든 문의가 "답변 대기"로만 보인다. 그 상태 자체를 화면이 정상으로 다룬다.
 */
export function ConsumerInquiriesPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null
  const inquiries = useConsumerInquiries()

  const [openId, setOpenId] = useState<number | null>(null)

  return (
    <ConsumerAccountLayout
      current="inquiries"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card" aria-labelledby="inquiries-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">INQUIRIES</p>
            <h2 id="inquiries-title">문의내역</h2>
          </div>
          <Link className="consumer-text-link" to="/mypage/inquiries/new">
            문의하기
          </Link>
        </div>

        {inquiries.isPending ? (
          <Loading label="문의 내역을 불러오는 중" variant="skeleton" rows={4} />
        ) : inquiries.isError ? (
          <ErrorMessage error={inquiries.error} onRetry={() => void inquiries.refetch()} />
        ) : inquiries.data.length === 0 ? (
          <EmptyState
            title="문의 내역이 없습니다"
            description="궁금한 점이 있으면 문의를 남겨 보세요."
            action={
              <Link className="consumer-text-link" to="/mypage/inquiries/new">
                문의하기
              </Link>
            }
          />
        ) : (
          <div className="consumer-inquiry-list">
            {inquiries.data.map((inquiry: ConsumerInquiry) => {
              const status = STATUS_META[inquiry.answered ? 'ANSWERED' : 'WAITING']
              const isOpen = openId === inquiry.inquiryId
              return (
                <article className="consumer-inquiry-item" key={inquiry.inquiryId}>
                  <button
                    type="button"
                    className="consumer-inquiry-item__summary"
                    aria-expanded={isOpen}
                    onClick={() => setOpenId(isOpen ? null : inquiry.inquiryId)}
                  >
                    <div className="consumer-inquiry-item__header">
                      <div className="consumer-inquiry-item__badges">
                        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                        <span>{TYPE_LABELS[inquiry.type]}</span>
                      </div>
                      <CaretDown className="consumer-inquiry-item__chevron" weight="bold" aria-hidden="true" />
                    </div>
                    {inquiry.targetLabel && (
                      <p className="consumer-inquiry-item__product">{inquiry.targetLabel}</p>
                    )}
                    <p className="consumer-inquiry-item__content">{inquiry.content}</p>
                    <time dateTime={inquiry.createdAt}>
                      {formatDate(inquiry.createdAt, dateFormatter)}
                    </time>
                  </button>
                  {isOpen && (
                    <div className="consumer-inquiry-item__answer">
                      {inquiry.answer ? (
                        <>
                          <p className="consumer-inquiry-item__answer-label">답변</p>
                          {inquiry.answeredAt && (
                            <time dateTime={inquiry.answeredAt}>
                              {formatDate(inquiry.answeredAt, dateFormatter)}
                            </time>
                          )}
                          <p>{inquiry.answer}</p>
                        </>
                      ) : (
                        <p className="consumer-inquiry-item__answer-waiting">
                          아직 답변이 등록되지 않았습니다.
                        </p>
                      )}
                    </div>
                  )}
                </article>
              )
            })}
          </div>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
