import { useAuth } from '../../auth/hooks/useAuth.ts'
import { ErrorMessage, Loading } from '../../../components/common/index.ts'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import { useTrade } from '../hooks/useTrade.ts'
import { PaymentPanel } from './PaymentPanel.tsx'
import { ReceiptPanel } from './ReceiptPanel.tsx'
import { ShipmentPanel } from './ShipmentPanel.tsx'
import { TradeChainPanel } from './TradeChainPanel.tsx'
import { TradeChainReleaseButton } from './TradeChainReleaseButton.tsx'
import { TradeTimeline } from './TradeTimeline.tsx'
import './trade.css'

export interface TradeDetailProps {
  tradeId: number
}

/**
 * 거래 상세 본문.
 *
 * **판매자와 구매자가 같은 것을 열고 서로 다른 것을 한다.** 거래에는 양쪽이 각자 해야 할
 * 일이 있고(구매자는 결제·수령 확인, 판매자는 발송), 서버도 한 엔드포인트로 양쪽에 상세를
 * 내려준다. 그래서 여기서 당사자를 구분해 패널을 고른다.
 *
 * 구분 기준은 **역할(Role)이 아니라 이 거래에서의 위치**다. `SUPPLIER` 라도 남의 거래의
 * 판매자가 아니면 발송할 수 없고(서버가 `seller_id` 로 대조한다), 정책 07.1 대로 Brand
 * 재판매가 열리면 `BRAND` 도 판매자 자리에 선다. 역할로 갈랐다면 그때 이 화면이 조용히
 * 틀린다.
 *
 * 화면 껍데기(돌아갈 길·모달 테두리)는 부르는 쪽이 정한다 — 목록에서는 팝업으로 뜨고
 * 주소로 바로 들어오면 한 페이지다.
 */
export function TradeDetail({ tradeId }: TradeDetailProps) {
  const { user } = useAuth()
  const { data, isPending, isError, error, refetch } = useTrade(tradeId)

  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  const isSeller = user?.id === data.sellerId
  const isBuyer = user?.id === data.buyerId

  return (
    <div className="trade-detail">
      {/* 거래 번호(TRD-…)는 적지 않는다. 사람이 읽고 판단하는 값이 아니고, 어느 거래인지는
          소재 이름과 아래 수량·금액이 말한다. */}
      <h2 className="trade-detail__title">{data.materialName}</h2>

      <dl className="trade-detail__meta">
        <div>
          <dt>거래 수량</dt>
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
        {/* 판매자에게는 "누가 샀는지" 가 발송에 필요한 정보다. */}
        <div>
          <dt>구매자</dt>
          <dd>{data.buyerName}</dd>
        </div>
      </dl>

      {/* 진행 상태는 양쪽이 같은 것을 본다. 무엇이 끝났고 지금 누구를 기다리는지가
          결제·발송 버튼보다 먼저 보여야 한다 — 사용자가 이 화면에 오는 이유다. */}
      <TradeTimeline trade={data} viewer={isSeller ? 'SELLER' : isBuyer ? 'BUYER' : undefined} />

      {/* 구매자: 결제 → 수령 확인. 두 패널을 함께 두는 이유는 결제 직후 다음에 무엇을
          기다려야 하는지가 같은 화면에 보여야 하기 때문이다. */}
      {isBuyer && (
        <>
          <PaymentPanel trade={data} />

          {/*
            **결제를 마치고 발송을 기다리는 동안에는 아무 칸도 두지 않는다.** 그 구간에서
            구매자가 할 수 있는 일이 없는데, 온체인 기록과 수령 확인이 버튼처럼 서 있으면
            지금 눌러야 하는 것으로 읽힌다 — 수령 확인은 물건을 받은 뒤의 일이다.

            온체인 주문은 결제와 별개다 — 컨트랙트에 금액이 없고(정책 v3), 체인이 남기는
            것은 "누가 무엇을 얼마나 사기로 했는가" 뿐이다. 그래서 결제 직후(CREATED)에
            남기고, 놓친 거래는 오프체인 기록만 남는다(TradeChainPanel 의 tooLate).

            <b>온체인 수령 버튼을 따로 두지 않는다.</b> ReceiptPanel 의 「수령 확인」이
            서버 기록에 성공하면 곧바로 `chain.receive` 로 이어간다. 예전에는 버튼이 둘
            보여서, 온체인 쪽을 먼저 누르면 서버는 모른 채 체인만 앞서가고 이어서
            「수령 확인」을 누르면 이미 RECEIVED 인 주문에 다시 서명하려다 되돌려졌다.
          */}
          {data.status !== 'PAID' && (
            <>
              <TradeChainPanel trade={data} />
              {/*
                취소된 거래인데 체인에는 예약이 남은 경우를 정리한다. 서버 취소는 결제
                마감 배치가 자동으로 하지만 체인은 당사자 서명이 있어야 풀린다 — 풀지
                않으면 판매자가 그 수량을 다시 팔 수 없다. 남아 있을 때만 나온다.
              */}
              <TradeChainReleaseButton trade={data} />
              <ReceiptPanel trade={data} />
            </>
          )}
        </>
      )}

      {/* 판매자: 발송. 결제 패널을 보여주지 않는다 — 판매자가 부를 수 있는 요청이 아니고,
          "결제하기" 버튼이 보이면 자기가 결제해야 하는 것처럼 읽힌다. */}
      {isSeller && (
        <>
          {/* 발송도 같다 — ShipmentPanel 이 서버 기록 뒤에 `chain.ship` 을 이어서 부른다. */}
          <ShipmentPanel trade={data} />
        </>
      )}

    </div>
  )
}
