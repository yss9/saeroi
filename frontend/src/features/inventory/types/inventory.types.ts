import type { ProcessingJobStatus } from '../../processing/types/processing.types.ts'

// Inventory(보유 소재) 도메인 타입.
// Backend `material_balance` / `material_lock` 에 대응한다.

/** `material_balance.status` */
export type BalanceStatus = 'ACTIVE' | 'DEPLETED'

/** `material_lock.purpose` — 수량이 잠긴 이유 */
export type LockPurpose = 'MARKETPLACE' | 'PROCESSING' | 'PRODUCTION'

/**
 * `material_lock.status`.
 *
 * `EXPIRED` 는 기한 경과로 자동 해제된 Lock 이다. 배치가 상태를 전환하지만,
 * **전환 전이라도 available 계산에서는 제외**한다
 * (`.harness/reference/02-domain-rules.md` Lock 상태 표).
 *
 * ⚠️ **이 문장을 한 번 잘못 덮었다 (2026-09-07).** MR !106 의 Backend 구현이
 * `status = ACTIVE` 만 보고 만료된 잠금까지 차감했고, 나는 그 구현이 맞다고 보고
 * 위 주석이 틀렸다고 고쳤다. 규칙이 정본이고 구현이 틀렸다 — MR !114 로 정정했다.
 * 상태를 전환해 주는 배치가 없어 만료된 잠금은 DB 에 `ACTIVE` 로 남으므로,
 * 서버 쿼리가 `expires_at` 을 직접 본다.
 *
 * **`available` 은 서버가 계산해 내려준다.** 클라이언트에서 다시 계산하지 않는다.
 * 이 enum 은 잠금 내역을 화면에 보여줄 때 쓸 값이고, 그 조회 API
 * (`GET /materials/{id}/locks`)는 아직 Jira 티켓조차 없다 (MR !94 요청 4번).
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

/** Processor가 물리적으로 배송받거나 가공 중인 소재. 토큰 소유자는 Brand로 유지된다. */
export interface ProcessingCustodyMaterial {
  jobId: number
  jobCode: string
  materialId: number
  materialCode: string
  materialName: string
  materialType: string
  quantity: number
  status: ProcessingJobStatus
  shippedAt: string
  receivedAt: string | null
}