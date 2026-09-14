import { House } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { ConsumerAddressBook } from '../../features/user/components/ConsumerAddressBook.tsx'

/**
 * 배송지 관리.
 *
 * **마이페이지와 같은 껍데기를 쓴다.** 두 화면이 다른 껍데기를 쓰면 오갈 때마다 머리말과
 * 푸터가 바뀐다 — 사용자는 다른 사이트로 넘어간 것처럼 읽는다.
 *
 * 사이드바 메뉴에는 두지 않는다. 마이페이지의 기본 배송지 카드가 이리로 데려온다.
 *
 * 주문 화면이 생기면 결제 단계에서도 이 목록을 고르게 된다 — 그때 이 화면은 그대로 두고
 * 선택 UI 만 따로 만든다. 관리와 선택은 사용자가 하려는 일이 다르다.
 */
export function ConsumerAddressPage() {
  return (
    <ConsumerAccountLayout>
      <main className="consumer-mypage-main">
        <nav className="consumer-breadcrumb" aria-label="현재 위치">
          <Link to="/">
            <House aria-hidden="true" /> 홈
          </Link>
          <span aria-hidden="true">/</span>
          <Link to="/mypage">마이페이지</Link>
          <span aria-hidden="true">/</span>
          <span aria-current="page">배송지 관리</span>
        </nav>

        <section className="consumer-mypage-card">
          <div className="consumer-section-heading">
            <div>
              <p className="consumer-section-heading__eyebrow">DELIVERY</p>
              <h1>배송지 관리</h1>
            </div>
          </div>

          <p className="consumer-address-page__lead">
            주문할 때 쓸 배송지를 등록해 두면 주소를 다시 입력하지 않아도 됩니다. 기본 배송지는
            주문 화면에서 먼저 선택됩니다.
          </p>

          <ConsumerAddressBook />
        </section>
      </main>
    </ConsumerAccountLayout>
  )
}
