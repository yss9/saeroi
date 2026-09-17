import { useState } from 'react'
import { useIsFetching } from '@tanstack/react-query'
import {
  Button,
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { productionItemKeys } from '../api/productionKeys.ts'
import { useProductionItemQr } from '../hooks/useProductionItemQr.ts'
import { useProductionItems } from '../hooks/useProductionItems.ts'
import {
  PRODUCT_ITEM_STATUS_LABEL,
  type ProductionItem,
  type ProductItemStatus,
} from '../types/production.types.ts'
import { qrFileName } from '../utils/qrLabel.ts'
import './production.css'

export interface ProductionItemQrGridProps {
  batchId: number
  /**
   * 라벨에 적을 제품 이름.
   *
   * **개체 코드만으로는 어느 제품의 라벨인지 모른다.** 종이에서 떨어져 나온 라벨 한 장은
   * 코드 문자열뿐이라, 붙일 옷을 찾으려면 화면으로 돌아와 조회해야 한다. 제품 이름은
   * `product` 기능이 알고 개체는 `production` 이 아는 값이라 화면이 건네준다
   * (ARCHITECTURE §12.1).
   */
  productName?: string
}

const TONE: Record<ProductItemStatus, StatusTone> = {
  IN_STOCK: 'success',
  RESERVED: 'warning',
  SOLD: 'neutral',
}

/**
 * 한 쪽에 몇 장을 그릴지.
 *
 * **개체 하나당 이미지 요청 하나다.** 개체 50개짜리 생산을 한 번에 그리면 화면을 열자마자
 * 요청 50개가 나간다. 쪽을 나누면 보고 있는 쪽만 받는다.
 *
 * 10장은 인쇄 한 장에 담기는 양이기도 하다 — 쪽과 종이가 어긋나면 "몇 장이 인쇄됐는지"
 * 를 세어 봐야 안다.
 */
const PAGE_SIZE = 10

/**
 * 배치가 만들어낸 개체의 QR 미리보기 격자.
 *
 * **개체 목록의 표와 다른 화면이다.** 표는 "어떤 개체가 있는가" 를 보고, 이 격자는
 * **"인쇄될 라벨이 어떻게 생겼는가"** 를 본다 — 그래서 이미지를 눌러 열지 않고 처음부터
 * 늘어놓는다. 라벨 한 장이 빠졌거나 깨진 것은 나란히 놓고 봐야 눈에 들어온다.
 *
 * 이미지는 서버가 그린다. 프론트에 생성 라이브러리를 들이지 않는 이유는 인쇄물이 되돌릴 수
 * 없어서 **모든 라벨이 같은 방식으로 만들어졌다는 보장**이 필요하기 때문이다.
 */
export function ProductionItemQrGrid({ batchId, productName }: ProductionItemQrGridProps) {
  const { data, isPending, isError, error, refetch } = useProductionItems(batchId)
  const [page, setPage] = useState(0)

  // **아직 오는 중인 라벨이 있으면 인쇄를 막는다.** 그 자리는 종이에 빈 칸으로 나가고,
  // 빈 라벨이 붙은 제품은 스캔되지 않는다. 카드마다 상태를 위로 올리는 대신 캐시에
  // 물어본다 — 같은 접두사를 쓰므로 이 배치의 이미지 요청이 그대로 세어진다.
  const loading = useIsFetching({ queryKey: productionItemKeys.all(batchId) }) > 0

  if (isPending) return <Loading variant="skeleton" rows={3} label="제품 개체를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  if (data.length === 0) {
    return (
      <EmptyState
        title="만들어진 제품 개체가 없습니다"
        description="확정된 생산인데 개체가 없다면 운영자에게 알려 주세요."
      />
    )
  }

  const pageCount = Math.max(1, Math.ceil(data.length / PAGE_SIZE))
  // 쪽이 줄어드는 경우(개체가 사라지는 일은 없지만 캐시가 갈릴 수 있다)를 위해 묶는다.
  const current = Math.min(page, pageCount - 1)
  const shown = data.slice(current * PAGE_SIZE, current * PAGE_SIZE + PAGE_SIZE)

  return (
    <section className="qr-grid">
      {/*
        경고와 인쇄를 오른쪽 끝에 붙여 둔다. 인쇄를 누르기 직전에 눈이 가는 자리가 거기라,
        "누구나 열 수 있는 주소" 라는 사실도 같은 자리에 있어야 읽힌다.

        **화면에 그려진 것만 인쇄된다.** 그래서 장수를 버튼에 적는다 — 20장짜리 생산에서
        10장만 인쇄된 것을 인쇄물을 세어 보고 알게 하지 않는다.
      */}
      <div className="qr-grid__bar">
        <p className="qr-grid__caption">인쇄 외의 용도로 공유하지 마세요.</p>

        <Button onClick={() => window.print()} disabled={loading}>
          {loading ? '라벨을 불러오는 중' : `${shown.length}장 인쇄`}
        </Button>
      </div>

      <ul className="qr-grid__list">
        {shown.map((item) => (
          <li key={item.productItemId} className="qr-grid__item">
            <QrCard batchId={batchId} item={item} productName={productName} />
          </li>
        ))}
      </ul>

      {/* 쪽 번호. 인쇄는 쪽 단위라 지금 어느 쪽인지가 곧 무엇이 인쇄되는지다. */}
      {pageCount > 1 && (
        <nav className="qr-grid__pages" aria-label="라벨 쪽">
          {Array.from({ length: pageCount }, (_, index) => (
            <button
              key={index}
              type="button"
              aria-current={index === current ? 'page' : undefined}
              className={index === current ? 'is-active' : undefined}
              onClick={() => setPage(index)}
            >
              {index + 1}
            </button>
          ))}
        </nav>
      )}
    </section>
  )
}

/**
 * 라벨 한 장.
 *
 * 개체 코드와 상태를 이미지에 붙여 둔다 — 격자에서 이미지만 보면 어느 개체의 라벨인지
 * 알 수 없고, 저장한 뒤 파일 이름으로 되짚어야 한다.
 */
function QrCard({
  batchId,
  item,
  productName,
}: {
  batchId: number
  item: ProductionItem
  productName?: string
}) {
  // 격자에 그려진 순간 받는다. 이 화면의 목적 자체가 이미지를 보는 것이라 지연시킬 이유가
  // 없다 — 보이는 장수를 제한하는 것으로 요청 수를 줄인다.
  const qr = useProductionItemQr(batchId, item.productItemId, true)

  return (
    <figure className="qr-card">
      <div className="qr-card__frame">
        {qr.isPending && <Loading label={`${item.itemCode} QR 을 만드는 중`} />}

        {qr.isError && (
          <div className="qr-card__error" role="alert">
            <p className="qr-card__message">{qr.errorMessage}</p>
            <Button variant="secondary" size="sm" onClick={qr.retry}>
              다시 시도
            </Button>
          </div>
        )}

        {qr.url !== null && (
          <img
            className="qr-card__image"
            src={qr.url}
            alt={`${item.itemCode} 개체의 QR 코드`}
            width={160}
            height={160}
          />
        )}
      </div>

      <figcaption className="qr-card__caption">
        {/* 제품 이름이 먼저다. 라벨을 손에 들었을 때 알아야 하는 것은 코드가 아니라 옷이다. */}
        {productName !== undefined && <strong className="qr-card__product">{productName}</strong>}
        <span className="qr-card__code">{item.itemCode}</span>
      </figcaption>

      {/*
        상태와 저장은 한 줄이다. 둘 다 화면에서만 쓰는 것이라(인쇄에서는 함께 빠진다) 같은
        줄에 묶어 두면 라벨 그림과 조작이 섞이지 않는다.

        저장은 링크로 한다. 스크립트로 내려받게 하면 팝업 차단·권한에 걸려 조용히 실패하는
        경로가 하나 더 생긴다. 이미지가 없으면 버튼을 두지 않는다 — 빈 파일을 받아 인쇄하는
        것이 최악이다.
      */}
      <div className="qr-card__row">
        <StatusBadge tone={TONE[item.status]}>
          {PRODUCT_ITEM_STATUS_LABEL[item.status]}
        </StatusBadge>

        {qr.url !== null && (
          <a
            className="ui-button ui-button--secondary ui-button--sm"
            href={qr.url}
            download={qrFileName(item)}
          >
            저장
          </a>
        )}
      </div>
    </figure>
  )
}
