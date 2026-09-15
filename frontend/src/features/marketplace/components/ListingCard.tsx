import { Link } from 'react-router-dom'
import { StatusBadge } from '../../../components/common/index.ts'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import { useClickNavigation } from '../../../utils/useClickNavigation.ts'
import { ROOT_ORIGIN_LABEL } from '../../material/types/material.types.ts'
import {
  LISTING_STATUS_LABEL,
  type ListingStatus,
  type Listing,
} from '../types/marketplace.types.ts'

export interface ListingCardProps {
  listing: Listing
  /** 판매자 화면에서는 취소 버튼 등 다른 동작이 붙는다 */
  actions?: React.ReactNode
}

const STATUS_TONE: Record<ListingStatus, 'success' | 'neutral' | 'danger'> = {
  ON_SALE: 'success',
  SOLD_OUT: 'neutral',
  CANCELED: 'danger',
}

// 카드 테두리 색도 StatusBadge 와 같은 톤 계열로 맞춘다 — 색상 값 자체는
// listing-card--<modifier> 클래스를 통해 marketplace.css 의 CSS 변수로만 지정한다.
const STATUS_CARD_MODIFIER: Record<ListingStatus, string> = {
  ON_SALE: 'listing-card--on-sale',
  SOLD_OUT: 'listing-card--sold-out',
  CANCELED: 'listing-card--canceled',
}

/**
 * 판매글 카드.
 *
 * 판매 가능 수량과 총 수량을 **함께** 보여준다. 남은 양만 보여주면 30kg 짜리 판매글이
 * 12kg 만 남았을 때 "원래 작은 물량" 처럼 보인다. 얼마나 팔렸는지가 구매 판단에 들어간다.
 */
export function ListingCard({ listing, actions }: ListingCardProps) {
  const followClick = useClickNavigation()
  const soldQuantity = listing.totalQuantity - listing.availableQuantity
  const soldRatio = listing.totalQuantity > 0 ? soldQuantity / listing.totalQuantity : 0

  return (
    <article
      className={`listing-card ${STATUS_CARD_MODIFIER[listing.status]}`}
      onClick={(event) => followClick(event, `/marketplace/${listing.listingId}`)}
    >
      <header className="listing-card__header">
        <h3 className="listing-card__name">
          <Link to={`/marketplace/${listing.listingId}`}>{listing.materialName}</Link>
        </h3>
        <StatusBadge tone={STATUS_TONE[listing.status]}>
          {LISTING_STATUS_LABEL[listing.status]}
        </StatusBadge>
      </header>

      <dl className="listing-card__meta">
        <div>
          <dt>소재 코드</dt>
          <dd>{listing.materialCode}</dd>
        </div>
        <div>
          <dt>출처</dt>
          <dd>
            {ROOT_ORIGIN_LABEL[listing.rootOrigin]}
            {listing.grade && ` · ${listing.grade}등급`}
          </dd>
        </div>
        <div>
          <dt>단가</dt>
          <dd className="listing-card__price">{formatWon(listing.pricePerKg)} / kg</dd>
        </div>
      </dl>

      <div className="listing-card__quantity">
        <div className="listing-card__quantity-row">
          <span>판매 가능</span>
          <strong>{formatKg(listing.availableQuantity)}</strong>
        </div>
        {/* 진행 막대는 장식이 아니라 정보다 — 얼마나 팔렸는지를 숫자보다 빨리 읽힌다. */}
        <div
          className="listing-card__bar"
          role="img"
          aria-label={`총 ${formatKg(listing.totalQuantity)} 중 ${formatKg(soldQuantity)} 판매됨`}
        >
          <span style={{ width: `${Math.round(soldRatio * 100)}%` }} />
        </div>
        <p className="listing-card__total">총 {formatKg(listing.totalQuantity)}</p>
      </div>

      <footer className="listing-card__footer">
        <span className="listing-card__seller">{listing.sellerName}</span>
        {actions}
      </footer>
    </article>
  )
}
