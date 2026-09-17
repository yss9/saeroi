import { useParams } from 'react-router-dom'
import { BackLink, ErrorMessage, Loading } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { PaymentPanel } from '../../features/trade/components/PaymentPanel.tsx'
import { TradeChainPanel } from '../../features/trade/components/TradeChainPanel.tsx'
import { ReceiptPanel } from '../../features/trade/components/ReceiptPanel.tsx'
import { ShipmentPanel } from '../../features/trade/components/ShipmentPanel.tsx'
import { TradeTimeline } from '../../features/trade/components/TradeTimeline.tsx'
import { useTrade } from '../../features/trade/hooks/useTrade.ts'
import { formatKg, formatWon } from '../../utils/quantity.ts'
import {
  PAYMENT_METHOD_LABEL,
  TRADE_STATUS_LABEL,
  type TradeStatus,
} from '../../features/trade/types/trade.types.ts'
import { formatTradeDateTime } from '../../features/trade/utils/tradeDateTime.ts'
import '../../components/common/console.css'

/**
 * 돌아갈 목록.
 *
 * 문구와 경로를 한 쌍으로 묶는다 — 따로 두면 "판매 거래" 라고 적힌 링크가 구매 내역으로
 * 가는 조합이 만들어진다. 실제로 이 화면은 같은 판단을 네 군데에서 한다.
 */
const PURCHASES = { to: '/trades', label: '구매 내역' } as const
const SALES = { to: '/supplier/trades', label: '판매 거래' } as const

/**
 * 거래 상세.
 *
 * **판매자와 구매자가 같은 주소를 쓰고 서로 다른 것을 한다.** 거래에는 양쪽이 각자
 * 해야 할 일이 있고(구매자는 결제·수령 확인, 판매자는 발송), 서버도 한 엔드포인트로
 * 양쪽에 상세를 내려준다. 그래서 주소를 나누지 않고 여기서 당사자를 구분해 패널을 고른다.
 *
 * 구분 기준은 **역할(Role)이 아니라 이 거래에서의 위치**다. `SUPPLIER` 라도 남의 거래의
 * 판매자가 아니면 발송할 수 없고(서버가 `seller_id` 로 대조한다), 정책 07.1 대로
 * Brand 재판매가 열리면 `BRAND` 도 판매자 자리에 선다. 역할로 갈랐다면 그때 이 화면이
 * 조용히 틀린다.
 *
 * <b>주소를 `/supplier/trades/:id` 와 `/brand/trades/:id` 로 나누지 않는다.</b> 세 가지가
 * 걸린다.
 *
 *   1) <b>URL 이 거짓말을 한다.</b> 역할과 당사자가 다르므로 `/brand/trades/5` 는 그
 *      브랜드가 구매자인지 판매자인지 말해주지 않는다. 재판매가 열리는 순간 틀린다.
 *   2) <b>링크가 깨진다.</b> 거래는 두 회사가 같이 보는 하나의 개체다. 알림이나 메일에
 *      붙은 주소가 상대에게는 /403 이 된다.
 *   3) <b>얻는 보안이 없다.</b> 당사자 판정은 서버가 `seller_id`·`buyer_id` 로 한다.
 *      라우트 가드는 UX 장치일 뿐이라 주소를 나눠도 막아 주는 것이 없다.
 *
 * <b>대신 목록을 나눈다.</b> `/supplier/trades`(판매)와 `/trades`(구매)가 이미 갈라져
 * 있다 — 관점이 다른 것은 목록이고, 거래 자체는 하나다. 메일함이 받은편지함과
 * 보낸편지함으로 나뉘어도 메일 한 통은 한 개인 것과 같다.
 */
export function TradeDetailPage() {
  const { tradeId } = useParams<{ tradeId: string }>()
  const parsed = Number(tradeId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { user, role } = useAuth()
  const { data, isPending, isError, error, refetch } = useTrade(id)

  // 조회 전에는 이 사람이 판매자인지 알 수 없다 (판정에 data 가 필요하다). 그래서 역할로
  // 고른다 — **구매 내역(/trades)은 BRAND 전용이라 판매자를 그리로 보내면 /403 이 된다.**
  // 역할은 거래를 읽기 전에도 알 수 있어 이 구간에서 쓸 수 있는 유일한 단서다.
  const fallback = role === 'BRAND' ? PURCHASES : SALES

  if (id === null) {
    return (
      <main className="console">
        <BackLink {...fallback} />
        <ErrorMessage error="잘못된 주소입니다." />
      </main>
    )
  }
  if (isPending) {
    return (
      <main className="console">
        <BackLink {...fallback} />
        <Loading variant="skeleton" />
      </main>
    )
  }
  if (isError) {
    return (
      <main className="console">
        <BackLink {...fallback} />
        <ErrorMessage error={error} onRetry={() => void refetch()} />
      </main>
    )
  }

  const isSeller = user?.id === data.sellerId
  const isBuyer = user?.id === data.buyerId

  return (
    <main className="console">
      {/* 판매자와 구매자가 서로 다른 목록에서 들어온다. 위치에 맞는 곳으로 돌려보낸다 —
          판매자를 /trades(내 구매 내역)로 보내면 방금 본 거래가 없는 목록이 열린다.
          여기서는 역할이 아니라 이 거래에서의 위치로 고른다. 이미 거래를 읽었으므로
          그쪽이 더 정확하다 (BRAND 가 판매자 자리에 서는 재판매도 맞게 처리된다). */}
      <BackLink {...(isSeller ? SALES : PURCHASES)} />

      <header className="console__head trade-detail__head">
        <div className="console__title">
          <h1>{data.materialName}</h1>
          <StatusChip status={data.status} viewer={isSeller ? 'SELLER' : 'BUYER'} />
        </div>
        <span className="console__tag">{data.tradeCode}</span>
      </header>

      {/*
        <b>상대방만 적는다.</b> 예전에는 판매자와 구매자를 둘 다 적었는데, 그중 하나는
        언제나 이 화면을 보는 본인이다 — 내 화면에 내 회사 이름이 적혀 있는 셈이었다.
        정작 필요한 것은 "누구와 거래하는가" 하나다.
      */}
      {/*
        <b>2단이다.</b> 왼쪽은 지금 할 일(발송·결제·수령), 오른쪽은 사실(요약·진행).
        세로 한 줄로 쌓으면 화면이 넓을수록 허전해지고, 정작 손댈 곳이 스크롤 어딘가에
        묻힌다 — 이 화면에 오는 이유는 "내가 지금 뭘 해야 하나" 하나다.
      */}
      <div className="trade-detail__layout">
        <div className="trade-detail__main">
          {/* 구매자: 결제 → 수령 확인. 두 패널을 함께 두는 이유는 결제 직후 다음에 무엇을
              기다려야 하는지가 같은 화면에 보여야 하기 때문이다. */}
          {isBuyer && (
            <>
              <PaymentPanel trade={data} />
              {/* 온체인 주문은 결제와 별개다 — 컨트랙트에 금액이 없고(정책 v3), 체인이 남기는
                  것은 "누가 무엇을 얼마나 사기로 했는가" 뿐이다. 그래서 결제 실패와 체인 실패가
                  서로를 막지 않도록 패널을 나란히 둔다. */}
              <TradeChainPanel trade={data} />
              {/*
                <b>온체인 수령 버튼을 따로 두지 않는다.</b> ReceiptPanel 의 「수령 확인」이
                서버 기록에 성공하면 곧바로 `chain.receive` 로 이어간다.

                예전에는 여기에 TradeChainActionPanel 이 함께 있어서 같은 시점에 버튼이 둘
                보였다. 온체인 버튼을 먼저 누르면 서버는 모른 채 체인만 앞서가고, 이어서
                「수령 확인」을 누르면 이미 RECEIVED 인 주문에 다시 서명하려다 되돌려진다
                ("order is not shipped").
              */}
              <ReceiptPanel trade={data} />
            </>
          )}

          {/* 판매자: 발송. 결제 패널을 보여주지 않는다 — 판매자가 부를 수 있는 요청이 아니고,
              "결제하기" 버튼이 보이면 자기가 결제해야 하는 것처럼 읽힌다. */}
          {isSeller && <ShipmentPanel trade={data} />}
        </div>

        {/* 오른쪽은 읽는 곳이다. 입력이 없고 스크롤을 따라온다. */}
        <aside className="trade-detail__side">
          <section className="console-panel trade-detail__card">
            <h2>거래 요약</h2>
            <dl className="trade-detail__meta">
            <div>
              <dt>{isSeller ? '구매자' : '판매자'}</dt>
              <dd>{isSeller ? data.buyerName : data.sellerName}</dd>
            </div>
            <div>
              <dt>수량 · 단가</dt>
              <dd className="console__num">
                {formatKg(data.quantity)} × {formatWon(data.pricePerKg)}/kg
              </dd>
            </div>
            {/*
              <b>총 금액이 화면에 없었다.</b> 수량과 단가만 있어서 사용자가 곱해야 했다.
              응답(`totalPrice`)에는 처음부터 있던 값이고, 돈 이야기에서 가장 먼저 보고 싶은
              숫자다. 그래서 여기만 크게 둔다.
            */}
            <div>
              <dt>소재</dt>
              <dd>
                {data.materialName} <span className="console__num">({data.materialCode})</span>
              </dd>
            </div>
            <div className="trade-detail__total">
              <dt>총 거래 금액</dt>
              <dd className="console__num">{formatWon(data.totalPrice)}</dd>
            </div>
            {/* 결제 수단과 시각. 총액 아래에 붙여 "얼마를 어떻게 받았나" 를 한 덩어리로 읽는다. */}
            <div className="trade-detail__paid">
              <dt>결제</dt>
              <dd className="console__num">
                {PAYMENT_METHOD_LABEL[data.paymentMethod]}
                {data.paidAt ? ` · ${formatTradeDateTime(data.paidAt)}` : ''}
              </dd>
            </div>
            </dl>
          </section>

          {/* 진행 상태. 무엇이 끝났고 지금 누구를 기다리는지가 버튼보다 먼저 보여야 한다. */}
          <section className="console-panel trade-detail__card">
            <h2>진행</h2>
            <TradeTimeline trade={data} viewer={isSeller ? 'SELLER' : isBuyer ? 'BUYER' : undefined} />
          </section>

          {/*
            <b>이력이 남았는지 한 줄로 말한다.</b> 판매자에게는 이 값이 "이 거래가 제대로
            기록되고 있나" 의 답이다 — 기록이 없으면 발송·수령도 체인에 남지 않는다.
            구매자에게는 TradeChainPanel 이 같은 일을 더 자세히 하므로 여기서는 빼고,
            판매자에게만 보여준다.
          */}
          {isSeller && <LedgerCard chainTradeId={data.chainTradeId} />}
        </aside>
      </div>

      {/* 당사자가 아니면 서버가 403 을 주므로 여기까지 오지 않는다. 그래도 로그인 정보가
          아직 복원되지 않은 순간이 있어(persist 복원 전) 아무 패널도 없는 화면이 잠깐
          보일 수 있다. 그때 빈 화면을 두면 고장으로 읽힌다. */}
      {!isBuyer && !isSeller && (
        <p className="page__lead">이 거래의 당사자만 결제·발송·수령 확인을 할 수 있습니다.</p>
      )}
    </main>
  )
}

/**
 * 상태 칩.
 *
 * <b>보는 사람에 따라 같은 상태가 다른 뜻이다.</b> `PAID` 는 판매자에게 "보낼 차례" 이고
 * 구매자에게는 "기다리는 중" 이다. 서버 라벨("결제 완료")을 그대로 쓰면 양쪽 다
 * 자기가 무엇을 해야 하는지 알 수 없다.
 */
function StatusChip({ status, viewer }: { status: TradeStatus; viewer: 'SELLER' | 'BUYER' }) {
  const mine =
    (viewer === 'SELLER' && status === 'PAID') ||
    (viewer === 'BUYER' && (status === 'CREATED' || status === 'SHIPPED'))

  const text =
    viewer === 'SELLER' && status === 'PAID'
      ? '발송 필요'
      : viewer === 'BUYER' && status === 'CREATED'
        ? '결제 필요'
        : viewer === 'BUYER' && status === 'SHIPPED'
          ? '수령 확인 필요'
          : TRADE_STATUS_LABEL[status]

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
