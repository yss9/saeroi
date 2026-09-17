import { Link } from 'react-router-dom'
import { ArrowRight, Lock, ShieldCheck, Warning } from '@phosphor-icons/react'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import { MATERIAL_TYPE_LABEL, ROOT_ORIGIN_LABEL } from '../../material/types/material.types.ts'
import {
  LISTING_STATUS_LABEL,
  type Listing,
  type ListingStatus,
} from '../../marketplace/types/marketplace.types.ts'

export interface SupplierListingCardProps {
  listing: Listing
  /** 판매 취소. 이미 내린 글에는 주지 않는다 */
  onCancel?: (listing: Listing) => void
  /** 체인 정리 등 상태별로 달라지는 추가 동작 */
  extraAction?: React.ReactNode
}

/**
 * 판매자가 보는 판매글 카드.
 *
 * <b>마켓플레이스의 {@link ListingCard} 와 따로 둔다.</b> 같은 데이터를 쓰지만 묻는
 * 질문이 다르다 — 구매자는 "이걸 살까" 이고 판매자는 "내 글이 제대로 걸려 있나" 다.
 * 그래서 여기에만 있는 것이 둘 있다: <b>소진율</b>(얼마나 팔렸나)과
 * <b>이력 기록 여부</b>(손볼 것이 남았나).
 *
 * 공유 컴포넌트를 고치지 않은 이유이기도 하다. 저쪽에 소진율과 이력 경고를 붙이면
 * 구매자에게 필요 없는 정보가 늘고, 브랜드 화면 담당자와 같은 파일을 다투게 된다.
 */
export function SupplierListingCard({ listing, onCancel, extraAction }: SupplierListingCardProps) {
  const sold = listing.totalQuantity - listing.availableQuantity
  const soldRatio = listing.totalQuantity > 0 ? sold / listing.totalQuantity : 0
  const soldPercent = Math.round(soldRatio * 100)

  // 이력이 남지 않은 글. 서버에는 있고 체인에는 없어, 구매자가 사도 발송·수령이
  // 기록되지 않는다. 이미 내린 글은 정리 대상이라 여기서 경고하지 않는다.
  const unrecorded = listing.chainListingId === null && listing.status !== 'CANCELED'
  const detailPath = `/marketplace/${listing.listingId}`

  return (
    <li
      className={[
        'console-card',
        unrecorded ? 'console-card--todo' : '',
        listing.status === 'CANCELED' ? 'console-card--done' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className="console-card__top">
        {/*
          **판매글 번호를 앞에 둔다.**

          소재 코드만으로는 카드를 구분할 수 없다. `material_listing.material_id` 에
          UNIQUE 가 없어 <b>같은 소재로 판매글을 여러 개</b> 만들 수 있기 때문이다
          (정책 07.1 부분 판매 — 30kg 중 12kg 을 팔아 품절되면 남은 18kg 을 새 글로
          다시 올린다). 그러면 `MAT-000020` 이 품절 카드와 판매 중 카드에 똑같이 찍혀
          어느 것을 취소하려는 건지 알 수 없다.

          소재 코드도 남긴다 — 어느 소재인지는 여전히 알아야 하고, 같은 소재의 글끼리
          묶어 보는 데도 쓴다. 다만 뒤로 물러나 옅게 둔다.
        */}
        <span className="console-card__code">
          <b>#{listing.listingId}</b>
          <span className="console-card__code-of">{listing.materialCode}</span>
        </span>
        <StatusBadge status={listing.status} unrecorded={unrecorded} />
      </div>

      <h3 className="console-card__name">
        <Link to={detailPath}>{listing.materialName}</Link>
      </h3>

      <div className="console-card__attrs">
        <span className="console-chip">
          {MATERIAL_TYPE_LABEL[listing.materialType] ?? listing.materialType}
        </span>
        <span className="console-chip">{ROOT_ORIGIN_LABEL[listing.rootOrigin]}</span>
        {/* 등급이 없는 소재가 실제로 적지 않다. 빈칸으로 두면 카드마다 높이가 어긋나고,
            "등급을 못 받은 것" 인지 "화면이 빠뜨린 것" 인지도 구분되지 않는다. */}
        {listing.grade ? (
          <span className="console-chip">{listing.grade}등급</span>
        ) : (
          <span className="console-chip console-chip--empty">등급 미지정</span>
        )}
      </div>

      <p className="console-card__price">
        {formatWon(listing.pricePerKg)}
        <small>/kg</small>
      </p>

      <div className="console-card__figures">
        <div className="console-figure">
          <span className="console-figure__label">판매 가능</span>
          <span className="console-figure__value">
            {formatKg(listing.availableQuantity, { withUnit: false })}
            <small>kg</small>
          </span>
        </div>

        {/* 남은 양만 보여주면 30kg 짜리가 12kg 남았을 때 "원래 작은 물량" 처럼 읽힌다.
            얼마나 팔렸는지가 판매자의 판단에 들어간다. */}
        <div className="console-bar" role="img" aria-label={`총 ${formatKg(listing.totalQuantity)} 중 ${soldPercent}% 판매됨`}>
          <span style={{ width: `${soldPercent}%` }} />
        </div>

        <div className="console-figure__split">
          <span>
            소진율 <b>{soldPercent}%</b>
          </span>
          <span>
            총 <b>{formatKg(listing.totalQuantity, { withUnit: false })}kg</b>
          </span>
        </div>
      </div>

      {unrecorded ? (
        <p className="console-card__alert">
          <span>
            <Warning aria-hidden="true" weight="fill" />
            이력 기록이 남지 않았습니다
          </span>
          <Link to={detailPath}>
            지금 완료하기 <ArrowRight aria-hidden="true" />
          </Link>
        </p>
      ) : null}

      <div className="console-card__foot console-card__foot--ruled">
        <span className="console-card__date">{formatDate(listing.createdAt)} 등록</span>
        <span className="console-card__acts">
          {extraAction}
          {onCancel && listing.status !== 'CANCELED' && (
            <button type="button" className="console-btn console-btn--ghost console-btn--sm" onClick={() => onCancel(listing)}>
              판매 취소
            </button>
          )}
        </span>
      </div>
    </li>
  )
}

/**
 * 상태 배지.
 *
 * 이력이 없으면 <b>그 사실을 먼저</b> 말한다. "판매 중" 을 띄우면 정상으로 읽히는데,
 * 실제로는 구매자가 사도 거래가 기록되지 않는 상태다.
 */
function StatusBadge({ status, unrecorded }: { status: ListingStatus; unrecorded: boolean }) {
  if (unrecorded) {
    return (
      <span className="console-badge console-badge--wait">
        <Warning aria-hidden="true" weight="fill" style={{ width: 11, height: 11 }} />
        이력 미기록
      </span>
    )
  }

  if (status === 'ON_SALE') {
    return (
      <span className="console-badge console-badge--ok">
        <span className="console-badge__dot" aria-hidden="true" />
        {LISTING_STATUS_LABEL[status]}
      </span>
    )
  }

  return (
    <span className="console-badge console-badge--flat">
      {status === 'SOLD_OUT' && <ShieldCheck aria-hidden="true" style={{ width: 11, height: 11 }} />}
      {status === 'CANCELED' && <Lock aria-hidden="true" style={{ width: 11, height: 11 }} />}
      {LISTING_STATUS_LABEL[status]}
    </span>
  )
}

/** "9월 10일". 연도는 같은 해가 대부분이라 뺀다 — 길어지면 카드 바닥이 두 줄이 된다. */
function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}월 ${date.getDate()}일`
}
