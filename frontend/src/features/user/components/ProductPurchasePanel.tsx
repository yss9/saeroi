import { Link } from 'react-router-dom'
import { Button, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useConsumerAddresses } from '../hooks/useConsumerAddresses.ts'
import { useProductOrderPayment } from '../hooks/useProductOrderPayment.ts'
import './consumer-mypage.css'

export interface ProductPurchasePanelProps {
  productModelId: number
  /** 살 수 있는 상태인지. 판매 중이 아니거나 재고가 없으면 false */
  purchasable: boolean
  /** 못 사는 이유. 버튼만 잠그면 사용자는 자기가 뭘 잘못했다고 생각한다 */
  blockedReason?: string
}

/**
 * 구매하기.
 *
 * **누르면 사이트를 떠난다.** 주문을 만든 뒤 카카오페이 결제창으로 이동하고, 인증을 마치면
 * 복귀 화면(`/orders/:orderId/payment/return`)이 승인을 끝낸다. 그래서 이 컴포넌트가 보는
 * 것은 "떠나기 전" 까지다.
 *
 * ⚠️ **주문을 만드는 순간 개체가 묶인다.** 제품 개체는 한 점씩이라 한 건만 묶여도 그 제품이
 * 즉시 품절로 보인다 — 결제를 포기하면 반드시 취소가 나가야 하고, 그 책임은 복귀 화면과
 * 이 훅(준비 실패 시)이 나눠 진다.
 *
 * **받을 곳을 먼저 보여준다.** 기본 배송지가 없으면 주문을 만들 수 없고(서버가 받을 곳을
 * 요구한다), 있더라도 어디로 가는지 모르고 누르게 하면 안 된다 — 주문하고 나서야 옛
 * 주소였다는 것을 알게 되는 것이 가장 나쁘다.
 */
export function ProductPurchasePanel({
  productModelId,
  purchasable,
  blockedReason,
}: ProductPurchasePanelProps) {
  const addresses = useConsumerAddresses()
  const payment = useProductOrderPayment()

  const defaultAddress = addresses.data?.find((address) => address.isDefault)

  if (addresses.isPending) {
    return (
      <section className="purchase-panel">
        <Loading label="배송지를 불러오는 중" />
      </section>
    )
  }

  return (
    <section className="purchase-panel" aria-labelledby="purchase-panel-title">
      <h3 id="purchase-panel-title">받을 곳</h3>

      {defaultAddress ? (
        <div className="purchase-panel__address">
          <strong>{defaultAddress.receiverName}</strong>
          <span>
            [{defaultAddress.zipCode}] {defaultAddress.address}
            {defaultAddress.addressDetail ? ` ${defaultAddress.addressDetail}` : ''}
          </span>
          <span>{defaultAddress.receiverPhone}</span>
          <Link to="/mypage/addresses">배송지 변경</Link>
        </div>
      ) : (
        <p className="purchase-panel__empty">
          기본 배송지가 없어 주문할 수 없습니다.{' '}
          <Link to="/mypage/addresses">배송지를 먼저 등록해 주세요.</Link>
        </p>
      )}

      {/* 실패는 버튼 옆에 둔다 — 목록 위에 띄우면 누른 자리에서 보이지 않는다. */}
      {payment.message && <ErrorMessage error={payment.message} />}

      {blockedReason && <p className="purchase-panel__blocked">{blockedReason}</p>}

      {payment.phase === 'SUCCEEDED' ? (
        <p className="purchase-panel__done">
          결제가 완료되었습니다. <Link to="/mypage/orders">주문 내역</Link>에서 확인하세요.
        </p>
      ) : payment.live === false ? (
        // 결제 키가 없는 환경이다. 결제창이 열리지 않으므로 그 사실을 알리고 여기서 끝낸다 —
        // 안 알리면 "버튼을 눌렀는데 아무 일도 안 난다" 로 읽힌다.
        <>
          <p className="purchase-panel__blocked">
            이 환경에는 결제 키가 없어 결제창이 열리지 않습니다. 시연용으로 승인만 진행합니다.
          </p>
          {/*
            `isBusy` 를 쓰지 않는다. 준비를 마친 뒤의 상태는 AWAITING_PG 인데, 실제 PG 에서는
            그것이 "결제창으로 떠나는 중" 이라 버튼을 잠가야 하지만 여기서는 떠나지 않는다 —
            그대로 잠그면 승인 버튼이 영원히 눌리지 않는다.
          */}
          <Button
            fullWidth
            loading={payment.phase === 'CONFIRMING'}
            onClick={() => void payment.approve()}
          >
            결제 승인 (시연)
          </Button>
        </>
      ) : (
        <Button
          fullWidth
          loading={payment.isBusy}
          disabled={!purchasable || defaultAddress === undefined}
          onClick={() => {
            if (defaultAddress === undefined) return
            void payment.purchase({ productModelId, addressId: defaultAddress.addressId })
          }}
        >
          구매하기
        </Button>
      )}

      <p className="purchase-panel__note">
        구매하기를 누르면 카카오페이 결제창으로 이동합니다. 결제를 마치기 전까지는 청구되지
        않습니다.
      </p>
    </section>
  )
}
