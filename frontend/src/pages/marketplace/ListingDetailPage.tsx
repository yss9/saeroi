import { useNavigate, useParams } from 'react-router-dom'
import { BackLink, ErrorMessage, Loading, StatusBadge } from '../../components/common/index.ts'
import { useListing } from '../../features/marketplace/hooks/useListing.ts'
import { LISTING_STATUS_LABEL } from '../../features/marketplace/types/marketplace.types.ts'
import { ROOT_ORIGIN_LABEL } from '../../features/material/types/material.types.ts'
import { ListingChainPanel } from '../../features/marketplace/components/ListingChainPanel.tsx'
import { PurchaseForm } from '../../features/trade/components/PurchaseForm.tsx'
import { formatKg, formatWon } from '../../utils/quantity.ts'

/** 판매 상세 + 부분 구매 (Brand Market). */
export function ListingDetailPage() {
  const { listingId } = useParams<{ listingId: string }>()
  const navigate = useNavigate()

  // 라우트 파라미터는 사용자가 고칠 수 있다. 숫자로 파싱되지 않으면 조회하지 않는다.
  const parsed = Number(listingId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { data, isPending, isError, error, refetch } = useListing(id)

  // Public 라우트라 사이드바 셸이 없다. 조회가 실패한 화면에서도 나갈 길이 있어야
  // 하므로 이른 반환마다 함께 그린다.
  const back = <BackLink to="/marketplace" label="소재 검색" />

  if (id === null) {
    return (
      <main className="page">
        {back}
        <ErrorMessage error="잘못된 주소입니다." />
      </main>
    )
  }
  if (isPending) {
    return (
      <main className="page">
        {back}
        <Loading variant="skeleton" />
      </main>
    )
  }
  if (isError) {
    return (
      <main className="page">
        {back}
        <ErrorMessage error={error} onRetry={() => void refetch()} />
      </main>
    )
  }

  const sold = data.totalQuantity - data.availableQuantity

  return (
    <main className="page listing-detail">
      {back}

      <header className="listing-detail__header">
        <div>
          <h1>{data.materialName}</h1>
          <p className="listing-detail__code">{data.materialCode}</p>
        </div>
        <StatusBadge tone={data.status === 'ON_SALE' ? 'success' : 'neutral'}>
          {LISTING_STATUS_LABEL[data.status]}
        </StatusBadge>
      </header>

      {/*
        살지 말지를 정하는 값 둘(단가·살 수 있는 양)을 위에 크게 세우고, 나머지는 아래
        한 줄로 깐다. 예전에는 여섯 칸이 모두 같은 크기라 무엇을 먼저 봐야 할지 없었다.
      */}
      <section className="listing-detail__box">
        <div className="listing-detail__figures">
          <div>
            <span>kg당 단가</span>
            <strong>{formatWon(data.pricePerKg)}</strong>
          </div>
          <div>
            <span>구매 가능</span>
            <strong>{formatKg(data.availableQuantity)}</strong>
            {/* 목록 카드와 같은 막대. 남은 양을 채운다 — 숫자보다 빨리 읽힌다. */}
            <div
              className="listing-detail__bar"
              role="img"
              aria-label={`총 ${formatKg(data.totalQuantity)} 중 ${formatKg(data.availableQuantity)} 구매 가능`}
            >
              <span
                style={{
                  width: `${
                    data.totalQuantity > 0
                      ? Math.round((data.availableQuantity / data.totalQuantity) * 100)
                      : 0
                  }%`,
                }}
              />
            </div>
            <small>
              총 {formatKg(data.totalQuantity)}
              {sold > 0 && ` · ${formatKg(sold)} 판매됨`}
            </small>
          </div>
        </div>

        <dl className="listing-detail__meta">
          <div>
            <dt>출처</dt>
            <dd>{ROOT_ORIGIN_LABEL[data.rootOrigin]}</dd>
          </div>
          <div>
            <dt>등급</dt>
            <dd>{data.grade ? `${data.grade}등급` : '미판정'}</dd>
          </div>
          <div>
            <dt>판매자</dt>
            {/* 서버가 주는 이름은 대표자 개인 이름이다. 업체명은 응답에 없어 백엔드에 요청해 뒀다. */}
            <dd>{data.sellerName}</dd>
          </div>
        </dl>

        {/*
          소재 설명(`materialDescription`)은 그리지 않는다. 등록할 때 판매자가 자유롭게
          적는 메모라 "봉제 공장에서 구매한 새 원단" 처럼 이 판매 글과 어긋나는 문장이
          그대로 남고, 구매 판단에 쓰이는 값(출처·등급·단가·남은 양)은 위에 이미 있다.
          소재 상세(/materials/:id)에는 그대로 있다.
        */}
      </section>

      {/* 판매자 본인에게만 보인다 — 패널이 스스로 판정한다. 구매자에게는 아무것도 그리지
          않으므로 여기서 역할을 다시 따지지 않는다. */}
      <ListingChainPanel listing={data} />

      <PurchaseForm
        listing={data}
        // 주문은 이미 만들어졌다. replace 를 쓰지 않으면 뒤로 가기가 구매 폼으로
        // 돌아오고, 사용자는 방금 산 것이 취소된 줄 알고 한 번 더 사기 쉽다 —
        // 그러면 같은 판매 글에 거래가 둘 생긴다.
        onCreated={(tradeId) => navigate(`/trades/${tradeId}`, { replace: true })}
      />
    </main>
  )
}
