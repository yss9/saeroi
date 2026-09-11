import { TradeList } from '../../features/trade/components/TradeList.tsx'

/**
 * 내 소재 구매 내역.
 *
 * **`side` 를 보내지 않아 서버 기본값 `BUY` 가 쓰인다** — 이 화면은 내가 산 것만 다룬다.
 * 내가 판 거래는 `/supplier/trades` 가 `side=SELL` 로 본다. 그래서 이름이 "거래 내역" 이
 * 아니라 "구매 내역" 이다: 판매 거래에서 상세로 들어온 판매자가 사이드바에서 "거래 내역" 이
 * 켜지는 것을 보고 자기 판매 목록이 거기 있다고 읽었다.
 */
export function TradeListPage() {
  return (
    <main className="page">
      <header className="page__header">
        <h1>구매 내역</h1>
        <p className="page__lead">구매한 소재의 결제·배송·수령 상태를 확인합니다.</p>
      </header>
      <TradeList />
    </main>
  )
}
