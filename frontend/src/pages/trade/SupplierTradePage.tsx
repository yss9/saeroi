import { SellerTradeBoard } from '../../features/trade/components/SellerTradeBoard.tsx'

/**
 * 판매 거래 관리 (Supplier).
 *
 * 판매 등록은 `/supplier/listings` 가, 그 판매글에서 발생한 **거래** 는 이 화면이 맡는다.
 * 한 화면에 합치지 않은 이유는 두 일의 주기가 다르기 때문이다 — 판매글은 한 번 올리고
 * 놔두지만, 거래는 결제될 때마다 발송이라는 할 일이 생긴다.
 *
 * 목록에서 거래를 고르면 `/trades/:tradeId` 로 간다. 발송 처리는 거기서 한다 —
 * 배송지와 송장이 거래 한 건의 정보라 상세 화면이 제자리다.
 */
export function SupplierTradePage() {
  return (
    <main className="page">
      <header className="page__header">
        <h1>판매 거래 관리</h1>
        <p className="page__lead">
          결제가 완료된 거래를 발송 처리하고 배송 상태를 확인합니다. 소유권은 구매자가 수령을
          확인하는 시점에 이전됩니다.
        </p>
      </header>

      <SellerTradeBoard />
    </main>
  )
}
