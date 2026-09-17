import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import { useTrades } from '../hooks/useTrades.ts'
import {
  BUYER_ACTION_REQUIRED,
  SELLER_ACTION_REQUIRED,
  TRADE_STATUS_LABEL,
  type TradeFilters,
  type TradeStatus,
} from '../types/trade.types.ts'

/** 끝난 거래는 카드 자체를 뒤로 물린다. 진행 중인 거래를 찾는 목록이다. */
const CARD_TONE: Partial<Record<TradeStatus, string>> = {
  COMPLETED: 'trade-card--completed',
  CANCELED: 'trade-card--canceled',
}

/*
  **진행 중인 상태는 서로 다른 색이어야 한다.** 결제 완료와 배송 중이 같은 파랑이면
  목록에서 두 줄을 나란히 놓고도 어느 것이 이미 떠났는지 글자를 읽어야 안다.

  끝난 거래는 색을 빼거나(완료) 붉게(취소) 둔다 — 카드 자체도 뒤로 물러나 있다.
*/
const TONE: Record<TradeStatus, StatusTone> = {
  CREATED: 'warning',
  PAID: 'info',
  SHIPPED: 'success',
  RECEIVED: 'success',
  COMPLETED: 'neutral',
  CANCELED: 'danger',
}

export interface TradeListProps {
  filters?: TradeFilters
  /**
   * 카드를 눌렀을 때. 주면 상세로 이동하는 대신 이것을 부른다 — 구매 내역은 팝업으로
   * 연다. 주지 않으면 그대로 상세 주소로 간다(판매 거래·알림에서 들어오는 길).
   */
  onSelect?: (tradeId: number) => void
}

export function TradeList({ filters, onSelect }: TradeListProps) {
  const { data, isPending, isError, error, refetch } = useTrades(filters)

  // 구매·판매 양쪽이 같은 목록 컴포넌트를 쓴다. 강조해야 할 상태가 서로 다르므로
  // (구매자는 결제·수령, 판매자는 발송) 필터의 side 로 관점을 정한다.
  // 별도 prop 을 두지 않는 이유는 그 prop 이 filters.side 와 어긋날 수 있기 때문이다.
  const isSellerView = filters?.side === 'SELL'
  const actionRequired = isSellerView ? SELLER_ACTION_REQUIRED : BUYER_ACTION_REQUIRED

  if (isPending) return <Loading variant="skeleton" rows={3} label="거래 내역을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.content.length === 0) {
    return (
      <EmptyState
        title="거래 내역이 없습니다"
        description={
          isSellerView
            ? '등록한 판매글에서 구매가 발생하면 여기에 표시됩니다.'
            : '마켓플레이스에서 소재를 구매하면 여기에 표시됩니다.'
        }
      />
    )
  }

  return (
    <ul className="trade-grid">
      {data.content.map((trade) => (
        <li key={trade.tradeId} className={`trade-card ${CARD_TONE[trade.status] ?? ''}`}>
          <CardShell tradeId={trade.tradeId} onSelect={onSelect}>
            {/*
              상태를 맨 위 왼쪽에 둔다. 카드 아래에 있으면 여러 장을 훑을 때 시선이
              카드마다 끝까지 내려갔다 올라와야 한다 — 목록에서 먼저 읽는 것이 상태다.
            */}
            <div className="trade-card__status">
              <StatusBadge tone={TONE[trade.status]}>
                {TRADE_STATUS_LABEL[trade.status]}
              </StatusBadge>
              {/* 사용자가 뭔가 해야 하는 거래를 눈에 띄게 한다.
                  목록이 길어지면 "결제 안 한 게 뭐였지" 를 찾기 어려워진다.
                  판매자에게는 결제 완료 건이 곧 발송해야 할 건이다. */}
              {actionRequired.includes(trade.status) && (
                <span className="trade-card__action-hint">
                  {isSellerView ? '발송 필요' : '결제 필요'}
                </span>
              )}
            </div>
            {/*
              거래 번호는 적지 않는다. 고르는 기준은 소재 이름이지 번호가 아니다.
            */}
            <strong className="trade-card__name">{trade.materialName}</strong>
            {/*
              **누구와 한 거래인지 목록에서 보여야 한다.** 소재 이름이 비슷한 건이 여러
              줄이면(같은 소재를 여러 곳에서 산다) 상대를 알아야 어느 건인지 갈린다.
              보는 쪽에 따라 상대가 다르다 — 구매자는 판매자를, 판매자는 구매자를 본다.
            */}
            <span className="trade-card__party">
              <small>{isSellerView ? '구매자' : '판매자'}</small>
              {isSellerView ? trade.buyerName : trade.sellerName}
            </span>

            {/*
              수량과 금액은 한 줄의 양끝이다. 세로로 쌓으면 카드마다 값이 네 줄로 늘어서
              무엇이 이름이고 무엇이 숫자인지 훑어서는 갈리지 않는다.
            */}
            <div className="trade-card__footer">
              <span className="trade-card__quantity">{formatKg(trade.quantity)}</span>
              <span className="trade-card__price">{formatWon(trade.totalPrice)}</span>
            </div>
          </CardShell>
        </li>
      ))}
    </ul>
  )
}

/**
 * 카드 껍데기.
 *
 * 누르면 팝업을 여는 화면과 주소로 넘어가는 화면이 같은 카드를 쓴다. 링크로만 두면 팝업
 * 쪽에서 새 탭·가운데 클릭이 상세 주소로 새 버리고, 버튼으로만 두면 링크 쪽에서 주소를
 * 복사할 수 없다 — 그래서 쓰는 쪽에 맞춰 태그를 바꾼다.
 */
function CardShell({
  tradeId,
  onSelect,
  children,
}: {
  tradeId: number
  onSelect?: (tradeId: number) => void
  children: ReactNode
}) {
  if (onSelect) {
    return (
      <button type="button" className="trade-card__link" onClick={() => onSelect(tradeId)}>
        {children}
      </button>
    )
  }

  return (
    <Link to={`/trades/${tradeId}`} className="trade-card__link">
      {children}
    </Link>
  )
}
