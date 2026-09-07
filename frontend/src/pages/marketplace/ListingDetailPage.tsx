import { useNavigate, useParams } from 'react-router-dom'
import { ErrorMessage, Loading, StatusBadge } from '../../components/common/index.ts'
import { useListing } from '../../features/marketplace/hooks/useListing.ts'
import { LISTING_STATUS_LABEL } from '../../features/marketplace/types/marketplace.types.ts'
import { ROOT_ORIGIN_LABEL } from '../../features/material/types/material.types.ts'
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

  if (id === null) return <ErrorMessage error="잘못된 주소입니다." />
  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  const sold = data.totalQuantity - data.availableQuantity

  return (
    <main className="page listing-detail">
      <header className="listing-detail__header">
        <div>
          <h1>{data.materialName}</h1>
          <p className="listing-detail__code">{data.materialCode}</p>
        </div>
        <StatusBadge tone={data.status === 'ON_SALE' ? 'success' : 'neutral'}>
          {LISTING_STATUS_LABEL[data.status]}
        </StatusBadge>
      </header>

      {data.materialDescription && (
        <p className="listing-detail__description">{data.materialDescription}</p>
      )}

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
          <dt>kg당 단가</dt>
          <dd>{formatWon(data.pricePerKg)}</dd>
        </div>
        <div>
          <dt>판매 가능</dt>
          <dd>{formatKg(data.availableQuantity)}</dd>
        </div>
        <div>
          <dt>총 등록 수량</dt>
          <dd>
            {formatKg(data.totalQuantity)}
            {sold > 0 && <span className="listing-detail__sold"> · {formatKg(sold)} 판매됨</span>}
          </dd>
        </div>
        <div>
          <dt>판매자</dt>
          <dd>{data.sellerName}</dd>
        </div>
      </dl>

      <PurchaseForm listing={data} onCreated={(tradeId) => navigate(`/trades/${tradeId}`)} />
    </main>
  )
}
