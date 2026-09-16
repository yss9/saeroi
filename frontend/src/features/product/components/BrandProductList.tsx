import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Button,
  ConfirmModal,
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { formatWon } from '../../../utils/quantity.ts'
import { useClickNavigation } from '../../../utils/useClickNavigation.ts'
import { useChangeProductModelStatus } from '../hooks/useChangeProductModelStatus.ts'
import { useMyProductModels } from '../hooks/useMyProductModels.ts'
import {
  PRODUCT_CATEGORY_LABEL,
  PRODUCT_MODEL_STATUS_LABEL,
  type ProductModel,
  type ProductModelStatus,
} from '../types/product.types.ts'
import './product.css'

/**
 * 상태 → 톤. 도메인 상태를 공통 컴포넌트가 알지 못하게 매핑을 여기 둔다.
 *
 * `DRAFT` 를 회색으로 두는 것이 중요하다. 판매 준비는 "아직 팔지 않는다" 는 뜻인데
 * 초록으로 칠하면 판매 중과 구분되지 않는다.
 */
const TONE: Record<ProductModelStatus, StatusTone> = {
  DRAFT: 'neutral',
  ON_SALE: 'success',
  SOLD_OUT: 'warning',
  DISCONTINUED: 'danger',
}

/**
 * 브랜드가 등록한 제품 관리.
 *
 * **마켓 목록과 다른 목록이다.** 이쪽은 `DRAFT` 를 포함한다 — 브랜드는 여기서 판매 시작을
 * 눌러야 하는데 준비 중인 제품이 안 보이면 그 버튼을 놓을 자리가 없다.
 *
 * 재고는 개체를 세어 나온 값이라 컬럼이 아니다. **등록만 된 제품은 언제나 0** 이고,
 * 그래서 판매 시작이 막힌다 (`PRODUCT_MODEL_HAS_NO_STOCK`). 버튼을 눌러 보고 알게 하지
 * 않고 왜 못 누르는지 미리 적는다.
 */
export function BrandProductList() {
  const followClick = useClickNavigation()
  const { data, isPending, isError, error, refetch } = useMyProductModels()
  const change = useChangeProductModelStatus()

  // 단종은 되돌릴 수 없다. 확인 단계를 거칠 대상을 들고 있는다.
  const [discontinuing, setDiscontinuing] = useState<ProductModel | null>(null)

  if (isPending) return <Loading variant="skeleton" rows={3} label="등록한 제품을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  if (data.length === 0) {
    return (
      <EmptyState
        title="등록한 제품이 없습니다"
        description="제품을 등록하면 여기에서 판매를 시작할 수 있습니다."
        action={<Link to="/brand/products/new">제품 등록하기</Link>}
      />
    )
  }

  return (
    <>
      {change.isError && <ErrorMessage error={change.error} />}

      <ul className="brand-product-list">
        {data.map((product) => (
          <li key={product.productModelId} className="brand-product-list__item" onClick={(event) => followClick(event, `/products/${product.productModelId}`)}>
            <div className="brand-product-list__main">
              <Link to={`/products/${product.productModelId}`} className="brand-product-list__name">
                {product.name}
              </Link>
              <span className="brand-product-list__meta">
                {PRODUCT_CATEGORY_LABEL[product.category]} · {formatWon(product.price)}
              </span>
              <StockLine product={product} />
              <div className="brand-product-list__links">
                <ProduceLink product={product} />
                <ProductEditLink product={product} />
                <QrLabelLink product={product} />
              </div>
            </div>

            <div className="brand-product-list__side">
              <StatusBadge tone={TONE[product.status]}>
                {PRODUCT_MODEL_STATUS_LABEL[product.status]}
              </StatusBadge>
              <StatusActions
                product={product}
                busyId={change.isPending ? change.variables?.productModelId : undefined}
                onPutOnSale={() =>
                  change.mutate({ productModelId: product.productModelId, action: 'ON_SALE' })
                }
                onDiscontinue={() => setDiscontinuing(product)}
              />
            </div>
          </li>
        ))}
      </ul>

      {/* 단종은 서버가 되돌리지 못한다 (`PRODUCT_MODEL_DISCONTINUED`). 같은 제품을 다시
          팔려면 새로 등록해야 하고, 이미 생산된 개체는 옛 제품에 붙어 있다. */}
      <ConfirmModal
        open={discontinuing !== null}
        title="판매를 중단할까요?"
        message={`${discontinuing?.name ?? ''} 을(를) 단종 처리합니다. 되돌릴 수 없고, 다시 팔려면 제품을 새로 등록해야 합니다.`}
        confirmLabel="판매 중단"
        destructive
        loading={change.isPending}
        onConfirm={() => {
          if (discontinuing === null) return
          change.mutate(
            { productModelId: discontinuing.productModelId, action: 'DISCONTINUE' },
            { onSettled: () => setDiscontinuing(null) },
          )
        }}
        onClose={() => setDiscontinuing(null)}
      />
    </>
  )
}

/**
 * 재고 한 줄.
 *
 * **숫자만 두지 않는다.** 등록만 된 제품의 0 은 "다 팔렸다" 가 아니라 "아직 만들지
 * 않았다" 다. 두 경우에 다른 문장을 낸다.
 */
function StockLine({ product }: { product: ProductModel }) {
  // 상태 배지가 이미 말하는 것을 반복하지 않는다. 단종·품절은 배지에 있고, 여기서 또
  // 적으면 같은 말이 한 줄에 두 번 나온다.
  if (product.status === 'DISCONTINUED') return null

  if (product.stockQuantity > 0) {
    return <span className="brand-product-list__stock">판매 가능 {product.stockQuantity}개</span>
  }

  return (
    <span className="brand-product-list__stock brand-product-list__stock--empty">
      {product.status === 'DRAFT' && '생산 전 · '}판매 가능 수량 0개
    </span>
  )
}

/**
 * 생산으로 가는 링크.
 *
 * **등록만 한 제품은 여기서 막힌다.** 재고가 0 이라 판매를 시작할 수 없는데, 그 0 을
 * 채우는 일(생산 배치)이 다른 화면에 있어 "등록했는데 왜 못 파는가" 로 읽혔다.
 *
 * 이미 재고가 있는 제품에도 둔다 — 다음 배치를 여는 자리가 같기 때문이다. 단종된
 * 제품에는 두지 않는다. 만들어도 팔 수 없다.
 */
function ProduceLink({ product }: { product: ProductModel }) {
  if (product.status === 'DISCONTINUED') return null

  return (
    <Link to="/brand/production" className="brand-product-list__qr">
      {product.stockQuantity > 0 ? '추가 생산' : '생산하기'}
    </Link>
  )
}

/**
 * 제품 수정으로 가는 링크.
 *
 * **오타 하나를 고칠 방법이 없었다.** 가격을 잘못 넣으면 결제가 통째로 실패하는데도 제품을
 * 새로 등록하는 것 말고는 손쓸 수가 없었고, 새로 등록하면 이미 만든 개체가 옛 제품에 붙는다.
 *
 * 단종된 제품에는 두지 않는다 — 서버가 수정을 거절한다(`PRODUCT_MODEL_DISCONTINUED`).
 */
function ProductEditLink({ product }: { product: ProductModel }) {
  if (product.status === 'DISCONTINUED') return null

  return (
    <Link
      to={`/brand/products/${product.productModelId}/edit`}
      className="brand-product-list__qr"
    >
      제품 수정
    </Link>
  )
}

/**
 * 그 제품의 QR 라벨로 가는 링크.
 *
 * **QR 은 개체마다 붙고 개체는 생산 배치가 만든다.** 그래서 여태 라벨에 닿으려면 그 제품이
 * 어느 배치에서 생산됐는지 기억해 제품 생산에서 찾아 들어가야 했다 — 브랜드의 일은 "이 제품
 * 라벨을 뽑는다" 로 시작하는데 순서가 뒤바뀐 것이다.
 *
 * 생산 전 제품에는 두지 않는다. 개체가 없어 라벨도 없고, 눌러 보고 빈 화면을 만나게 하지
 * 않는다 — 그 자리에는 이미 "생산해야 판매 시작 가능" 이 있다.
 *
 * 단종된 제품에는 남긴다. 이미 만들어진 개체의 라벨은 여전히 유효하고, 긁히거나 떨어진
 * 라벨을 다시 인쇄해야 할 수 있다.
 */
function QrLabelLink({ product }: { product: ProductModel }) {
  if (product.status === 'DRAFT' && product.stockQuantity === 0) return null

  return (
    <Link
      to={`/brand/products/${product.productModelId}/qr`}
      className="brand-product-list__qr"
    >
      QR 라벨 보기
    </Link>
  )
}

/**
 * 상태별 버튼.
 *
 * 제품마다 할 수 있는 일이 다르다 — 서버가 허용하는 것만 보여준다. 못 하는 동작의 버튼을
 * 비활성으로 남기지 않고 **왜 못 하는지** 적는 편이 낫다. 비활성 버튼은 이유를 말하지 않는다.
 */
function StatusActions({
  product,
  busyId,
  onPutOnSale,
  onDiscontinue,
}: {
  product: ProductModel
  busyId: number | undefined
  onPutOnSale: () => void
  onDiscontinue: () => void
}) {
  const busy = busyId === product.productModelId

  if (product.status === 'DISCONTINUED') {
    return <span className="brand-product-list__hint">되돌릴 수 없습니다</span>
  }

  // 재고 0 이면 서버가 판매 시작을 거절한다. 생산으로 개체를 만들라고 안내한다.
  if (product.status === 'DRAFT' && product.stockQuantity === 0) {
    return (
      <Link to="/brand/production" className="brand-product-list__hint-link">
        생산해야 판매 시작 가능
      </Link>
    )
  }

  return (
    <div className="brand-product-list__actions">
      {product.status === 'DRAFT' && (
        <Button size="sm" onClick={onPutOnSale} loading={busy}>
          판매 시작
        </Button>
      )}
      {(product.status === 'ON_SALE' || product.status === 'SOLD_OUT') && (
        <Button variant="secondary" size="sm" onClick={onDiscontinue} disabled={busy}>
          판매 중단
        </Button>
      )}
    </div>
  )
}
