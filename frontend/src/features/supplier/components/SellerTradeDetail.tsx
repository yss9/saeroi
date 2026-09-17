import { ShipmentPanel } from '../../trade/components/ShipmentPanel.tsx'
import { TradeTimeline } from '../../trade/components/TradeTimeline.tsx'
import { formatKg, formatWon } from '../../../utils/quantity.ts'
import {
  PAYMENT_METHOD_LABEL,
  TRADE_STATUS_LABEL,
  type Trade,
  type TradeStatus,
} from '../../trade/types/trade.types.ts'
import { formatTradeDateTime } from '../../trade/utils/tradeDateTime.ts'
import '../../../components/common/console.css'

/**
 * 거래 상세 — 판매자 자리에서 볼 때.
 *
 * <b>구매자 쪽(`TradeDetail`)과 따로 둔다.</b> 같은 거래를 열지만 하는 일이 반대다 —
 * 구매자는 결제하고 받고, 판매자는 보낸다. 구매자 화면은 목록에서 팝업으로도 뜨므로
 * 세로로 짧아야 하고, 판매자 화면은 주소로만 들어와 배송지·송장을 적을 자리가 넓어야
 * 한다. 한 컴포넌트에 넣으면 둘 중 하나는 반드시 어색해진다.
 *
 * <b>가르는 기준은 역할이 아니라 이 거래에서의 위치다.</b> `SUPPLIER` 라도 남의 거래의
 * 판매자가 아니면 발송할 수 없고(서버가 `seller_id` 로 대조한다), 정책 07.1 대로 Brand
 * 재판매가 열리면 `BRAND` 도 판매자 자리에 선다. 그때도 이 화면이 맞다.
 *
 * <b>2단이다.</b> 왼쪽은 지금 할 일(발송), 오른쪽은 사실(요약·진행·이력). 세로 한 줄로
 * 쌓으면 화면이 넓을수록 허전해지고 정작 손댈 곳이 스크롤 어딘가에 묻힌다 — 이 화면에
 * 오는 이유는 "내가 지금 뭘 보내야 하나" 하나다.
 */
export function SellerTradeDetail({ trade }: { trade: Trade }) {
  return (
    <>
      <header className="console__head trade-detail__head">
        <div className="console__title">
          <h1>{trade.materialName}</h1>
          <StatusChip status={trade.status} />
        </div>
        <span className="console__tag">{trade.tradeCode}</span>
      </header>

      <div className="trade-detail__layout">
        <div className="trade-detail__main">
          {/* 결제 패널을 보여주지 않는다 — 판매자가 부를 수 있는 요청이 아니고,
              "결제하기" 버튼이 보이면 자기가 결제해야 하는 것처럼 읽힌다. */}
          <ShipmentPanel trade={trade} />

          {/*
            <b>요약이 왼쪽 아래다.</b> 오른쪽 기둥(320px)에 두면 값이 전부 오른쪽 끝에
            몰려 라벨과 값 사이가 벌어지고, 정작 왼쪽 668px 은 발송 패널 아래가 비어
            있었다. 값이 짧아(대부분 100~160px) 가로로 늘어놓으면 한눈에 읽힌다.

            오른쪽에는 <b>시간에 따라 변하는 것</b>(진행·이력)만 남긴다 — 요약은 거래가
            만들어진 순간 정해져 더 바뀌지 않는 값이다.
          */}
          <section className="console-panel trade-detail__card trade-detail__card--wide">
            <h2>거래 요약</h2>
            <dl className="trade-detail__meta">
              {/*
                <b>상대방만 적는다.</b> 예전에는 판매자와 구매자를 둘 다 적었는데, 그중
                하나는 언제나 이 화면을 보는 본인이다 — 내 화면에 내 회사 이름이 적혀 있는
                셈이었다. 정작 필요한 것은 "누구에게 보내는가" 하나다.
              */}
              <div>
                <dt>구매자</dt>
                <dd>{trade.buyerName}</dd>
              </div>
              <div>
                <dt>수량 · 단가</dt>
                <dd className="console__num">
                  {formatKg(trade.quantity)} × {formatWon(trade.pricePerKg)}/kg
                </dd>
              </div>
              <div>
                <dt>소재</dt>
                <dd>
                  {trade.materialName} <span className="console__num">({trade.materialCode})</span>
                </dd>
              </div>
              {/*
                <b>총 금액이 화면에 없었다.</b> 수량과 단가만 있어서 사용자가 곱해야 했다.
                응답(`totalPrice`)에는 처음부터 있던 값이고, 돈 이야기에서 가장 먼저 보고
                싶은 숫자다. 그래서 여기만 크게 둔다.
              */}
              <div className="trade-detail__total">
                <dt>총 거래 금액</dt>
                <dd className="console__num">{formatWon(trade.totalPrice)}</dd>
              </div>
              {/* 결제 수단과 시각. 총액 아래에 붙여 "얼마를 어떻게 받았나" 를 한 덩어리로 읽는다. */}
              <div className="trade-detail__paid">
                <dt>결제</dt>
                <dd className="console__num">
                  {PAYMENT_METHOD_LABEL[trade.paymentMethod]}
                  {trade.paidAt ? ` · ${formatTradeDateTime(trade.paidAt)}` : ''}
                </dd>
              </div>
            </dl>
          </section>
        </div>

        {/* 오른쪽은 읽는 곳이다. 입력이 없고 스크롤을 따라온다. */}
        <aside className="trade-detail__side">

          {/* 진행 상태. 무엇이 끝났고 지금 누구를 기다리는지가 버튼보다 먼저 보여야 한다. */}
          <section className="console-panel trade-detail__card">
            <h2>진행</h2>
            <TradeTimeline trade={trade} viewer="SELLER" />
          </section>

          <LedgerCard chainTradeId={trade.chainTradeId} />
        </aside>
      </div>
    </>
  )
}

/**
 * 상태 칩.
 *
 * <b>같은 상태가 보는 사람에 따라 다른 뜻이다.</b> `PAID` 는 판매자에게 "보낼 차례" 이고
 * 구매자에게는 "기다리는 중" 이다. 서버 라벨("결제 완료")을 그대로 쓰면 자기가 무엇을
 * 해야 하는지 알 수 없다. 여기는 판매자 화면이므로 판매자의 뜻만 적는다.
 */
function StatusChip({ status }: { status: TradeStatus }) {
  const mine = status === 'PAID'
  const text = mine ? '발송 필요' : TRADE_STATUS_LABEL[status]

  if (status === 'CANCELED') return <span className="console-badge console-badge--bad">{text}</span>
  if (mine) return <span className="console-badge console-badge--wait">{text}</span>
  return (
    <span className="console-badge console-badge--ok">
      <span className="console-badge__dot" aria-hidden="true" />
      {text}
    </span>
  )
}

/**
 * 이력 기록 한 줄.
 *
 * <b>평소에는 조용해야 한다.</b> 잘 기록됐다는 사실은 사용자가 볼 이유가 없는 정보다.
 * 기록이 <b>없을 때만</b> 소리를 낸다 — 그 거래는 발송·수령도 체인에 남지 않아,
 * 최종 소비자에게 보여줄 이력이 끊긴다.
 *
 * 구매자에게는 `TradeChainPanel` 이 같은 일을 더 자세히 하므로 그쪽에는 두지 않는다.
 *
 * 기술 정보(기록 번호)는 접어 둔다. 심사나 문의 때 필요하지만 평소에 읽을 값은 아니다.
 */
function LedgerCard({ chainTradeId }: { chainTradeId: number | null }) {
  const recorded = chainTradeId !== null

  return (
    <section className={`console-panel trade-detail__card${recorded ? '' : ' trade-detail__card--todo'}`}>
      <h2>이력 기록</h2>
      <p className="trade-detail__ledger">
        <span className={`console-badge ${recorded ? 'console-badge--ok' : 'console-badge--wait'}`}>
          <span className="console-badge__dot" aria-hidden="true" />
          {recorded ? '기록 완료' : '미기록'}
        </span>
      </p>

      {recorded ? (
        <details className="trade-detail__ledger-more">
          <summary>자세히</summary>
          <dl>
            <div>
              <dt>기록 번호</dt>
              <dd className="console__num">#{chainTradeId}</dd>
            </div>
          </dl>
        </details>
      ) : (
        <p className="trade-detail__ledger-note">
          판매 등록 때 지갑 서명이 끝나지 않아 생긴 상태입니다. 발송·수령도 이력에 남지
          않습니다.
        </p>
      )}
    </section>
  )
}
