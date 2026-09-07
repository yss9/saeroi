import { useParams } from 'react-router-dom'
import { ErrorMessage, Loading } from '../../components/common/index.ts'
import { PaymentPanel } from '../../features/trade/components/PaymentPanel.tsx'
import { useTrade } from '../../features/trade/hooks/useTrade.ts'
import { formatKg, formatWon } from '../../utils/quantity.ts'

/** 거래 상세 + 결제. */
export function TradeDetailPage() {
  const { tradeId } = useParams<{ tradeId: string }>()
  const parsed = Number(tradeId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { data, isPending, isError, error, refetch } = useTrade(id)

  if (id === null) return <ErrorMessage error="잘못된 주소입니다." />
  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  return (
    <main className="page">
      <header className="page__header">
        <h1>{data.materialName}</h1>
        <p className="page__lead">{data.tradeCode}</p>
      </header>

      <dl className="trade-detail__meta">
        <div>
          <dt>구매 수량</dt>
          <dd>{formatKg(data.quantity)}</dd>
        </div>
        <div>
          <dt>kg당 단가</dt>
          <dd>{formatWon(data.pricePerKg)}</dd>
        </div>
        <div>
          <dt>판매자</dt>
          <dd>{data.sellerName}</dd>
        </div>
      </dl>

      <PaymentPanel trade={data} />
    </main>
  )
}
