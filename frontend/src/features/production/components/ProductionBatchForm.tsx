import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input, Loading } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyWallets } from '../../wallet/hooks/useMyWallets.ts'
import { addProductionInputs } from '../api/addProductionInputs.ts'
import { productionKeys } from '../api/productionKeys.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { useOpenProductionBatch } from '../hooks/useOpenProductionBatch.ts'
import { PRODUCTION_INPUTS_MAX, type ProductionBatch } from '../types/production.types.ts'
import {
  canSubmitDrafts,
  toRequest,
  totalDraftQuantity,
  type DraftInput,
} from '../utils/productionInputDraft.ts'
import { MaterialInputPicker } from './MaterialInputSelect.tsx'
import './production.css'

export interface ProductionBatchFormProps {
  onOpened?: (batch: ProductionBatch) => void
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
 * 생산 배치 열기.
 *
 * 배치는 **"이번에 얼마나 만든다"** 는 선언이다. 여기서 소재도, 완성 제품도 고르지 않는다 —
 * 둘 다 배치가 열린 뒤에 정한다. 투입은 여러 소재를 모아 한 번에 잠가야 하고, 무엇을
 * 만들지는 소재를 다 넣고 나서 정해도 늦지 않다 (확정 전까지만 채우면 된다).
 *
 * ⚠️ **활성 지갑이 없으면 열 수 없다** (`PRODUCTION_WALLET_NOT_LINKED`). 서명자 주소를
 * 서버가 등록된 지갑에서 가져와 배치에 굳히기 때문이다. 제출해 보고 알게 하지 않고 미리
 * 막는다 — 지갑 등록은 다른 화면에서 해야 하는 일이라 되돌아오는 길이 길다.
 */
export function ProductionBatchForm({ onOpened, initial }: ProductionBatchFormProps) {
  const {
    register,
    handleSubmit,
    reset,
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
  const [inputError, setInputError] = useState<unknown>(null)
  // 투입할 소재를 고르는 칸이 펴져 있는가. "투입 추가" 가 카드 제목 줄에 있어 여기서 쥔다.
  const [choosing, setChoosing] = useState(false)

  if (wallets.isPending) {
    return <Loading label="지갑 정보를 불러오는 중" />
  }

  // 지갑 조회가 실패하면 막지 않는다 — 서버가 최종 판단을 한다. 여기서 차단하면
  // 조회 실패만으로 생산 자체를 못 하게 된다.
  const hasWallet = wallets.isError || wallets.activeWallet !== null

  /**
   * 배치를 열고, 고른 소재를 이어서 담는다.
   *
   * **서버는 두 번 부른다.** 투입은 배치에 속하는 것이라 배치 번호가 있어야 보낼 수 있다
   * (`POST /{batchId}/inputs`). 화면에서만 한 번이다.
   *
   * 투입 저장이 실패해도 **배치는 이미 열려 있다.** 그 사실을 감추지 않는다 — 되돌리려고
   * 배치를 지우면 사용자가 적은 이름과 개수까지 사라진다. 대신 이유를 적고 상세로 보낸다.
   * 거기서 같은 소재를 다시 담으면 된다.
   */
  const onSubmit = handleSubmit((values) => {
    if (!hasWallet) return
    setInputError(null)
    open.mutate(
      {
        batchName: values.batchName.trim(),
        producedQuantity: values.producedQuantity,
      },
      {
        onSuccess: async (batch) => {
          if (drafts.length === 0) {
            reset()
            onOpened?.(batch)
            return
          }

          try {
            const withInputs = await addProductionInputs(
              batch.productionBatchId,
              drafts.map(toRequest),
            )
            /*
              **투입까지 담긴 배치를 상세 캐시에 심는다.** 배치 열기가 방금 빈 배치를
              심어 두었기 때문에(useOpenProductionBatch), 그대로 두면 상세 화면이 그 빈
              값을 먼저 그린다 — 고른 소재가 사라진 것처럼 보이고 "소재를 담으라" 는
              안내가 뜬다.

              보유 소재도 함께 무효화한다. 투입한 만큼 가용량이 잠긴다.
            */
            queryClient.setQueryData(
              productionKeys.detail(batch.productionBatchId),
              withInputs,
            )
            void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
            void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            void queryClient.invalidateQueries({ queryKey: materialKeys.all })
            reset()
            setDrafts([])
            onOpened?.(withInputs)
          } catch (error) {
            setInputError(error)
            onOpened?.(batch)
          }
        },
      },
    )
  })

  const draftTotal = totalDraftQuantity(drafts)

  /*
    **생산 상세와 같은 틀로 그린다.** 머리 카드 → 투입 소재 카드 → 오른쪽 끝 버튼. 예전에는
    가운데 흰 카드 한 장에 칸을 쌓은 입력 폼이라, 시작을 누르고 상세로 넘어가면 같은 생산이
    전혀 다른 화면으로 바뀌어 보였다.

    머리에는 적는 것 둘(이름 · 개수)만 한 줄에 둔다. 투입 합계와 건수는 적는 값이 아니라
    담은 소재에서 계산되는 값이라, 소재 카드의 맨 아래 합계 줄에서 보여 준다.
  */
  return (
    <form onSubmit={onSubmit} noValidate className="batch-detail batch-new">
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

      {/* 무엇으로 만들지도 여기서 정한다. 배치를 연 뒤 다른 화면에서 담게 하면 이름과
          개수만 적어 둔 빈 배치가 쌓인다. */}
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
              disabled={drafts.length >= PRODUCTION_INPUTS_MAX}
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
            것과 같다. 버튼 옆에 따로 떼어 두면 어느 목록의 합인지 눈으로 이어지지 않았다.
            무게가 이 줄의 주인이고, 건수는 그 옆에 조용히 붙는다.
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

      {/* 배치는 열렸는데 투입만 실패한 경우다. 배치가 사라진 것이 아니다. */}
      {inputError !== null && <ErrorMessage error={inputError} />}

      {/* 지갑이 없으면 서버가 거절한다. 등록은 다른 화면이라 미리 알려주고 데려간다. */}
      {!hasWallet && (
        <p className="batch-form__note batch-form__note--warning">
          생산에는 등록된 지갑이 필요합니다. 회사 정보에서 지갑을 먼저 연결해 주세요.
        </p>
      )}

      {open.isError && <ErrorMessage error={open.error} />}

      {/* 상세의 "생산 확정" 과 같은 자리 — 위를 읽고 내려온 눈이 마치는 오른쪽 끝이다. */}
      <section className="batch-detail__confirm-area">
        <div className="batch-detail__confirm">
          {/* 소재를 하나도 고르지 않아도 열 수 있다 — 나중에 담는 것도 정상 경로다.
              다만 한 건이라도 값이 어긋나면 막는다. 서버가 통째로 거절하기 때문이다. */}
          <Button
            type="submit"
            loading={open.isPending}
            disabled={!hasWallet || (drafts.length > 0 && !canSubmitDrafts(drafts))}
          >
            생산 시작
          </Button>
        </div>
      </section>
    </form>
  )
}
