// Inventory(보유 소재) 도메인 타입.
// Backend `material_balance` / `material_lock` 에 대응한다.

/** `material_balance.status` */
export type BalanceStatus = 'ACTIVE' | 'DEPLETED'

/** `material_lock.purpose` — 수량이 잠긴 이유 */
export type LockPurpose = 'MARKETPLACE' | 'PROCESSING' | 'PRODUCTION'

/** `material_lock.status` */
export type LockStatus = 'ACTIVE' | 'RELEASED' | 'CONSUMED'

export interface InventoryItem {
  materialId: number
  materialName: string
  materialCode: string
  walletAddress: string
  /** 총 보유 수량 */
  quantity: number
  /** 잠긴 수량. 가용 수량 = quantity - lockedQuantity */
  lockedQuantity: number
  status: BalanceStatus
}

// TODO(2번/Material 담당): 잠금 해제·이력 조회 타입을 추가한다.
