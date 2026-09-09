import { useState } from 'react'
import {
  Button,
  EmptyState,
  ErrorMessage,
  Input,
  Loading,
  Select,
} from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../../inventory/hooks/useMyMaterials.ts'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import { useAddProductionInputs } from '../hooks/useAddProductionInputs.ts'
import {
  isProductionEligible,
  PRODUCTION_INPUTS_MAX,
  type ProductionBatch,
  type ProductionInputRequest,
} from '../types/production.types.ts'
import './production.css'

export interface MaterialInputSelectProps {
  batchId: number
  /** 이 배치에 이미 들어간 소재. 같은 소재를 두 번 넣을 수 없다 */
  inputMaterialIds?: readonly number[]
  onSaved?: (batch: ProductionBatch) => void
}

/** 화면이 모아 두는 투입 초안. 저장 전까지는 서버에 아무것도 보내지 않는다. */
interface DraftInput {
  material: InventoryItem
  /** gram. 셋의 합이 inputQuantity 다 */
  productUsed: number
  reusableScrap: number
  waste: number
  wasteReason: string
}

/**
 * 생산에 투입할 보유 소재를 고른다.
 *
 * **여러 건을 화면에서 모아 한 번에 저장한다.** 서버가 목록으로 받고 전부 성공하거나 전부
 * 실패하기 때문이다 — 한 건씩 보내면 세 번째에서 잔량이 모자랄 때 앞의 둘이 잠긴 채 남고,
 * 화면이 어디까지 성공했는지 추적해 되돌려야 한다.
 *
 * **`available`(가용량)로 다룬다. `quantity`(보유량)가 아니다.** 보유량에는 다른 판매글이나
 * 가공 요청에 잠긴 몫이 섞여 있어서, 그걸 기준으로 보여주면 사용자가 투입할 수 없는 수량을
 * 입력하고 서버가 거절한다.
 *
 * 고를 수 없는 소재도 목록에 남기고 **왜 못 고르는지 적는다.** 빼버리면 "내 소재가 사라졌다"
 * 로 읽힌다 (MaterialSelect 와 같은 판단).
 */
export function MaterialInputSelect({
  batchId,
  inputMaterialIds = [],
  onSaved,
}: MaterialInputSelectProps) {
  const materials = useMyMaterials()
  const save = useAddProductionInputs(batchId)

  const [drafts, setDrafts] = useState<DraftInput[]>([])
  const [picked, setPicked] = useState<number | null>(null)

  if (materials.isPending) return <Loading label="보유 소재를 불러오는 중" />
  if (materials.isError) {
    return <ErrorMessage error={materials.error} onRetry={() => void materials.refetch()} />
  }

  if (materials.data.length === 0) {
    return (
      <EmptyState
        title="보유한 소재가 없습니다"
        description="소재를 확보하고 가공을 거친 뒤 생산에 투입할 수 있습니다."
      />
    )
  }

  const draftIds = drafts.map((draft) => draft.material.materialId)
  const usable = materials.data.filter(
    (item) =>
      isProductionEligible(item) &&
      item.available > 0 &&
      !draftIds.includes(item.materialId) &&
      !inputMaterialIds.includes(item.materialId),
  )

  const full = drafts.length >= PRODUCTION_INPUTS_MAX

  function addPicked() {
    const material = materials.data?.find((item) => item.materialId === picked)
    if (!material || full) return
    // 투입량 셋을 0 으로 시작한다. 가용량을 미리 채워 넣으면 사용자가 확인 없이 저장한다 —
    // 얼마가 제품이 되고 얼마가 자투리인지는 재단해 본 사람만 안다.
    setDrafts((current) => [
      ...current,
      { material, productUsed: 0, reusableScrap: 0, waste: 0, wasteReason: '' },
    ])
    setPicked(null)
  }

  function update(materialId: number, patch: Partial<DraftInput>) {
    setDrafts((current) =>
      current.map((draft) =>
        draft.material.materialId === materialId ? { ...draft, ...patch } : draft,
      ),
    )
  }

  const problems = drafts.map(problemOf)
  const canSave = drafts.length > 0 && problems.every((problem) => problem === null)

  return (
    <section className="material-input">
      <div className="material-input__picker">
        <Select
          label="투입할 소재"
          placeholder="소재를 선택하세요"
          value={picked === null ? '' : String(picked)}
          options={materials.data.map((item) => ({
            value: String(item.materialId),
            label: optionLabel(item, draftIds, inputMaterialIds),
            disabled: !usable.some((candidate) => candidate.materialId === item.materialId),
          }))}
          onChange={(event) => setPicked(event.target.value ? Number(event.target.value) : null)}
        />
        <Button variant="secondary" onClick={addPicked} disabled={picked === null || full}>
          투입 추가
        </Button>
      </div>

      {/* 고를 수 있는 소재가 하나도 없으면 이유를 알려준다. 목록은 보이는데 전부 선택이
          안 되는 상태가 설명 없이 남으면 고장으로 읽힌다. */}
      {usable.length === 0 && drafts.length === 0 && (
        <p className="material-input__note">
          지금 투입할 수 있는 소재가 없습니다. 폐기물에서 바로 등록한 소재는 가공을 한 번
          거쳐야 하고, 다른 판매글이나 가공 요청에 잠긴 수량은 쓸 수 없습니다.
        </p>
      )}

      {full && (
        <p className="material-input__note">
          한 번에 최대 {PRODUCTION_INPUTS_MAX}건까지 투입할 수 있습니다. 나눠서 저장해 주세요.
        </p>
      )}

      {drafts.map((draft, index) => (
        <DraftRow
          key={draft.material.materialId}
          draft={draft}
          problem={problems[index]}
          onChange={(patch) => update(draft.material.materialId, patch)}
          onRemove={() =>
            setDrafts((current) =>
              current.filter((item) => item.material.materialId !== draft.material.materialId),
            )
          }
        />
      ))}

      {save.isError && <ErrorMessage error={save.error} />}

      {drafts.length > 0 && (
        <div className="material-input__actions">
          {/* 저장은 전부 성공하거나 전부 실패한다. 그래서 한 건이라도 값이 어긋나면
              보내지 않는다 — 서버가 거절하면 아무것도 저장되지 않으므로 헛걸음이다. */}
          <Button
            onClick={() =>
              save.mutate(drafts.map(toRequest), {
                onSuccess: (batch) => {
                  setDrafts([])
                  onSaved?.(batch)
                },
              })
            }
            loading={save.isPending}
            disabled={!canSave}
          >
            투입 {drafts.length}건 저장
          </Button>
          <span className="material-input__total">
            합계 {formatKg(drafts.reduce((sum, draft) => sum + inputQuantityOf(draft), 0))}
          </span>
        </div>
      )}
    </section>
  )
}

/**
 * 투입 한 줄.
 *
 * 수량을 셋으로 나눠 받는다 — 제품·자투리·폐기. 합계를 서버가 행마다 검사하므로
 * (`PRODUCTION_INPUT_QUANTITY_MISMATCH`) 화면도 행마다 본다.
 */
function DraftRow({
  draft,
  problem,
  onChange,
  onRemove,
}: {
  draft: DraftInput
  problem: string | null
  onChange: (patch: Partial<DraftInput>) => void
  onRemove: () => void
}) {
  const total = inputQuantityOf(draft)

  return (
    <div className="material-input__row">
      <div className="material-input__row-head">
        <strong>{draft.material.name}</strong>
        <span className="material-input__available">
          가용 {formatKg(draft.material.available)}
        </span>
        <Button variant="secondary" size="sm" onClick={onRemove}>
          제거
        </Button>
      </div>

      <div className="material-input__quantities">
        <Input
          label="제품 사용 (g)"
          type="number"
          min={0}
          value={draft.productUsed}
          onChange={(event) => onChange({ productUsed: toGram(event.target.value) })}
        />
        <Input
          label="자투리 (g)"
          type="number"
          min={0}
          hint="새 소재로 되살아납니다"
          value={draft.reusableScrap}
          onChange={(event) => onChange({ reusableScrap: toGram(event.target.value) })}
        />
        <Input
          label="폐기 (g)"
          type="number"
          min={0}
          value={draft.waste}
          onChange={(event) => onChange({ waste: toGram(event.target.value) })}
        />
      </div>

      {/* 폐기가 있을 때만 사유를 묻는다. 없는데 칸이 있으면 무엇을 적어야 하는지 헷갈린다. */}
      {draft.waste > 0 && (
        <Input
          label="폐기 사유"
          placeholder="예: 오염으로 사용 불가"
          value={draft.wasteReason}
          onChange={(event) => onChange({ wasteReason: event.target.value })}
        />
      )}

      <p className={`material-input__row-total${problem ? ' material-input__row-total--error' : ''}`}>
        투입 합계 {formatKg(total)}
      </p>

      {problem && (
        <p className="material-input__error" role="alert">
          {problem}
        </p>
      )}
    </div>
  )
}

/** 투입량은 셋의 합이다. 사용자가 따로 입력하지 않는다 — 두 값이 어긋날 자리를 만들지 않는다. */
function inputQuantityOf(draft: DraftInput): number {
  return draft.productUsed + draft.reusableScrap + draft.waste
}

function toRequest(draft: DraftInput): ProductionInputRequest {
  const reason = draft.wasteReason.trim()
  return {
    materialId: draft.material.materialId,
    inputQuantity: inputQuantityOf(draft),
    productUsedQuantity: draft.productUsed,
    reusableScrapQuantity: draft.reusableScrap,
    wasteQuantity: draft.waste,
    // 폐기가 없으면 사유를 보내지 않는다. 폐기 0 에 붙은 사유는 뜻이 없다.
    ...(draft.waste > 0 && reason ? { wasteReason: reason } : {}),
  }
}

/**
 * 이 줄을 그대로 보낼 수 있는가. 보낼 수 없으면 그 이유.
 *
 * 서버도 같은 것을 검사하지만 **저장이 전부-또는-전무**라, 한 줄이 틀리면 나머지도 저장되지
 * 않는다. 그러니 보내기 전에 어느 줄이 왜 틀렸는지 화면이 말해야 한다.
 */
function problemOf(draft: DraftInput): string | null {
  const total = inputQuantityOf(draft)

  if (total <= 0) return '투입량을 입력해 주세요. 제품·자투리·폐기 중 하나는 1g 이상이어야 합니다.'
  if (total > draft.material.available) {
    return `가용 수량(${formatKg(draft.material.available)})을 넘을 수 없습니다.`
  }
  if (draft.waste > 0 && draft.wasteReason.trim().length > 200) {
    return '폐기 사유는 200자를 넘을 수 없습니다.'
  }
  return null
}

/** 빈 입력과 음수를 0 으로 정규화한다. gram 정수만 서버로 나간다. */
function toGram(raw: string): number {
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed < 0) return 0
  return Math.floor(parsed)
}

/**
 * 목록에 보일 문구.
 *
 * 고를 수 없는 소재는 **왜 못 고르는지**를 이름 옆에 적는다. 선택 후에야 알게 하면
 * 사용자가 목록을 여러 번 열어보며 비교해야 한다.
 */
function optionLabel(
  item: InventoryItem,
  draftIds: readonly number[],
  inputMaterialIds: readonly number[],
): string {
  if (inputMaterialIds.includes(item.materialId)) return `${item.name} · 이미 투입됨`
  if (draftIds.includes(item.materialId)) return `${item.name} · 아래에 추가됨`
  if (!isProductionEligible(item)) return `${item.name} · 가공을 거쳐야 투입 가능`
  if (item.available <= 0) return `${item.name} · 가용 수량 없음`
  return `${item.name} · 가용 ${formatKg(item.available)}`
}
