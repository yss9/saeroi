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
}

const TONE: Record<ProductItemStatus, StatusTone> = {
  IN_STOCK: 'success',
  RESERVED: 'warning',
  SOLD: 'neutral',
}

/**
 * 한 번에 몇 장을 그릴지.
 *
 * **개체 하나당 이미지 요청 하나다.** 개체 50개짜리 배치를 한 번에 그리면 화면을 열자마자
 * 요청 50개가 나가고, 브랜드가 라벨 앞부분만 확인하려던 경우에도 전부 내려온다. 눈으로
 * 확인하는 데는 한 화면 분량이면 충분하고, 더 필요하면 스스로 늘린다.
 */
const PAGE_SIZE = 12

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
export function ProductionItemQrGrid({ batchId }: ProductionItemQrGridProps) {
  const { data, isPending, isError, error, refetch } = useProductionItems(batchId)
  const [visible, setVisible] = useState(PAGE_SIZE)

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
        description="확정된 배치인데 개체가 없다면 운영자에게 알려 주세요."
      />
    )
  }

  const shown = data.slice(0, visible)
  const rest = data.length - shown.length

  return (
    <section className="qr-grid">
      <p className="qr-grid__caption">
        라벨 {data.length}장 중 {shown.length}장을 미리 보고 있습니다. QR 주소는 누구나 열 수
        있는 공개 경로입니다 — 인쇄 외의 용도로 공유하지 마세요.
      </p>

      <div className="qr-grid__actions">
        {/*
          **화면에 그려진 것만 인쇄된다.** 아직 받지 않은 라벨은 종이에 빈 칸으로 나가므로
          장수를 버튼에 적고, 남은 것이 있으면 그 사실을 옆에 적는다 — 20장짜리 배치에서
          12장만 인쇄된 것을 인쇄물을 세어 보고 알게 하지 않는다.
        */}
        <Button onClick={() => window.print()} disabled={loading}>
          {loading ? '라벨을 불러오는 중' : `라벨 ${shown.length}장 인쇄`}
        </Button>

        {/* 남은 장수를 적는다. "더 보기" 만 있으면 몇 장이 더 있는지 알 수 없다. */}
        {rest > 0 && (
          <Button variant="secondary" onClick={() => setVisible((count) => count + PAGE_SIZE)}>
            {rest}장 더 보기
          </Button>
        )}
      </div>

      {rest > 0 && (
        <p className="qr-grid__hint">
          {rest}장은 아직 불러오지 않았습니다. 더 보기를 누르면 함께 인쇄됩니다.
        </p>
      )}

      <ul className="qr-grid__list">
        {shown.map((item) => (
          <li key={item.productItemId} className="qr-grid__item">
            <QrCard batchId={batchId} item={item} />
          </li>
        ))}
      </ul>
    </section>
  )
}

/**
 * 라벨 한 장.
 *
 * 개체 코드와 상태를 이미지에 붙여 둔다 — 격자에서 이미지만 보면 어느 개체의 라벨인지
 * 알 수 없고, 저장한 뒤 파일 이름으로 되짚어야 한다.
 */
function QrCard({ batchId, item }: { batchId: number; item: ProductionItem }) {
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
        <span className="qr-card__code">{item.itemCode}</span>
        <StatusBadge tone={TONE[item.status]}>
          {PRODUCT_ITEM_STATUS_LABEL[item.status]}
        </StatusBadge>
      </figcaption>

      {/*
        저장은 링크로 한다. 스크립트로 내려받게 하면 팝업 차단·권한에 걸려 조용히 실패하는
        경로가 하나 더 생긴다. 이미지가 없으면 버튼을 두지 않는다 — 빈 파일을 받아 인쇄하는
        것이 최악이다.
      */}
      {qr.url !== null && (
        <a className="ui-button ui-button--secondary ui-button--sm" href={qr.url} download={qrFileName(item)}>
          저장
        </a>
      )}
    </figure>
  )
}
