// Inventory(보유 소재) 도메인 타입.
// Backend `material_balance` / `material_lock` 에 대응한다.

/** `material_balance.status` */
export type BalanceStatus = 'ACTIVE' | 'DEPLETED'

/** `material_lock.purpose` — 수량이 잠긴 이유 */
export type LockPurpose = 'MARKETPLACE' | 'PROCESSING' | 'PRODUCTION'

/**
 * `material_lock.status`.
 *
 * ⚠️ **`EXPIRED` 동작 정정 (2026-09-07).** 이전 주석은 *"배치가 상태를 전환하지만
 * 전환 전이라도 available 계산에서는 제외한다"* 였는데, **Backend 는 반대로 동작한다**
 * (MR !106). `expires_at` 이 지난 `ACTIVE` 잠금도 그대로 차감한다 —
 * EXPIRED 로 전환해 주는 배치가 **어디에도 없어서** 과소 계산을 택했다.
 * 실제보다 적게 보여 판매를 못 하는 편이 초과 판매보다 안전하다.
 *
 * 그리고 **`available` 은 서버가 계산해 내려준다.** 클라이언트에서 다시 계산하지 않는다.
 * 이 enum 은 잠금 내역을 화면에 보여줄 때 쓸 값이고, 그 조회 API
 * (`GET /materials/{id}/locks`)는 아직 티켓조차 없다.
 */
export type LockStatus = 'ACTIVE' | 'RELEASED' | 'CONSUMED' | 'EXPIRED'

/**
 * `GET /api/materials/me` 의 항목 — 내가 보유한 소재 한 건.
 *
 * <b>`totalQuantity` 와 `quantity` 를 섞으면 화면이 거짓을 말한다.</b>
 *
 * | 필드 | 뜻 |
 * | --- | --- |
 * | `totalQuantity` | 그 소재의 **총 발행량** |
 * | `quantity` | **내 보유량** |
 *
 * 거래가 일어나면 둘이 달라진다. "보유 수량" 자리에 `totalQuantity` 를 넣으면
 * "1200kg 을 갖고 있다" 는 거짓이 되고, 그게 판매 등록으로 이어지면 초과 판매다.
 *
 * 수량은 모두 **gram 정수**다. 화면 표시는 `utils/quantity.ts` 의 `formatKg()` 로 한다.
 *
 * `walletAddress` 가 없는 것은 의도적이다 — 내 보유 목록이므로 내 지갑이 자명하고,
 * 서버 응답에도 없다. 보유자별 잔량(`MaterialBalance`)에는 있다.
 */
export interface InventoryItem {
  materialId: number
  materialCode: string
  materialType: string
  rootOrigin: string
  generationType: string
  name: string
  grade: string | null
  status: BalanceStatus
  /** 그 소재의 총 발행량 (gram) */
  totalQuantity: number
  /** 내 보유량 (gram) */
  quantity: number
  /** ACTIVE 잠금 합계 (gram) */
  lockedQuantity: number
  /** quantity − lockedQuantity (gram). 음수 가능 — 데이터 파손 신호다 */
  available: number
  /** 이 값이 언제 기준인지. 온체인 잔액 Projection 동기화 전에는 특히 중요하다 */
  syncedAt: string
}

// TODO(6번/Material 담당): 잠금 내역 조회 타입을 추가한다.
// 선행: GET /materials/{materialId}/locks — 아직 Jira 티켓이 없다 (MR !94 요청 4번).
// 그게 없으면 잠긴 수량은 보여도 "왜 잠겼는지" 를 못 보여준다.
