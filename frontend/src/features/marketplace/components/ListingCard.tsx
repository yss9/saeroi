import { Link } from 'react-router-dom'
import { StatusBadge } from '../../../components/common/index.ts'
import { EvidenceThumbnailImage } from '../../evidence/components/EvidenceThumbnailImage.tsx'
import type { EvidenceThumbnail } from '../../evidence/types/evidence.types.ts'
import { calculateTotalPrice, formatKg, formatWon } from '../../../utils/quantity.ts'
import { useClickNavigation } from '../../../utils/useClickNavigation.ts'
import {
  GENERATION_TYPE_LABEL,
  MATERIAL_TYPE_LABEL,
  ROOT_ORIGIN_LABEL,
} from '../../material/types/material.types.ts'
import {
  LISTING_STATUS_LABEL,
  type ListingStatus,
  type Listing,
} from '../types/marketplace.types.ts'

export interface ListingCardProps {
  listing: Listing
  /** 판매자 화면에서는 취소 버튼 등 다른 동작이 붙는다 */
  actions?: React.ReactNode
  /**
   * 누가 보는 카드인가. 남은 수량의 이름이 달라진다 — 소재 마켓에서 이 카드를 보는 쪽은
   * 사는 사람이라 "판매 가능" 이 남의 일처럼 읽힌다. 자기 판매글을 보는 화면만 'seller' 다.
   *
   * `actions` 유무로 넘겨짚지 않는다. 판매자 화면에서 버튼이 하나도 안 붙는 판매글
   * (이미 내린 글)이 있고, 그때 이름만 조용히 바뀐다.
   */
  viewer?: 'buyer' | 'seller'
  /** 이 판매글의 소재 대표 사진. 없으면(아직 조회 중이거나 사진이 없음) 자리표시 아이콘. */
  thumbnail?: EvidenceThumbnail
}

const STATUS_TONE: Record<ListingStatus, 'success' | 'neutral' | 'danger'> = {
  ON_SALE: 'success',
  SOLD_OUT: 'neutral',
  CANCELED: 'danger',
}

/**
 * 판매글 카드.
 *
 * 남은 수량과 총 수량을 **함께** 보여준다. 남은 양만 보여주면 30kg 짜리 판매글이
 * 12kg 만 남았을 때 "원래 작은 물량" 처럼 보인다. 얼마나 팔렸는지가 구매 판단에 들어간다.
 */
export function ListingCard({ listing, actions, viewer = 'buyer', thumbnail }: ListingCardProps) {
  const availableLabel = viewer === 'seller' ? '판매 가능' : '구매 가능'
  const followClick = useClickNavigation()
  // **남은 양을 채운다.** 막대 옆의 숫자가 남은 양이라 팔린 양을 채우면 8/10 이 남은
  // 판매글의 막대가 20% 만 차서 거의 다 팔린 것처럼 읽혔다. 팔린 양은 적지 않는다 —
  // 남은 양과 총량이 있으면 뺄셈으로 나오고, 사는 쪽이 고를 때 쓰는 숫자도 아니다.
  const availableRatio =
    listing.totalQuantity > 0 ? listing.availableQuantity / listing.totalQuantity : 0
  // 남은 양을 다 샀을 때의 금액. 단가만으로는 이 판매글이 얼마짜리인지 감이 오지 않는다.
  //
  // **계산은 calculateTotalPrice 로만 한다.** 여기서 따로 곱하면 반올림 방향이 달라진다 —
  // 정본은 버림인데 이 줄은 반올림이어서, 결제창에서 보는 금액과 1원 어긋날 수 있었다.
  const lotAmount = calculateTotalPrice(listing.availableQuantity, listing.pricePerKg)

  return (
    <article
      className="listing-card"
      onClick={(event) => followClick(event, `/marketplace/${listing.listingId}`)}
    >
      <ThumbnailSlot listing={listing} thumbnail={thumbnail} />

      <div className="listing-card__body">
        {/*
          소재 코드와 판매자 이름은 적지 않는다. 코드는 사람이 읽는 값이 아니고(상세에
          있다), 서버가 주는 판매자 이름은 업체명이 아니라 대표자 개인 이름이다.
        */}
        <div className="listing-card__head">
          <h3 className="listing-card__name">
            <Link to={`/marketplace/${listing.listingId}`}>{listing.materialName}</Link>
          </h3>
          <StatusBadge tone={STATUS_TONE[listing.status]}>
            {LISTING_STATUS_LABEL[listing.status]}
          </StatusBadge>
        </div>

        {/* 고를 때 견주는 사실들. 한 칸에 몰아 두면 이름이 어디까지인지 흐려진다. */}
        <dl className="listing-card__facts">
          <div>
            <dt>소재 종류</dt>
            <dd>{MATERIAL_TYPE_LABEL[listing.materialType]}</dd>
          </div>
          <div>
            <dt>출처</dt>
            <dd>{ROOT_ORIGIN_LABEL[listing.rootOrigin]}</dd>
          </div>
          <div>
            <dt>생성 유형</dt>
            <dd>{GENERATION_TYPE_LABEL[listing.generationType]}</dd>
          </div>
        </dl>

        <div className="listing-card__quantity">
          <p className="listing-card__quantity-line">
            <span>수량 현황</span>
            <strong>{formatKg(listing.availableQuantity)}</strong>
            <small>/ 총 {formatKg(listing.totalQuantity)}</small>
          </p>
          {/* 막대는 장식이 아니라 정보다 — 얼마나 남았는지가 숫자보다 빨리 읽힌다. */}
          <div
            className="listing-card__bar"
            role="img"
            aria-label={`총 ${formatKg(listing.totalQuantity)} 중 ${formatKg(listing.availableQuantity)} ${availableLabel}`}
          >
            <span style={{ width: `${Math.round(availableRatio * 100)}%` }} />
          </div>
        </div>
      </div>

      {/* 오른쪽은 값이다. 여러 장을 훑을 때 눈이 한 줄에서 내려온다. */}
      <div className="listing-card__aside">
        <p className="listing-card__price">
          {formatWon(listing.pricePerKg)}
          <small>/kg</small>
        </p>
        <p className="listing-card__lot">
          {availableLabel} 전량 {formatWon(lotAmount)}
        </p>

        {actions ?? (
          <Link
            className="listing-card__cta"
            to={`/marketplace/${listing.listingId}#purchase`}
          >
            구매하기
          </Link>
        )}
      </div>
    </article>
  )
}

/**
 * 왼쪽 이미지 자리.
 *
 * 소재에는 이미지 컬럼이 서버에 없다 — 등록 시 첨부한 사진은 증빙(evidence_file)으로
 * 저장된다. 그 중 대표 사진 하나를 `thumbnail` 로 받아 그린다. 사진이 없는 소재도
 * 있으므로(증빙 없이 등록 가능) 자리는 항상 같은 크기로 잡아 둔다 — 사진 유무로
 * 카드 높이와 줄 맞춤이 달라지지 않게 하기 위해서다.
 *
 * 등급은 이 자리에 얹는다. 사진 위에 겹쳐 두면 가운데 칸에서 한 줄을 뺄 수 있다.
 */
function ThumbnailSlot({
  listing,
  thumbnail,
}: {
  listing: Listing
  thumbnail?: EvidenceThumbnail
}) {
  return (
    <div className="listing-card__thumbnail">
      <EvidenceThumbnailImage thumbnail={thumbnail} alt={`${listing.materialName} 사진`} />
      {listing.grade && <span className="listing-card__grade">{listing.grade}등급</span>}
    </div>
  )
}
