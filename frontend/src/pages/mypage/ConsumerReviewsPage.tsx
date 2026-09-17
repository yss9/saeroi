import { Star } from '@phosphor-icons/react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, EmptyState } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'

interface WritableItem {
  id: string
  productName: string
  brandName: string
  orderNumber: string
}

interface WrittenReview {
  id: string
  productName: string
  brandName: string
  rating: number
  content: string
  writtenAt: string
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** 화면 확인용 목데이터. 리뷰 작성·조회 API가 아직 없다. */
const MOCK_WRITABLE: readonly WritableItem[] = [
  {
    id: '1',
    productName: '업사이클 데님 토트백',
    brandName: 'ReWeave',
    orderNumber: 'ORD-00000012',
  },
]

const MOCK_WRITTEN: readonly WrittenReview[] = [
  {
    id: '2',
    productName: '재생 울 니트',
    brandName: 'ReWeave',
    rating: 5,
    content: '생각보다 두툼하고 색도 화면과 똑같아요. 만족합니다.',
    writtenAt: '2026-08-30T12:00:00',
  },
]

function StarPicker({ value, onChange }: { value: number; onChange: (next: number) => void }) {
  return (
    <div className="consumer-review-stars" role="radiogroup" aria-label="별점">
      {[1, 2, 3, 4, 5].map((score) => (
        <button
          key={score}
          type="button"
          role="radio"
          aria-checked={value >= score}
          aria-label={`${score}점`}
          onClick={() => onChange(score)}
        >
          <Star weight={value >= score ? 'fill' : 'regular'} aria-hidden="true" />
        </button>
      ))}
    </div>
  )
}

function StarDisplay({ value }: { value: number }) {
  return (
    <div className="consumer-review-stars consumer-review-stars--display" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((score) => (
        <Star key={score} weight={value >= score ? 'fill' : 'regular'} />
      ))}
    </div>
  )
}

/**
 * 리뷰관리.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 리뷰 저장 API가 없어, "작성 가능"
 * 탭에서 쓴 리뷰는 페이지를 벗어나면 사라진다(로컬 상태일 뿐 서버 저장 없음).
 */
export function ConsumerReviewsPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const [tab, setTab] = useState<'WRITABLE' | 'WRITTEN'>('WRITABLE')
  const [writable, setWritable] = useState(MOCK_WRITABLE)
  const [written, setWritten] = useState(MOCK_WRITTEN)
  const [draftId, setDraftId] = useState<string | null>(null)
  const [draftRating, setDraftRating] = useState(0)
  const [draftContent, setDraftContent] = useState('')

  function submitDraft(item: WritableItem) {
    setWritten((prev) => [
      {
        id: item.id,
        productName: item.productName,
        brandName: item.brandName,
        rating: draftRating,
        content: draftContent,
        writtenAt: new Date().toISOString(),
      },
      ...prev,
    ])
    setWritable((prev) => prev.filter((it) => it.id !== item.id))
    setDraftId(null)
    setDraftRating(0)
    setDraftContent('')
  }

  return (
    <ConsumerAccountLayout
      current="reviews"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card" aria-labelledby="reviews-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">REVIEWS</p>
            <h2 id="reviews-title">리뷰관리</h2>
          </div>
        </div>

        <div className="consumer-tabs" role="tablist" aria-label="리뷰 상태">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'WRITABLE'}
            className={`consumer-tabs__tab${tab === 'WRITABLE' ? ' is-active' : ''}`}
            onClick={() => setTab('WRITABLE')}
          >
            작성 가능 ({writable.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'WRITTEN'}
            className={`consumer-tabs__tab${tab === 'WRITTEN' ? ' is-active' : ''}`}
            onClick={() => setTab('WRITTEN')}
          >
            작성 완료 ({written.length})
          </button>
        </div>

        {tab === 'WRITABLE' ? (
          writable.length === 0 ? (
            <EmptyState
              title="작성할 수 있는 리뷰가 없습니다"
              description="구매 확정된 상품이 있으면 여기서 리뷰를 남길 수 있습니다."
            />
          ) : (
            <div className="consumer-review-list">
              {writable.map((item) => (
                <article className="consumer-review-item" key={item.id}>
                  <p className="consumer-review-item__brand">{item.brandName}</p>
                  <h3>{item.productName}</h3>
                  <p className="consumer-review-item__order">{item.orderNumber}</p>

                  {draftId === item.id ? (
                    <div className="consumer-review-draft">
                      <StarPicker value={draftRating} onChange={setDraftRating} />
                      <textarea
                        className="ui-field__control"
                        rows={4}
                        placeholder="상품은 어떠셨나요?"
                        value={draftContent}
                        onChange={(event) => setDraftContent(event.target.value)}
                      />
                      <div className="consumer-review-draft__actions">
                        <Button variant="secondary" size="sm" onClick={() => setDraftId(null)}>
                          취소
                        </Button>
                        <Button
                          size="sm"
                          disabled={draftRating === 0 || draftContent.trim() === ''}
                          onClick={() => submitDraft(item)}
                        >
                          등록
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => {
                        setDraftId(item.id)
                        setDraftRating(0)
                        setDraftContent('')
                      }}
                    >
                      리뷰 쓰기
                    </Button>
                  )}
                </article>
              ))}
            </div>
          )
        ) : written.length === 0 ? (
          <EmptyState title="작성한 리뷰가 없습니다" />
        ) : (
          <div className="consumer-review-list">
            {written.map((review) => (
              <article className="consumer-review-item" key={review.id}>
                <p className="consumer-review-item__brand">{review.brandName}</p>
                <h3>{review.productName}</h3>
                <StarDisplay value={review.rating} />
                <p className="consumer-review-item__content">{review.content}</p>
                <time dateTime={review.writtenAt}>
                  {dateFormatter.format(new Date(review.writtenAt))}
                </time>
              </article>
            ))}
          </div>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
