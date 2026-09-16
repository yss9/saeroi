import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import { useTrades } from '../hooks/useTrades.ts'
import {
  BUYER_ACTION_REQUIRED,
  SELLER_ACTION_REQUIRED,
  TRADE_STATUS_LABEL,
  type TradeFilters,
  type TradeStatus,
} from '../types/trade.types.ts'

const TONE: Record<TradeStatus, 'neutral' | 'info' | 'warning' | 'success'> = {
  CREATED: 'warning',
  PAID: 'info',
  SHIPPED: 'info',
  RECEIVED: 'info',
  COMPLETED: 'success',
  CANCELED: 'neutral',
}

export interface TradeListProps {
  filters?: TradeFilters
}

export function TradeList({ filters }: TradeListProps) {
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
        <li key={trade.tradeId} className="trade-card">
          <Link to={`/trades/${trade.tradeId}`} className="trade-card__link">
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
                  {isSellerView ? '발송 필요' : '확인 필요'}
                </span>
              )}
            </div>
            {/*
              거래 번호는 적지 않는다. 카드에서 고르는 기준은 소재 이름이지 번호가 아니고,
              번호가 맨 위에 있으면 이름보다 먼저 읽힌다. 상세 화면에는 그대로 있다.
            */}
            <strong className="trade-card__name">{trade.materialName}</strong>
            <span className="trade-card__quantity">{formatKg(trade.quantity)}</span>
            <span className="trade-card__price">{formatWon(trade.totalPrice)}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
