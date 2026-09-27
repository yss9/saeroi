import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { Button, ErrorMessage, Input, Loading } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyWallets } from '../../wallet/hooks/useMyWallets.ts'
import { addProductionInputs } from '../api/addProductionInputs.ts'
import { productionKeys } from '../api/productionKeys.ts'
import { useOpenProductionBatch } from '../hooks/useOpenProductionBatch.ts'
import { invalidateAfterProductionFlow, productionFlowErrorMessage } from '../hooks/useProductionFlow.ts'
import { runProductionFlow, stepsBefore, type ProductionFlowStep } from '../services/productionFlow.ts'
import { PRODUCTION_INPUTS_MAX, type ProductionBatch } from '../types/production.types.ts'
import {
  canSubmitDrafts,
  toRequest,
  totalDraftQuantity,
  type DraftInput,
} from '../utils/productionInputDraft.ts'
import { MaterialInputPicker } from './MaterialInputSelect.tsx'
import { ProductionFlowProgress } from './ProductionFlowProgress.tsx'
import './production.css'

export interface ProductionBatchFormProps {
  /**
   * 이 화면이 할 일을 마쳤다. 끝까지 갔으면 `failure` 가 null, 배치를 연 뒤 멈췄으면 그 이유다.
   *
   * 멈춘 경우에도 배치는 이미 있다 — 여기서 다시 누르면 배치가 하나 더 열리므로, 받는 쪽이 상세로
   * 보내 거기서 남은 단계를 잇게 한다.
   */
  onDone?: (batch: ProductionBatch, failure: string | null) => void
  /**
   * 지난 생산을 베껴 온 값. 같은 제품을 또 만들 때 이름·개수·투입이 대개 같아, 처음부터
   * 다시 적게 하지 않는다.
   *
   * 값이 준비된 뒤에만 그린다 — 나중에 채우면 사용자가 이미 고친 칸을 덮어쓴다.
   */
  initial?: {
    batchName: string
    producedQuantity: number
    drafts: DraftInput[]
  }
}

interface BatchFormValues {
  batchName: string
  producedQuantity: number
}

/**
 * 생산 — 이름·개수·소재를 적고 **한 번 눌러** 확정과 체인 기록까지 마친다.
 *
 * 예전에는 여기서 배치만 열고 상세 화면에서 같은 내용을 다시 읽은 뒤 확정했다. 두 화면에 걸칠
 * 이유가 없다 — 확정 전에 읽어야 할 것(무엇이 소비되고 몇 개가 만들어지는지)은 이 화면에 다 있다.
 *
 * ⚠️ **활성 지갑이 없으면 열 수 없다** (`PRODUCTION_WALLET_NOT_LINKED`). 서명자 주소를
 * 서버가 등록된 지갑에서 가져와 배치에 굳히기 때문이다. 제출해 보고 알게 하지 않고 미리
 * 막는다 — 지갑 등록은 다른 화면에서 해야 하는 일이라 되돌아오는 길이 길다.
 */
export function ProductionBatchForm({ onDone, initial }: ProductionBatchFormProps) {
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<BatchFormValues>({
    defaultValues: {
      batchName: initial?.batchName ?? '',
      ...(initial ? { producedQuantity: initial.producedQuantity } : {}),
    },
  })

  const wallets = useMyWallets()
  const open = useOpenProductionBatch()
  const queryClient = useQueryClient()
  // 배치가 아직 없어 투입은 화면이 모아 둔다. 서버로는 배치를 만든 뒤에 보낸다.
  const [drafts, setDrafts] = useState<DraftInput[]>(initial?.drafts ?? [])
  // 투입할 소재를 고르는 칸이 펴져 있는가. "투입 추가" 가 소재 카드 밖에 있어 여기서 쥔다.
  const [choosing, setChoosing] = useState(false)
  const [running, setRunning] = useState(false)
  const [currentStep, setCurrentStep] = useState<ProductionFlowStep | null>(null)
  // 두 번 눌러 배치가 둘 열리지 않게 한다. 상태는 다음 그리기까지 늦다.
  const inFlight = useRef(false)
  // 확정 전에 읽는 문장이 지금 적힌 개수를 따라가야 한다.
  const producedQuantity = useWatch({ control, name: 'producedQuantity' })

  if (wallets.isPending) {
    return <Loading label="지갑 정보를 불러오는 중" />
  }

  // 지갑 조회가 실패하면 막지 않는다 — 서버가 최종 판단을 한다. 여기서 차단하면
  // 조회 실패만으로 생산 자체를 못 하게 된다.
  const hasWallet = wallets.isError || wallets.activeWallet !== null

  const productUsed = drafts.reduce((total, draft) => total + draft.productUsed, 0)
  const draftTotal = totalDraftQuantity(drafts)

  /**
   * 배치를 열고, 고른 소재를 담고, 확정과 체인 기록 네 단계를 차례로 진행한다.
   *
   * **서버는 여러 번 부르지만 누르는 것은 한 번이다.** 투입은 배치 번호가 있어야 보낼 수 있고
   * (`POST /{batchId}/inputs`), 확정·기록은 투입이 담긴 배치여야 한다.
   *
   * 배치를 연 뒤 어디서 멈추든 **배치는 이미 있다.** 되돌리려고 지우면 적은 이름과 개수까지
   * 사라지고, 이 화면에서 다시 누르게 하면 배치가 하나 더 열린다. 그래서 이유를 들고 상세로
   * 넘긴다 — 거기서 소재를 다시 담거나 남은 단계부터 잇는다.
   */
  const start = async (values: BatchFormValues) => {
    if (!hasWallet || !canSubmitDrafts(drafts) || inFlight.current) return
    inFlight.current = true
    setRunning(true)
    setCurrentStep(null)

    let batch: ProductionBatch
    try {
      batch = await open.mutateAsync({
        batchName: values.batchName.trim(),
        producedQuantity: values.producedQuantity,
      })
    } catch {
      // 배치가 열리지 않았다. 아래 오류 문구가 이유를 말하고, 여기서 다시 누르면 된다.
      inFlight.current = false
      setRunning(false)
      return
    }

    let failure: string | null = null
    try {
      batch = await addProductionInputs(batch.productionBatchId, drafts.map(toRequest))
      /*
        **투입까지 담긴 배치를 상세 캐시에 심는다.** 배치 열기가 방금 빈 배치를 심어 두었기
        때문에(useOpenProductionBatch), 멈춰서 상세로 넘어가면 그 빈 값을 먼저 그린다 — 고른
        소재가 사라진 것처럼 보인다.
      */
      queryClient.setQueryData(productionKeys.detail(batch.productionBatchId), batch)
      await runProductionFlow(batch, setCurrentStep)
    } catch (cause) {
      failure = productionFlowErrorMessage(cause)
    } finally {
      invalidateAfterProductionFlow(queryClient, batch.productionBatchId)
    }

    inFlight.current = false
    setRunning(false)
    onDone?.(batch, failure)
  }

  /*
    **생산 상세와 같은 틀로 그린다.** 머리 카드 → 투입 소재 카드 → 오른쪽 끝 버튼. 예전에는
    가운데 흰 카드 한 장에 칸을 쌓은 입력 폼이라, 상세로 넘어가면 같은 생산이 전혀 다른 화면으로
    바뀌어 보였다.

    머리에는 적는 것 둘(이름 · 개수)만 한 줄에 둔다. 투입 합계와 건수는 적는 값이 아니라
    담은 소재에서 계산되는 값이라, 소재 카드의 맨 아래 합계 줄에서 보여 준다.
  */
  return (
    <form onSubmit={(event) => void handleSubmit(start)(event)} noValidate className="batch-detail batch-new">
      <header className="batch-detail__header">
        <div className="batch-new__fields">
          <div className="batch-new__name">
            <Input
              label="생산 이름"
              placeholder="예: 2026 가을 토트백 1차"
              error={errors.batchName?.message}
              {...register('batchName', {
                required: '생산 이름을 입력해 주세요.',
                maxLength: { value: 200, message: '생산 이름은 200자를 넘을 수 없습니다.' },
              })}
            />
          </div>
          <div className="batch-new__quantity">
            <Input
              label="생산 개수"
              type="number"
              min={1}
              placeholder="0"
              error={errors.producedQuantity?.message}
              {...register('producedQuantity', {
                required: '생산 개수를 입력해 주세요.',
                valueAsNumber: true,
                min: { value: 1, message: '1개 이상 입력해 주세요.' },
                validate: (value) =>
                  Number.isInteger(value) ? true : '생산 개수는 정수로 입력해 주세요.',
              })}
            />
            <small aria-hidden="true">개</small>
          </div>
        </div>
      </header>

      {/* 무엇으로 만들지도 여기서 정한다. 확정은 투입 없이 되지 않는다(PRODUCTION_NO_INPUT). */}
      <div className="batch-new__inputs">
        {/*
          "투입 추가" 는 카드 밖, 카드 바로 위 오른쪽 끝에 둔다 — 카드 안은 담은 소재의 자리라,
          버튼이 그 안에 있으면 담긴 줄의 일부처럼 읽힌다. 칸이 하나라도 열린 뒤에만 선다.
        */}
        {drafts.length > 0 && !choosing && (
          <div className="batch-new__more">
            <Button
              variant="secondary"
              size="sm"
              disabled={running || drafts.length >= PRODUCTION_INPUTS_MAX}
              onClick={() => setChoosing(true)}
            >
              + 투입 추가
            </Button>
          </div>
        )}
        <section className="batch-detail__section">
          {/* 큰 제목은 두지 않는다 — 선택 칸의 이름("투입할 소재")이 같은 말을 한다.
              구획 이름은 보조 기술에만 남긴다. */}
          <h2 className="ui-visually-hidden">투입할 소재</h2>
          <MaterialInputPicker
            drafts={drafts}
            onChange={setDrafts}
            choosing={choosing}
            onChoosingChange={setChoosing}
          />

          {/*
            **담은 것의 합계는 담은 칸의 맨 아래에 선다** — 상세의 투입 표가 "합계" 줄로 끝나는
            것과 같다. 무게가 이 줄의 주인이고, 건수는 그 옆에 조용히 붙는다.
          */}
          {drafts.length > 0 && (
            <div className="batch-new__sum" aria-live="polite">
              <span className="batch-new__sum-label">투입 합계</span>
              <span className="batch-new__sum-value">
                <strong>{formatKg(draftTotal)}</strong>
                <small>소재 {drafts.length}건</small>
              </span>
            </div>
          )}
        </section>
      </div>

      {/* 지갑이 없으면 서버가 거절한다. 등록은 다른 화면이라 미리 알려주고 데려간다. */}
      {!hasWallet && (
        <p className="batch-form__note batch-form__note--warning">
          생산에는 등록된 지갑이 필요합니다. 회사 정보에서 지갑을 먼저 연결해 주세요.
        </p>
      )}

      {open.isError && <ErrorMessage error={open.error} />}

      {/* 상세의 "생산 확정" 과 같은 자리 — 위를 읽고 내려온 눈이 마치는 오른쪽 끝이다. */}
      <section className="batch-detail__confirm-area">
        {/*
          **확인 단계를 두지 않는다.** 대신 무엇이 일어나는지를 버튼 바로 위에 둔다 — 누르기 전에
          읽는 자리가 여기다.
        */}
        {drafts.length > 0 && Number.isInteger(producedQuantity) && producedQuantity > 0 && (
          <p className="batch-detail__effects">
            투입한 {formatKg(productUsed)}이 소비되고 제품 {producedQuantity}개가
            만들어집니다. <strong>되돌릴 수 없습니다.</strong>
          </p>
        )}

        {(running || currentStep) && (
          <ProductionFlowProgress doneSteps={stepsBefore(currentStep)} currentStep={currentStep} running={running} />
        )}

        <div className="batch-detail__confirm">
          {/* 소재 없이는 확정할 수 없다(PRODUCTION_NO_INPUT). 한 건이라도 값이 어긋나도 막는다 —
              서버가 투입을 통째로 거절한다. */}
          <Button
            type="submit"
            loading={running}
            disabled={!hasWallet || !canSubmitDrafts(drafts)}
          >
            생산 확정
          </Button>
        </div>
      </section>
    </form>
  )
}
