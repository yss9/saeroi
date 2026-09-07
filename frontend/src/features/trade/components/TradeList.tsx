import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import { useTrades } from '../hooks/useTrades.ts'
import {
  BUYER_ACTION_REQUIRED,
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

  if (isPending) return <Loading variant="skeleton" rows={3} label="거래 내역을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.content.length === 0) {
    return (
      <EmptyState
        title="거래 내역이 없습니다"
        description="마켓플레이스에서 소재를 구매하면 여기에 표시됩니다."
      />
    )
  }

  return (
    <ul className="trade-list">
      {data.content.map((trade) => (
        <li key={trade.tradeId} className="trade-list__item">
          <Link to={`/trades/${trade.tradeId}`} className="trade-list__link">
            <div className="trade-list__main">
              <span className="trade-list__code">{trade.tradeCode}</span>
              <strong className="trade-list__name">{trade.materialName}</strong>
              <span className="trade-list__quantity">{formatKg(trade.quantity)}</span>
            </div>
            <div className="trade-list__side">
              <span className="trade-list__price">{formatWon(trade.totalPrice)}</span>
              <StatusBadge tone={TONE[trade.status]}>
                {TRADE_STATUS_LABEL[trade.status]}
              </StatusBadge>
              {/* 사용자가 뭔가 해야 하는 거래를 눈에 띄게 한다.
                  목록이 길어지면 "결제 안 한 게 뭐였지" 를 찾기 어려워진다. */}
              {BUYER_ACTION_REQUIRED.includes(trade.status) && (
                <span className="trade-list__action-hint">확인 필요</span>
              )}
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}
