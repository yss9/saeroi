import { useState } from 'react'
import { Button, EmptyState, ErrorMessage, Loading, StatusBadge, type StatusTone } from '../../../components/common/index.ts'
import { useProductionItems } from '../hooks/useProductionItems.ts'
import { ProductionItemQr } from './ProductionItemQr.tsx'
import {
  PRODUCT_ITEM_STATUS_LABEL,
  type ProductionItem,
  type ProductItemStatus,
} from '../types/production.types.ts'
import './production.css'

export interface ProductionItemListProps {
  batchId: number
  /** 확정 전에는 개체가 없다. 부르지 않기 위해 받는다 */
  confirmed: boolean
}

const TONE: Record<ProductItemStatus, StatusTone> = {
  IN_STOCK: 'success',
  RESERVED: 'warning',
  SOLD: 'neutral',
}

/**
 * 확정으로 만들어진 제품 개체와 QR.
 *
 * **브랜드가 제품에 인쇄할 QR 주소를 얻는 화면이다.** 서버가 배치 소유자에게만 개체를
 * 주므로(남의 배치는 403) 이 화면은 생산자 본인만 본다.
 *
 * ⚠️ **`qrToken` 을 화면에 늘어놓지 않는다.** 그 값 하나가 비회원 공개 경로
 * (`/qr/{token}`)의 열쇠다 — 화면 캡처나 화면 공유로 새면 아무나 그 제품의 이력을 열 수
 * 있다. 브랜드 전용 화면이라도 눈에 보이는 형태로 두지 않고 **복사와 열기 동작으로만**
 * 다룬다. 확인용으로 앞 6자만 보여준다.
 *
 * **QR 이미지는 서버가 그린다.** 프론트에 생성 라이브러리를 들이지 않는다 — 브랜드는 배치
 * 하나에 라벨 수십 장을 한 번에 인쇄하고, 인쇄물은 되돌릴 수 없어 **모든 라벨이 같은 방식
 * (같은 오류정정 수준·여백)으로 만들어졌다는 보장**이 중요하다. 브라우저마다 다른 결과가
 * 나오면 그 차이는 스캔해 봐야 드러난다.
 */
export function ProductionItemList({ batchId, confirmed }: ProductionItemListProps) {
  const { data, isPending, isError, error, refetch } = useProductionItems(batchId, confirmed)

  // 확정 전에는 만들어진 개체가 없다. 조회하지 않고 이유를 적는다.
  if (!confirmed) {
    return (
      <p className="item-list__note">생산을 확정하면 QR 이 만들어집니다.</p>
    )
  }

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

  return (
    <section className="item-list">
      <table className="item-list__table">
        <caption className="ui-visually-hidden">제품 개체 {data.length}개의 QR 목록</caption>
        <thead>
          <tr>
            <th scope="col">번호</th>
            <th scope="col">개체 코드</th>
            <th scope="col">상태</th>
            <th scope="col">QR 주소</th>
          </tr>
        </thead>
        <tbody>
          {data.map((item) => (
            <tr key={item.productItemId}>
              <th scope="row">{item.serialNumber}</th>
              <td className="item-list__code">{item.itemCode}</td>
              <td>
                <StatusBadge tone={TONE[item.status]}>
                  {PRODUCT_ITEM_STATUS_LABEL[item.status]}
                </StatusBadge>
              </td>
              <td>
                <QrActions batchId={batchId} item={item} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

/**
 * QR 주소 복사·열기.
 *
 * 절대 주소로 복사한다 — 라벨을 만드는 사람이 도메인을 손으로 붙이게 하면 오타가 그대로
 * 인쇄되고, 인쇄된 QR 은 고칠 수 없다.
 */
function QrActions({ batchId, item }: { batchId: number; item: ProductionItem }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    // 클립보드는 권한·보안 컨텍스트에 따라 실패한다(http, 권한 거부). 실패를 삼키면
    // 사용자는 복사된 줄 알고 빈 값을 붙여 넣는다.
    try {
      await navigator.clipboard.writeText(item.qrUrl)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="item-list__qr">
      {/* 확인용으로 앞부분만. 전체를 보여주면 화면 캡처 한 장으로 다 새어 나간다. */}
      <code className="item-list__token" title="복사 버튼으로만 전체 주소를 얻으세요">
        {item.qrToken.slice(0, 6)}…
      </code>
      <ProductionItemQr batchId={batchId} item={item} />
      <Button variant="secondary" size="sm" onClick={() => void copy()}>
        {copied ? '복사됨' : '주소 복사'}
      </Button>
      {/*
        새 탭으로 연다 — 브랜드가 라벨을 만들다가 이 화면을 잃으면 다시 찾아와야 한다.
        주소는 서버가 준 것을 그대로 쓴다. 토큰에 접두어를 붙여 만들면 그 규칙이 화면과 서버
        두 곳이 되고, 서버 설정이 바뀔 때 한쪽만 바뀐다 — 그러면 여기서 열어 확인한 주소와
        실제로 인쇄된 QR 이 다른 곳을 가리킨다.
      */}
      <a className="item-list__open" href={item.qrUrl} target="_blank" rel="noreferrer">
        열어보기
      </a>
    </div>
  )
}
