import { useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { BackLink, ErrorMessage, Loading, Modal, StatusBadge } from '../../components/common/index.ts'
import { EvidenceThumbnailImage } from '../../features/evidence/components/EvidenceThumbnailImage.tsx'
import { useEvidenceThumbnails } from '../../features/evidence/hooks/useEvidenceThumbnails.ts'
import { useListing } from '../../features/marketplace/hooks/useListing.ts'
import { LISTING_STATUS_LABEL } from '../../features/marketplace/types/marketplace.types.ts'
import {
  GENERATION_TYPE_LABEL,
  MATERIAL_TYPE_LABEL,
  ROOT_ORIGIN_LABEL,
} from '../../features/material/types/material.types.ts'
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
  // 목록 카드와 같은 방식 — 조회가 끝나기 전에는 훅에 빈 배열을 준다(요청하지 않음).
  const thumbnails = useEvidenceThumbnails('MATERIAL', data ? [data.materialId] : [])
  // 목록에서 "구매하기" 로 들어오면 곧바로 연다 — 한 번 더 누르게 하지 않는다.
  const [buying, setBuying] = useState(useLocation().hash === '#purchase')

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

      {/*
        왼쪽에 사진, 오른쪽에 이름 → 설명 → 사실 → 값 → 구매하기 순으로 내려온다.
        살지 말지를 정하는 값 둘(단가·살 수 있는 양)을 버튼 바로 위에 크게 세운다 —
        누르기 직전에 마지막으로 확인하는 숫자다.
      */}
      <section className="listing-detail__box">
        {/* 목록 카드와 같은 자리·같은 사진 소스 — 소재 등록 시 첨부한 증빙 사진 중 대표 한 장. */}
        <div className="listing-detail__photo">
          <EvidenceThumbnailImage
            thumbnail={thumbnails.data?.[data.materialId]}
            alt={`${data.materialName} 사진`}
          />
          {data.grade && <span>{data.grade}등급</span>}
        </div>

        <div className="listing-detail__info">
          <div className="listing-detail__title">
            <h1>{data.materialName}</h1>
            <StatusBadge tone={data.status === 'ON_SALE' ? 'success' : 'neutral'}>
              {LISTING_STATUS_LABEL[data.status]}
            </StatusBadge>
          </div>

          {/* 소재를 등록할 때 판매자가 적은 설명. 없는 판매글이 많아 있을 때만 그린다. */}
          {data.materialDescription && (
            <p className="listing-detail__description">{data.materialDescription}</p>
          )}

          <dl className="listing-detail__meta">
            <div>
              <dt>소재 종류</dt>
              <dd>{MATERIAL_TYPE_LABEL[data.materialType]}</dd>
            </div>
            <div>
              <dt>출처</dt>
              <dd>{ROOT_ORIGIN_LABEL[data.rootOrigin]}</dd>
            </div>
            <div>
              <dt>생성 유형</dt>
              <dd>{GENERATION_TYPE_LABEL[data.generationType]}</dd>
            </div>
            <div>
              <dt>등급</dt>
              <dd>{data.grade ? `${data.grade}등급` : '미판정'}</dd>
            </div>
            <div>
              <dt>소재 코드</dt>
              <dd className="listing-detail__mono">{data.materialCode}</dd>
            </div>
            <div>
              <dt>판매자</dt>
              {/* 서버가 주는 이름은 대표자 개인 이름이다. 업체명은 응답에 없어 백엔드에 요청해 뒀다. */}
              <dd>{data.sellerName}</dd>
            </div>
          </dl>

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

          {/* 다음 걸음은 오른쪽 아래다 — 값을 읽고 내려온 눈이 그대로 닿는다.
              살 수 없는 판매글에는 내지 않는다. 눌러도 잠긴 폼이 나올 뿐이다. */}
          {data.status === 'ON_SALE' && (
            <div className="listing-detail__actions">
              <button
                type="button"
                className="listing-detail__buy"
                onClick={() => setBuying(true)}
              >
                구매하기
              </button>
            </div>
          )}
        </div>
      </section>

      {/* 판매자 본인에게만 보인다 — 패널이 스스로 판정한다. 구매자에게는 아무것도 그리지
          않으므로 여기서 역할을 다시 따지지 않는다. */}
      <ListingChainPanel listing={data} />

      {/*
        구매 요청은 **물어보는 화면**이라 상세 아래에 늘 펼쳐 둘 이유가 없다. 수량·배송지
        여섯 칸이 언제나 열려 있으면 소재를 보러 온 사람도 그 폼을 지나쳐 스크롤해야 한다.
      */}
      <Modal open={buying} title="구매 요청" onClose={() => setBuying(false)}>
        <PurchaseForm
          listing={data}
          // 주문은 이미 만들어졌다. replace 를 쓰지 않으면 뒤로 가기가 구매 폼으로
          // 돌아오고, 사용자는 방금 산 것이 취소된 줄 알고 한 번 더 사기 쉽다 —
          // 그러면 같은 판매 글에 거래가 둘 생긴다.
          onCreated={(tradeId) => navigate(`/trades/${tradeId}`, { replace: true })}
        />
      </Modal>
    </main>
  )
}
