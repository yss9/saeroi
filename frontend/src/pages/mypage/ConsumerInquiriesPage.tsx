import { CaretDown } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { EmptyState, StatusBadge, type StatusTone } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { formatDate } from '../../features/user/utils/formatDate.ts'

type InquiryStatus = 'WAITING' | 'ANSWERED'

interface InquiryItem {
  id: string
  status: InquiryStatus
  productName: string
  typeLabel: string
  content: string
  answer: string | null
  createdAt: string
  answeredAt: string | null
}

const STATUS_META: Record<InquiryStatus, { label: string; tone: StatusTone }> = {
  WAITING: { label: '답변 대기', tone: 'warning' },
  ANSWERED: { label: '답변 완료', tone: 'success' },
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

/** 화면 확인용 목데이터. 문의 접수·조회 API가 아직 없다. */
const MOCK_INQUIRIES: readonly InquiryItem[] = [
  {
    id: '1',
    status: 'ANSWERED',
    productName: '업사이클 데님 토트백',
    typeLabel: '배송',
    content: '배송이 예정일보다 늦어지고 있는데 확인 부탁드립니다.',
    answer: '확인 결과 물류 지연으로 하루 늦게 도착 예정입니다. 불편을 드려 죄송합니다.',
    createdAt: '2026-09-11T10:00:00',
    answeredAt: '2026-09-11T15:32:00',
  },
  {
    id: '2',
    status: 'WAITING',
    productName: '재생 울 니트',
    typeLabel: '상품 문의',
    content: '세탁 방법이 궁금합니다. 드라이클리닝만 가능한가요?',
    answer: null,
    createdAt: '2026-09-15T13:20:00',
    answeredAt: null,
  },
]

/**
 * 문의내역.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 문의 조회 API가 없어 목데이터로
 * 채운다 — "문의하기" 에서 새로 등록해도 이 목록에는 반영되지 않는다(서버 저장이 없음).
 */
export function ConsumerInquiriesPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const [openId, setOpenId] = useState<string | null>(null)

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

        {MOCK_INQUIRIES.length === 0 ? (
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
            {MOCK_INQUIRIES.map((inquiry) => {
              const status = STATUS_META[inquiry.status]
              const isOpen = openId === inquiry.id
              return (
                <article className="consumer-inquiry-item" key={inquiry.id}>
                  <button
                    type="button"
                    className="consumer-inquiry-item__summary"
                    aria-expanded={isOpen}
                    onClick={() => setOpenId(isOpen ? null : inquiry.id)}
                  >
                    <div className="consumer-inquiry-item__header">
                      <div className="consumer-inquiry-item__badges">
                        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                        <span>{inquiry.typeLabel}</span>
                      </div>
                      <CaretDown className="consumer-inquiry-item__chevron" weight="bold" aria-hidden="true" />
                    </div>
                    <p className="consumer-inquiry-item__product">{inquiry.productName}</p>
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
