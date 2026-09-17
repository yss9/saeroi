// 투입 초안을 다루는 순수 함수들.
//
// 컴포넌트 파일 밖에 둔다 — 배치 상세와 배치 열기 폼이 같은 초안을 쓰고, 컴포넌트
// 파일에서 함수를 내보내면 Fast Refresh 가 깨진다.

import { formatKg } from '../../../utils/quantity.ts'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import type { ProductionInputRequest } from '../types/production.types.ts'

/** 화면이 모아 두는 투입 초안. 저장 전까지는 서버에 아무것도 보내지 않는다. */
export interface DraftInput {
  material: InventoryItem
  /** gram. 셋의 합이 inputQuantity 다 */
  productUsed: number
  reusableScrap: number
  waste: number
  wasteReason: string
}

/** 투입량은 셋의 합이다. 사용자가 따로 입력하지 않는다 — 두 값이 어긋날 자리를 만들지 않는다. */
export function inputQuantityOf(draft: DraftInput): number {
  return draft.productUsed + draft.reusableScrap + draft.waste
}

export function toRequest(draft: DraftInput): ProductionInputRequest {
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
export function problemOf(draft: DraftInput): string | null {
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

/** 초안이 전부 보낼 수 있는 상태인가. 한 건이라도 어긋나면 서버가 통째로 거절한다. */
export function canSubmitDrafts(drafts: readonly DraftInput[]): boolean {
  return drafts.length > 0 && drafts.every((draft) => problemOf(draft) === null)
}

/** 초안 전체의 투입 합계 (gram). */
export function totalDraftQuantity(drafts: readonly DraftInput[]): number {
  return drafts.reduce((sum, draft) => sum + inputQuantityOf(draft), 0)
}
