import { Link, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useConsumerAddresses } from '../hooks/useConsumerAddresses.ts'
import { useCreateProductOrder } from '../hooks/useCreateProductOrder.ts'
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
 * **결제 단계가 없다.** 결제 수단이 정해지지 않아 주문만 만들고 마이페이지에서 확인하게
 * 한다. 그래서 "결제하기" 가 아니라 "구매하기" 이고, 누르면 결제창 대신 주문 내역으로
 * 넘어간다 — 결제를 기다리는 화면이 없다는 것을 동선으로 알린다.
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
  const navigate = useNavigate()
  const addresses = useConsumerAddresses()
  const order = useCreateProductOrder()

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

      {/* 주문 실패는 버튼 옆에 둔다 — 목록 위에 띄우면 누른 자리에서 보이지 않는다. */}
      {order.isError && <ErrorMessage error={order.error} />}

      {blockedReason && <p className="purchase-panel__blocked">{blockedReason}</p>}

      <Button
        fullWidth
        loading={order.isPending}
        disabled={!purchasable || defaultAddress === undefined}
        onClick={() => {
          if (defaultAddress === undefined) return
          order.mutate(
            { productModelId, addressId: defaultAddress.addressId },
            // 주문이 만들어진 것을 확인할 수 있는 곳으로 보낸다. 같은 화면에 남겨 두면
            // 눌렀는데 아무 일도 없는 것처럼 보인다 — 결제창이 뜨지 않기 때문에 더 그렇다.
            { onSuccess: () => navigate('/mypage/orders') },
          )
        }}
      >
        구매하기
      </Button>

      <p className="purchase-panel__note">
        결제 단계는 아직 준비 중입니다. 지금은 주문만 만들어지고, 마이페이지 주문 내역에서
        확인할 수 있습니다.
      </p>
    </section>
  )
}
