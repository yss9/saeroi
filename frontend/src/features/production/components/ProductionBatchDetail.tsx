import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Button,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useCancelProductionBatch } from '../hooks/useCancelProductionBatch.ts'
import { useProductionFlow } from '../hooks/useProductionFlow.ts'
import { ProductionFlowProgress } from './ProductionFlowProgress.tsx'
import { stepsBefore } from '../services/productionFlow.ts'
import { useProductionBatch } from '../hooks/useProductionBatch.ts'
import type { ProductionBatch, ProductionBatchStatus } from '../types/production.types.ts'
import { MaterialInputSelect } from './MaterialInputSelect.tsx'
import { ProductionChainPanel } from './ProductionChainPanel.tsx'
import { ProductionInputList } from './ProductionInputList.tsx'
import './production.css'

export interface ProductionBatchDetailProps {
  batchId: number
}

const STATUS_LABEL: Record<ProductionBatchStatus, string> = {
  DRAFT: '작성 중',
  SIGNING: '확인 중',
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
      <h1 className="batch-detail__page-title">생산 상세</h1>

      <BatchSummary batch={data} />

      {/* 확정된 생산은 소재를 이미 소비했다. 선택 UI 를 남기면 서버가
          PRODUCTION_BATCH_NOT_EDITABLE 로 거절할 요청만 만들게 된다. */}
      <section className="batch-detail__section">
        <div className="batch-detail__section-head">
          <h2>투입된 소재</h2>
          {editable && <AddInputToggle batch={data} batchId={batchId} />}
        </div>
        <ProductionInputList batch={data} />
      </section>

      {editable && <ConfirmSection batch={data} />}

      {data.status === 'CONFIRMED' && <ConfirmedResult batch={data} />}

      {/* 온체인 기록. 확정 전에는 못 하는 이유를 패널이 직접 적는다 — 투입이 더
          바뀔 수 있어 확정 전에 체인에 남기면 그 내용과 어긋난다. */}
      <section className="batch-detail__section">
        <h2>생산 이력</h2>
        <ProductionChainPanel batch={data} />
      </section>

      {/*
        개체와 QR 은 여기서 보여주지 않는다. 라벨을 뽑는 일은 제품 단위로 일어나고
        (내 제품 → QR 라벨 보기), 같은 것을 생산 상세에도 두면 이 화면이 확정을 마친 뒤에도
        끝나지 않는다 — 확정 결과까지 읽고 생산 목록으로 돌아가는 것이 이 화면의 끝이다.
      */}
    </div>
  )
}

/**
 * 소재를 더 담는 자리.
 *
 * **평소에는 접어 둔다.** 생산을 시작하면서 소재를 이미 담고 온 사람이 대부분인데, 빈
 * 선택 칸이 담긴 목록 위에 펼쳐져 있으면 "안 담겼나" 로 읽힌다.
 *
 * **지우지는 않는다.** 소재 없이 시작한 생산은 여기서만 담을 수 있어, 없애면 그 생산이
 * 막다른 길이 된다. 그래서 담긴 것이 없으면 처음부터 열어 둔다.
 */
function AddInputToggle({ batch, batchId }: { batch: ProductionBatch; batchId: number }) {
  /*
    **사용자가 건드리기 전까지는 지금 담긴 투입을 따른다.** 처음 한 번만 보고 정하면,
    캐시에 있던 빈 배치를 먼저 그린 뒤 투입이 도착해도 선택 칸이 열린 채 남는다 — 방금
    소재를 골라 시작한 사람에게 "또 담으라" 고 말하는 꼴이다.
  */
  const [touched, setTouched] = useState<boolean | null>(null)
  const open = touched ?? batch.inputs.length === 0
  const setOpen = (next: boolean) => setTouched(next)

  return (
    <>
      {/*
        담는 일은 이 화면에서 시작하는 동작이라 채운 버튼으로 둔다 — 표 안의 "취소" 와 같은
        모양이면 줄이 비슷비슷해져 어느 것이 무엇을 하는 버튼인지 눈으로 갈리지 않는다.
      */}
      <Button size="sm" onClick={() => setOpen(!open)}>
        {open ? '닫기' : '+ 소재 더 담기'}
      </Button>

      {open && (
        <div className="batch-detail__add-input">
          <MaterialInputSelect
            batchId={batchId}
            inputMaterialIds={batch.inputs.map((input) => input.materialId)}
            onSaved={() => setOpen(false)}
          />
        </div>
      )}
    </>
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
        {/* 화면 제목은 "생산 상세" 다. 이름은 그 아래 이 생산이 무엇인지 말하는 자리다. */}
        <h2>{batch.batchName}</h2>
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
  /*
    **누르는 것은 한 번이다.** 확정(서버)과 온체인 기록 네 단계를 차례로 진행한다 — 단계마다 지갑
    서명 창은 뜨지만 다섯 번 따로 누르지 않는다. 중간에 멈추면 아래 기록 패널의 "남은 기록 이어서
    진행" 으로 남은 단계부터 잇는다.
  */
  const confirm = useProductionFlow(batch)
  const cancel = useCancelProductionBatch(batch.productionBatchId)

  const productUsed = batch.inputs.reduce((total, input) => total + input.productUsedQuantity, 0)
  const ready = batch.inputs.length > 0 && productUsed > 0
  const locked = batch.inputs.reduce((total, input) => total + input.inputQuantity, 0)

  return (
    /*
      **상자에 담지 않는다.** 확정은 이 화면의 마지막 한 걸음이라 제목을 달아 구획으로
      세울 것이 없다 — 위의 투입 상자를 읽고 내려오면 바로 누르는 자리다.
    */
    <section className="batch-detail__confirm-area">
      {/*
        **확인 단계를 두지 않는다.** 대신 무엇이 일어나는지를 버튼 바로 위에 둔다 — 누르기
        전에 읽는 자리가 여기여야 하고, 모달로 한 번 더 물으면 같은 말을 두 번 하게 된다.
      */}
      {/*
        확정하면 일어나는 일과 그만두면 일어나는 일을 한 상자에 둔다 — 두 버튼이 나란히 서
        있으니 설명도 한 덩어리여야 어느 말이 어느 버튼 것인지 한눈에 맞춰 읽힌다.

        **그만두는 길을 같은 자리에 둔다.** 투입한 소재는 잠겨 있어 다른 배치에도 판매 글에도
        쓸 수 없고, 그 잠금에는 만료가 없다. 확정만 놓아두면 마음이 바뀐 담당자에게 남는 길이
        "원하지 않는 제품을 확정한다" 뿐이다 — 그건 되돌릴 수 없는 쪽으로 미는 것이다.
      */}
      <div className="batch-detail__effects">
        <p>
          투입한 {formatKg(productUsed)}이 소비되고 제품 {batch.producedQuantity}개가
          만들어집니다. <strong>되돌릴 수 없습니다.</strong>
        </p>
        {locked > 0 && (
          <p>취소하면 투입해 둔 {formatKg(locked)}의 잠금이 풀려 다시 쓸 수 있습니다.</p>
        )}
      </div>

      {(confirm.running || confirm.currentStep) && (
        <ProductionFlowProgress doneSteps={stepsBefore(confirm.currentStep)} currentStep={confirm.currentStep} running={confirm.running} />
      )}
      {confirm.error && !confirm.running && <ErrorMessage error={confirm.error} />}
      {cancel.isError && <ErrorMessage error={cancel.error} />}

      {/* 마지막 동작은 오른쪽 끝이다 — 위의 설명을 읽고 내려온 눈이 마치는 자리. */}
      <div className="batch-detail__confirm">
        <Button
          variant="secondary"
          onClick={() => cancel.mutate()}
          loading={cancel.isPending}
          disabled={confirm.running}
        >
          생산 취소
        </Button>
        <Button
          onClick={() => void confirm.run()}
          disabled={!ready || cancel.isPending}
          loading={confirm.running}
        >
          생산 확정
        </Button>
      </div>
    </section>
  )
}

/**
 * 확정 결과.
 *
 * 개체와 QR 은 여기서 보여주지 않는다 — 라벨은 제품 단위로 뽑는 것이라 내 제품의
 * "QR 라벨 보기" 가 맡는다.
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
      {/*
        다음 할 일이 제품에 따라 다르다. 제품을 아직 안 붙인 생산은 개체가 있어도 팔리지
        않는다 — 서버가 제품 모델로 재고를 세기 때문이다.
      */}
      {batch.productModelId === null ? (
        <p className="batch-detail__note">
          생산 제품이 등록되었습니다.{' '}
          <Link to="/brand/products/new">제품 등록</Link>에서 이 생산을 골라 제품을 붙이면
          판매할 수 있습니다.
        </p>
      ) : (
        <p className="batch-detail__note">
          생산 제품이 등록되었습니다.{' '}
          <Link to="/brand/products">내 제품에서 판매를 시작</Link>할 수 있습니다.
        </p>
      )}
    </section>
  )
}
