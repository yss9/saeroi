import { MaterialProcessingBadge } from '../../material/components/MaterialProcessingBadge.tsx'
import { processingHistoryText } from '../../material/utils/processingHistory.ts'
import type { MaterialGenerationType } from '../../material/types/material.types.ts'
import { useState, type ReactNode } from 'react'
import {
  Button,
  EmptyState,
  ErrorMessage,
  Input,
  Loading,
  Select,
  type SelectOption,
} from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../../inventory/hooks/useMyMaterials.ts'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import { useAddProductionInputs } from '../hooks/useAddProductionInputs.ts'
import {
  isProductionEligible,
  PRODUCTION_INPUTS_MAX,
  type ProductionBatch,
} from '../types/production.types.ts'
import {
  canSubmitDrafts,
  problemOf,
  toRequest,
  totalDraftQuantity,
  type DraftInput,
} from '../utils/productionInputDraft.ts'
import './production.css'

export interface MaterialInputSelectProps {
  batchId: number
  /** 이 배치에 이미 들어간 소재. 같은 소재를 두 번 넣을 수 없다 */
  inputMaterialIds?: readonly number[]
  onSaved?: (batch: ProductionBatch) => void
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
  const save = useAddProductionInputs(batchId)
  const [drafts, setDrafts] = useState<DraftInput[]>([])

  return (
    <>
      {/*
        저장 줄은 마지막 투입 칸 **안**에 들어간다 — 따로 상자를 세우면 담는 일과 보내는
        일이 다른 화면처럼 갈린다. 자리는 picker 가 정하고 여기서는 무엇을 놓을지만 준다.
      */}
      <MaterialInputPicker
        drafts={drafts}
        onChange={setDrafts}
        inputMaterialIds={inputMaterialIds}
        actions={
          <div className="material-input__actions">
          {/* 합계가 버튼 왼쪽이다 — 무엇을 보내는지 읽고 그 손끝에서 누른다. */}
          <span className="material-input__total">
            합계 {formatKg(totalDraftQuantity(drafts))}
          </span>

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
            disabled={!canSubmitDrafts(drafts)}
          >
            투입 저장
          </Button>
          </div>
        }
      />

      {save.isError && <ErrorMessage error={save.error} />}
    </>
  )
}

export interface MaterialInputPickerProps {
  drafts: readonly DraftInput[]
  onChange: (drafts: DraftInput[]) => void
  /** 이 배치에 이미 들어간 소재. 같은 소재를 두 번 넣을 수 없다 */
  inputMaterialIds?: readonly number[]
  /**
   * 마지막 투입 칸 안에 붙일 것 (저장 줄).
   *
   * 저장을 모르는 컴포넌트라 무엇을 놓을지는 부르는 쪽이 정하고, **어디에 놓을지**만
   * 여기서 정한다 — 담은 것의 끝에 붙어야 "이만큼을 보낸다" 로 읽힌다.
   */
  actions?: ReactNode
  /**
   * 선택 칸이 펴져 있는가 — **부르는 쪽이 쥘 수도 있다.**
   *
   * 새 생산 화면은 "+ 투입 추가" 를 카드 제목 줄 오른쪽에 둔다. 버튼이 이 컴포넌트 밖에
   * 서니 펴고 접는 값도 밖에 있어야 한다. 넘기지 않으면 지금처럼 스스로 쥐고, 버튼도 선택
   * 칸 자리에 스스로 그린다.
   */
  choosing?: boolean
  onChoosingChange?: (choosing: boolean) => void
}

/**
 * 투입 초안을 모으는 부분만 떼어 낸 것.
 *
 * **저장을 모른다.** 배치 상세는 이미 있는 배치에 곧바로 저장하지만, 배치 열기 폼은 배치가
 * 아직 없어 만들고 나서야 보낼 수 있다 — 두 화면이 같은 입력 UI 를 쓰려면 저장이 밖에
 * 있어야 한다.
 */
export function MaterialInputPicker({
  drafts,
  onChange,
  inputMaterialIds = [],
  actions,
  choosing: controlledChoosing,
  onChoosingChange,
}: MaterialInputPickerProps) {
  const materials = useMyMaterials()
  /*
    투입 칸이 하나라도 열리면 선택 칸은 접고 그 자리에 "투입 추가" 를 둔다 — 소재를 여럿 함께
    넣을 수 있다는 것이 버튼으로 보인다. 누르면 선택 칸이 다시 펴진다.
  */
  const [ownChoosing, setOwnChoosing] = useState(false)
  const controlled = controlledChoosing !== undefined
  const choosing = controlled ? controlledChoosing : ownChoosing
  const setChoosing = (next: boolean) => {
    if (controlled) onChoosingChange?.(next)
    else setOwnChoosing(next)
  }

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
    (item) => groupOf(item, draftIds, inputMaterialIds) === GROUPS.usable,
  )

  const full = drafts.length >= PRODUCTION_INPUTS_MAX

  /*
    **고르면 곧바로 아래에 투입 칸이 열린다.** 예전에는 고른 뒤 "투입 추가" 를 한 번 더
    눌러야 했는데, 고르는 것 말고 그 버튼이 하는 일이 없었다 — 고르고 멈춘 사람에게는
    아무 일도 안 일어난 것처럼 보였다. 칸은 곧바로 비워 다음 소재를 고를 수 있게 둔다.
  */
  function addMaterial(materialId: number) {
    const material = materials.data?.find((item) => item.materialId === materialId)
    if (!material || full) return
    // 투입량 셋을 0 으로 시작한다. 가용량을 미리 채워 넣으면 사용자가 확인 없이 저장한다 —
    // 얼마가 제품이 되고 얼마가 자투리인지는 재단해 본 사람만 안다.
    onChange([
      ...drafts,
      { material, productUsed: 0, reusableScrap: 0, waste: 0, wasteReason: '' },
    ])
    setChoosing(false)
  }

  function update(materialId: number, patch: Partial<DraftInput>) {
    onChange(
      drafts.map((draft) =>
        draft.material.materialId === materialId ? { ...draft, ...patch } : draft,
      ),
    )
  }

  const problems = drafts.map(problemOf)

  return (
    <section className="material-input">
      {drafts.length === 0 || choosing ? (
        <div className="material-input__picker">
          <Select
            label="투입할 소재"
            placeholder="소재를 선택하세요"
            value=""
            options={optionsOf(materials.data, draftIds, inputMaterialIds)}
            onChange={(event) => {
              if (event.target.value) addMaterial(Number(event.target.value))
            }}
          />
          {drafts.length > 0 && (
            <Button variant="secondary" onClick={() => setChoosing(false)}>
              닫기
            </Button>
          )}
        </div>
      ) : controlled ? null : (
        <div className="material-input__more">
          <Button variant="secondary" onClick={() => setChoosing(true)} disabled={full}>
            + 투입 추가
          </Button>
        </div>
      )}

      {/*
        고를 수 있는 소재가 하나도 없다는 사실만 적는다. 목록은 보이는데 전부 선택이 안 되는
        상태가 설명 없이 남으면 고장으로 읽힌다 — 못 고르는 이유는 목록의 각 줄이 이미
        자기 자리에서 말한다(가공 필요·가용 수량 없음·이미 투입됨).
      */}
      {usable.length === 0 && drafts.length === 0 && (
        <p className="material-input__empty">지금 투입할 수 있는 소재가 없습니다.</p>
      )}

      {full && (
        <p className="material-input__note">
          한 번에 최대 {PRODUCTION_INPUTS_MAX}건까지 투입할 수 있습니다. 나눠서 저장해 주세요.
        </p>
      )}

      {/*
        자투리를 적는 자리에서 그 뜻을 말한다. 확정 안내(아래 박스)에 적어 두면 수량을
        적는 순간에는 보이지 않아, 무엇이 남고 무엇이 사라지는지 모른 채 칸을 채운다.
      */}
      {drafts.length > 0 && (
        <p className="material-input__note">
          자투리는 생산을 확정할 때 <strong>새 소재로 등록</strong>되어 보유 소재로
          돌아옵니다. 폐기는 그대로 사라집니다.
        </p>
      )}

      {drafts.map((draft, index) => (
        <DraftRow
          key={draft.material.materialId}
          draft={draft}
          problem={problems[index]}
          footer={index === drafts.length - 1 ? actions : undefined}
          onChange={(patch) => update(draft.material.materialId, patch)}
          onRemove={() =>
            onChange(
              drafts.filter((item) => item.material.materialId !== draft.material.materialId),
            )
          }
        />
      ))}
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
  footer,
}: {
  draft: DraftInput
  problem: string | null
  footer?: ReactNode
  onChange: (patch: Partial<DraftInput>) => void
  onRemove: () => void
}) {
  return (
    <div className="material-input__row">
      <div className="material-input__row-head">
        <strong>{draft.material.name}</strong>
        <MaterialProcessingBadge completedProcessTypes={draft.material.completedProcessTypes} generationType={draft.material.generationType as MaterialGenerationType}/>
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
          value={blankIfZero(draft.productUsed)}
          onChange={(event) => onChange({ productUsed: toGram(event.target.value) })}
        />
        <Input
          label="자투리 (g)"
          type="number"
          min={0}
          value={blankIfZero(draft.reusableScrap)}
          onChange={(event) => onChange({ reusableScrap: toGram(event.target.value) })}
        />
        <Input
          label="폐기 (g)"
          type="number"
          min={0}
          value={blankIfZero(draft.waste)}
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

      {problem && (
        <p className="material-input__error" role="alert">
          {problem}
        </p>
      )}

      {footer}
    </div>
  )
}


/**
 * 0 은 빈 칸으로 보여준다.
 *
 * 초안은 0 으로 시작하는데 그 0 이 칸에 적혀 있으면 값을 넣을 때마다 지우고 쓰게 된다 —
 * 세 칸이 나란히 있어 그 손이 세 번 간다.
 */
function blankIfZero(value: number): string {
  return value === 0 ? '' : String(value)
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
function optionsOf(
  items: readonly InventoryItem[],
  draftIds: readonly number[],
  inputMaterialIds: readonly number[],
): SelectOption[] {
  const labeled = items.map((item) => {
    const group = groupOf(item, draftIds, inputMaterialIds)
    const usable = group === GROUPS.usable
    return {
      value: String(item.materialId),
      // 고를 수 있는 것에만 가용량을 붙인다. 못 고르는 소재의 수량은 지금 쓸 수 없는 값이라
      // 읽는 사람을 헷갈리게 한다.
      // 가공을 거쳐도 이름은 그대로라, 같은 이름끼리는 가공 이력으로 가른다.
      label: usable
        ? `${itemName(item)} · 가용 ${formatKg(item.available)}`
        : itemName(item),
      group,
      disabled: !usable,
    }
  })

  // 고를 수 있는 것이 맨 위다. 묶음 안의 순서는 보유 목록 그대로 둔다 — 다시 정렬하면
  // 같은 소재가 화면마다 다른 자리에 온다.
  return GROUP_ORDER.flatMap((group) => labeled.filter((option) => option.group === group))
}

/**
 * 못 고르는 이유 = 묶음 이름.
 *
 * **한 줄에 "이름 · 이유" 로 붙여 적던 것을 올린 것이다.** `<option>` 안의 글자는 일부만
 * 색이나 굵기를 달리할 수 없어서, 붙여 적으면 어디까지가 소재 이름인지 읽히지 않았다.
 */
const GROUPS = {
  usable: '투입 가능',
  inBatch: '이미 투입한 소재',
  inDraft: '아래에 추가한 소재',
  missingRequired: '세척·해체·선별이 더 필요함',
  empty: '가용 수량 없음',
} as const

const GROUP_ORDER: readonly string[] = [
  GROUPS.usable,
  GROUPS.inDraft,
  GROUPS.inBatch,
  GROUPS.missingRequired,
  GROUPS.empty,
]

function groupOf(
  item: InventoryItem,
  draftIds: readonly number[],
  inputMaterialIds: readonly number[],
): string {
  if (inputMaterialIds.includes(item.materialId)) return GROUPS.inBatch
  if (draftIds.includes(item.materialId)) return GROUPS.inDraft
  // 폐기물은 세척·해체·선별을 다 거쳐야 제품이 될 수 있다. 서버가 소재마다 누적 이력을
  // 실어 주므로(`completedProcessTypes`) 화면이 따로 이어 붙이지 않는다.
  if (!isProductionEligible(item)) return GROUPS.missingRequired
  if (item.available <= 0) return GROUPS.empty
  return GROUPS.usable
}

function itemName(item: InventoryItem): string {
  return `${item.name} · ${processingHistoryText(item.completedProcessTypes, item.generationType as MaterialGenerationType)}`
}
