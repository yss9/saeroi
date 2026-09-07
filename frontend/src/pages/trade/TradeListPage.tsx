import { TradeList } from '../../features/trade/components/TradeList.tsx'

/** 내 거래 내역. */
export function TradeListPage() {
  return (
    <main className="page">
      <header className="page__header">
        <h1>거래 내역</h1>
        <p className="page__lead">구매한 소재의 결제·배송·수령 상태를 확인합니다.</p>
      </header>
      <TradeList />
    </main>
  )
}
