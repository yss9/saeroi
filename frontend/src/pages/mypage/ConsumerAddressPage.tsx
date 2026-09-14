import { BackLink } from '../../components/common/index.ts'
import { ConsumerAddressBook } from '../../features/user/components/ConsumerAddressBook.tsx'

/**
 * 배송지 관리.
 *
 * **마이페이지 안에서 들어가는 화면이라 사이드바에 두지 않는다.** 기본 배송지 카드가
 * 이리로 데려온다.
 *
 * 주문 화면이 생기면 결제 단계에서도 이 목록을 고르게 된다 — 그때 이 화면은 그대로 두고
 * 선택 UI 만 따로 만든다. 관리와 선택은 사용자가 하려는 일이 다르다.
 */
export function ConsumerAddressPage() {
  return (
    <main className="page">
      <BackLink to="/mypage" label="마이페이지" />

      <header className="page__header">
        <h1>배송지 관리</h1>
        <p className="page__lead">
          주문할 때 쓸 배송지를 등록해 두면 주소를 다시 입력하지 않아도 됩니다. 기본 배송지는
          주문 화면에서 먼저 선택됩니다.
        </p>
      </header>

      <ConsumerAddressBook />
    </main>
  )
}
