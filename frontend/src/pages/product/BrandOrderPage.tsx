import { BrandOrderList } from '../../features/product/components/BrandOrderList.tsx'

/**
 * 제품 발송 관리 (Brand).
 *
 * **소비자 주문이 여기서 멈춰 있었다.** 결제까지 끝난 주문을 보낼 화면이 없어 주문이
 * `PAID` 에 머물렀고, 그러면 소비자의 수령 확인도 열리지 않는다.
 *
 * 제품 관리(`/brand/products`)와 나눈다. 그쪽은 "무엇을 파는가" 이고 여기는 "무엇을 오늘
 * 보내야 하는가" 다 — 두 일의 주기가 다르다 (판매 관리와 판매 거래를 나눈 것과 같은 판단이다).
 */
export function BrandOrderPage() {
  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 발송 관리</h1>

      <BrandOrderList />
    </main>
  )
}
