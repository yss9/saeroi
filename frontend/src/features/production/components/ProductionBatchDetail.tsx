import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Button,
  ConfirmModal,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useConfirmProductionBatch } from '../hooks/useConfirmProductionBatch.ts'
import { useProductionBatch } from '../hooks/useProductionBatch.ts'
import type { ProductionBatch, ProductionBatchStatus } from '../types/production.types.ts'
import { MaterialInputSelect } from './MaterialInputSelect.tsx'
import { ProductionInputList } from './ProductionInputList.tsx'
import { ProductionItemList } from './ProductionItemList.tsx'
import './production.css'

export interface ProductionBatchDetailProps {
  batchId: number
}

const STATUS_LABEL: Record<ProductionBatchStatus, string> = {
  DRAFT: '작성 중',
  SIGNING: '서명 중',
  CONFIRMED: '확정',
  FAILED: '실패',
  CANCELED: '취소',
}

const TONE: Record<ProductionBatchStatus, StatusTone> = {
  DRAFT: 'warning',
  SIGNING: 'info',
  CONFIRMED: 'success',
  FAILED: 'danger',
  CANCELED: 'neutral',
}

/**
 * 생산 배치 상세 — 투입을 담고 확정하는 화면.
 *
 * 세 조각을 한 화면에 둔다. 투입을 고르고(`MaterialInputSelect`), 담긴 것을 확인하고
 * (`ProductionInputList`), 확정한다. **나누면 사용자가 담은 것을 보지 못한 채 확정한다** —
 * 확정은 되돌릴 수 없어서 그건 위험하다.
 *
 * 확정된 배치는 투입을 바꿀 수 없으므로 선택 UI 를 아예 내리지 않는다.
 */
export function ProductionBatchDetail({ batchId }: ProductionBatchDetailProps) {
  const { data, isPending, isError, error, refetch } = useProductionBatch(batchId)

  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  const editable = data.status === 'DRAFT'

  return (
    <div className="batch-detail">
      <BatchSummary batch={data} />

      {/* 확정된 배치는 소재를 이미 소비했다. 선택 UI 를 남기면 서버가
          PRODUCTION_BATCH_NOT_EDITABLE 로 거절할 요청만 만들게 된다. */}
      {editable && (
        <section className="batch-detail__section">
          <h2>소재 투입</h2>
          <MaterialInputSelect
            batchId={batchId}
            inputMaterialIds={data.inputs.map((input) => input.materialId)}
          />
        </section>
      )}

      <section className="batch-detail__section">
        <h2>담긴 투입</h2>
        <ProductionInputList batch={data} />
      </section>

      {editable && <ConfirmSection batch={data} />}

      {data.status === 'CONFIRMED' && <ConfirmedResult batch={data} />}

      {/* 확정으로 만들어진 개체의 QR. 확정 전에는 조회하지 않는다. */}
      <section className="batch-detail__section">
        <h2>제품 QR</h2>
        <ProductionItemList batchId={batchId} confirmed={data.status === 'CONFIRMED'} />
      </section>
    </div>
  )
}

/**
 * 배치 요약.
 *
 * 확정 전에는 **투입 합계를 배치 필드에서 읽지 않는다.** `totalUsedQuantity` 는 확정
 * 시점에 채워지므로 그 전에는 0 이고, 그대로 보여주면 화면이 거짓을 말한다.
 */
function BatchSummary({ batch }: { batch: ProductionBatch }) {
  const inputTotal = batch.inputs.reduce((total, input) => total + input.inputQuantity, 0)

  return (
    <header className="batch-detail__header">
      <div className="batch-detail__title">
        <h1>{batch.batchName}</h1>
        <StatusBadge tone={TONE[batch.status]}>{STATUS_LABEL[batch.status]}</StatusBadge>
        <span className="batch-detail__code">{batch.batchCode}</span>
      </div>

      {/* 이 배치를 말하는 숫자 셋. 같은 크기로 늘어놓으면 무엇이 결과인지 알 수 없어
          만들 개수와 넣은 양을 크게 세운다. */}
      <dl className="batch-detail__meta">
        <div>
          <dt>생산 개수</dt>
          <dd>{batch.producedQuantity}<small>개</small></dd>
        </div>
        <div>
          <dt>투입 합계</dt>
          <dd>{formatKg(inputTotal)}</dd>
        </div>
        <div>
          <dt>투입 소재</dt>
          <dd>{batch.inputs.length}<small>건</small></dd>
        </div>
      </dl>
    </header>
  )
}

/**
 * 확정.
 *
 * **이 서비스에서 가장 무거운 버튼이다.** 한 번의 요청이 소재를 소비하고, 제품 개체를
 * 만들고, 자투리를 새 소재로 발행하고, 업사이클 비율을 굳힌다. 되돌릴 수 없다.
 *
 * 그래서 두 가지를 한다 — 무엇이 일어나는지 미리 적고, 확인 단계를 둔다.
 *
 * 서버가 거절하는 두 경우를 미리 막는다. 투입이 없으면(`PRODUCTION_NO_INPUT`) 버튼을
 * 내주지 않고, 제품에 쓰인 수량이 전부 0 이면(`PRODUCTION_NO_PRODUCT_MATERIAL`)
 * `ProductionInputList` 가 이미 경고하므로 여기서는 버튼만 잠근다.
 */
function ConfirmSection({ batch }: { batch: ProductionBatch }) {
  const confirm = useConfirmProductionBatch(batch.productionBatchId)
  const [asking, setAsking] = useState(false)

  const productUsed = batch.inputs.reduce((total, input) => total + input.productUsedQuantity, 0)
  const ready =
    batch.inputs.length > 0 && productUsed > 0 && batch.productModelId !== null

  return (
    <section className="batch-detail__section">
      <h2>확정</h2>

      <ul className="batch-detail__effects">
        <li>투입한 소재가 소비되고 보유량에서 빠집니다.</li>
        <li>제품 개체 {batch.producedQuantity}개가 만들어집니다.</li>
        <li>자투리는 새 소재로 발행되어 이력이 이어집니다.</li>
        <li>업사이클 비율이 계산되어 이 생산에 굳습니다.</li>
      </ul>

      {batch.inputs.length === 0 && (
        <p className="batch-detail__note">
          투입한 소재가 없어 확정할 수 없습니다. 위에서 소재를 투입해 주세요.
        </p>
      )}

      {/*
        확정하면 서버가 개체를 만들고 그 개체가 어느 상품인지 여기서 정해진다. 비어 있으면
        서버도 PRODUCTION_PRODUCT_MODEL_REQUIRED 로 거절한다 — 되돌릴 수 없는 버튼이라
        눌러 보고 알게 두지 않는다.
      */}
      {batch.productModelId === null && (
        <p className="batch-detail__note">
          이 생산으로 만들 제품이 정해지지 않아 확정할 수 없습니다.{' '}
          <Link to="/brand/products/new">제품을 등록</Link>할 때 이 생산을 골라 연결해 주세요.
        </p>
      )}

      {confirm.isError && <ErrorMessage error={confirm.error} />}

      <Button onClick={() => setAsking(true)} disabled={!ready} loading={confirm.isPending}>
        생산 확정
      </Button>

      {/* 되돌릴 수 없는 동작이라 확인을 거친다. 문구가 결과를 그대로 말해야 한다 —
          "확정할까요?" 만으로는 소재가 소비된다는 사실이 전달되지 않는다. */}
      <ConfirmModal
        open={asking}
        title="생산을 확정할까요?"
        message={`투입한 ${formatKg(productUsed)}이 소비되고 제품 ${batch.producedQuantity}개가 만들어집니다. 되돌릴 수 없습니다.`}
        confirmLabel="확정"
        destructive
        loading={confirm.isPending}
        onConfirm={() => confirm.mutate(undefined, { onSettled: () => setAsking(false) })}
        onClose={() => setAsking(false)}
      />
    </section>
  )
}

/**
 * 확정 결과.
 *
 * 만들어진 개체와 QR 은 아래 `ProductionItemList` 가 보여준다
 * (`GET /{batchId}/items`).
 *
 * 업사이클 비율은 서버가 확정 시점에 계산해 스냅샷으로 굳힌 값이라 여기서 다시 계산하지
 * 않는다 — 다시 계산하면 소재 데이터가 바뀔 때 이미 팔린 제품의 비율이 소급해 변한다.
 */
function ConfirmedResult({ batch }: { batch: ProductionBatch }) {
  return (
    <section className="batch-detail__section">
      <h2>확정 결과</h2>
      <dl className="batch-detail__result">
        <div>
          <dt>제품에 사용</dt>
          <dd>{formatKg(batch.totalUsedQuantity)}</dd>
        </div>
        <div>
          <dt>업사이클 소재</dt>
          <dd>{formatKg(batch.upcycledUsedQuantity)}</dd>
        </div>
        <div>
          <dt>업사이클 비율</dt>
          <dd>{Number(batch.upcycleRatio).toFixed(2)}%</dd>
        </div>
      </dl>
      <p className="batch-detail__note">
        제품 개체가 만들어졌습니다.{' '}
        <Link to="/brand/products">내 제품에서 판매를 시작</Link>할 수 있습니다.
      </p>
    </section>
  )
}
