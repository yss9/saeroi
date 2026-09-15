// Production 도메인 타입. Backend `production_batch` 에 대응한다.

/** `production_batch.status` — 블록체인 기록 상태를 함께 표현한다 */
export type ProductionBatchStatus = 'DRAFT' | 'SIGNING' | 'CONFIRMED' | 'FAILED' | 'CANCELED'

/**
 * 생산 배치 한 건과 그 투입 목록.
 *
 * Backend 원본: `production/dto/ProductionBatchResponse.java`.
 *
 * **투입이 응답에 함께 담긴다.** 화면이 언제나 둘을 같이 보여주기 때문이고, 나눠 부르면
 * 그 사이에 투입이 바뀌어 합계와 목록이 어긋난 화면이 그려진다.
 *
 * `upcycleRatio` 는 **확정 전에는 0** 이다. 계산은 확정 시점에 한 번 하고 스냅샷으로
 * 굳힌다 — 매번 재계산하면 소재 데이터가 바뀔 때 이미 팔린 제품의 비율이 소급해 변한다.
 */
export interface ProductionBatch {
  productionBatchId: number
  batchCode: string
  batchName: string
  productModelId: number
  producedQuantity: number
  producerId: number
  /** 배치를 연 시점의 활성 지갑. 온체인 기록에 그대로 남는다 */
  producerWallet: string
  /**
   * 온체인 배치 번호. `createBatch` 를 서명하고 서버에 알리기 전에는 null 이다.
   *
   * **뒤의 세 단계가 모두 이 번호를 인자로 받는다.** 화면이 들고 있지 않고 서버가 보관하는
   * 이유가 그것이다 — 새로고침 한 번에 잃어버리면 이미 체인에 만들어진 배치를 다시 가리킬
   * 수 없다.
   */
  chainBatchId: number | null
  status: ProductionBatchStatus
  /** 투입 합계 (gram) */
  totalUsedQuantity: number
  virginUsedQuantity: number
  upcycledUsedQuantity: number
  /** 확정 전에는 0. 서버가 문자열로 줄 수도 있어(BigDecimal) 표시 전에 Number 로 좁힌다 */
  upcycleRatio: number
  /**
   * 체인에 올린 manifest 의 해시. `submitPlanAndLock` 결과를 알리기 전에는 null 이다.
   *
   * 이 값이 채워졌는지가 "계획이 체인에 기록됐는가" 의 답이다.
   */
  evidenceHash: string | null
  manifestUrl: string | null
  createdAt: string
  confirmedAt: string | null
  inputs: ProductionInput[]
}

/**
 * 투입 한 건.
 *
 * `materialLockId` 가 함께 온다 — 투입을 취소하면 이 잠금이 풀린다는 것을 화면이 설명할 수
 * 있어야 하고, 잠금이 풀리지 않은 채 남는 사고를 눈으로 확인할 수 있다.
 */
export interface ProductionInput {
  productionInputId: number
  materialId: number
  /**
   * 사람이 읽는 소재 식별자.
   *
   * **온체인 2단계가 이 값으로 짝을 맞춘다.** manifest 는 소재를 코드로 적고(체인에 남아
   * 다른 시스템이 읽을 수 있어야 한다) 이 목록은 `productionInputId` 를 갖고 있어, 둘을
   * 잇는 유일한 값이다.
   */
  materialCode: string
  /** gram */
  inputQuantity: number
  productUsedQuantity: number
  reusableScrapQuantity: number
  wasteQuantity: number
  wasteReason: string | null
  materialLockId: number
  /**
   * 온체인 잠금 번호. `submitPlanAndLock` 결과를 알리기 전에는 null 이다.
   *
   * 서버가 보관하는 이유는 3단계(소각)가 이 번호로 대상을 정하기 때문이다 — 화면이 들고
   * 있으면 그 사이 새로고침 한 번에 잃는다.
   */
  chainLockId: number | null
}

/**
 * 투입 한 건의 요청 값.
 *
 * **투입량을 셋으로 나눠 보낸다.**
 *
 * ```text
 *   inputQuantity = productUsedQuantity   제품에 실제로 들어간 양
 *                 + reusableScrapQuantity 자투리 (새 소재로 되살아난다)
 *                 + wasteQuantity         폐기
 * ```
 *
 * 합계만 보내고 서버가 쪼개는 방식은 불가능하다 — 어디까지가 제품이고 어디부터 자투리인지는
 * 실제로 재단해 본 브랜드만 안다. 대신 **식이 맞는지는 서버가 행마다 검사한다**
 * (`PRODUCTION_INPUT_QUANTITY_MISMATCH`). 배치 합계로만 보면 한 소재를 과다 입력하고 다른
 * 소재를 과소 입력해도 통과하기 때문이다.
 *
 * 단위는 **gram** 이다. `materialLockId` 는 보내지 않는다 — 잠금은 서버가 이 요청을
 * 처리하면서 직접 걸고, 클라이언트가 고르게 하면 남이 잡아 둔 잠금을 자기 투입에 붙일 수 있다.
 */
export interface ProductionInputRequest {
  materialId: number
  inputQuantity: number
  productUsedQuantity: number
  reusableScrapQuantity: number
  wasteQuantity: number
  /** 200자 이하. 폐기가 없으면 비워 둔다 */
  wasteReason?: string
}

/**
 * 온체인에 올릴 생산 manifest 와 그 해시 (`GET /{batchId}/chain/manifest`).
 *
 * **해시는 서버가 계산한 값을 그대로 쓴다.** 화면이 다시 계산하지 않는다 — 필드 순서·공백·
 * 인코딩이 조금만 달라도 해시가 완전히 달라지고, 그러면 나중에 재현해 대조할 수 없다.
 * 추적성의 마지막 한 칸이 그 대조에 걸려 있다.
 *
 * `manifest` 본문은 화면이 읽을 필요가 거의 없다 — 무엇을 서명하는지 사용자에게 보여줄 때만
 * 쓴다. 서버 직렬화 규칙은 선언 순서 · 공백 없음 · UTF-8 이다.
 */
export interface ProductionManifest {
  manifest: {
    batchCode: string
    productModelId: number
    producedQuantity: number
    producerWallet: string
    /** 문자열이다 — BigDecimal 을 그대로 직렬화한다 */
    upcycleRatio: string
    totalUsedQuantity: number
    virginUsedQuantity: number
    upcycledUsedQuantity: number
    /**
     * 투입 목록.
     *
     * **`submitPlanAndLock` 의 배열 인자가 이 순서 그대로다.** 컨트랙트가 인덱스로 짝을
     * 맞추므로 화면이 다른 응답과 조합해 만들면 순서가 어긋나 엉뚱한 소재가 잠긴다.
     */
    inputs: {
      /** DB id 가 아니라 소재 코드다 — manifest 는 체인에 남아 다른 시스템이 읽을 수 있다 */
      materialCode: string
      /** 온체인 토큰 번호. 서명에 그대로 들어간다 */
      materialTokenId: number
      inputQuantity: number
      productUsedQuantity: number
      reusableScrapQuantity: number
      wasteQuantity: number
      /** 자투리 메타데이터 주소. 저장소 경로가 아직 없어 지금은 빈 문자열이다 */
      scrapMetadataUri: string
      /** 자투리가 없으면 32바이트 0 이다 — 컨트랙트가 scrap > 0 일 때만 0 이 아닌 값을 요구한다 */
      scrapEvidenceHash: string
    }[]
    itemCodes: string[]
  }
  /** `0x` + 64 hex. 서명과 서버 보고에 이 값을 그대로 쓴다 */
  evidenceHash: string
}

/**
 * `product_item.status` — 개별 제품 개체의 상태.
 *
 * Backend `ProductItemStatus` 와 문자열이 일치한다.
 */
export type ProductItemStatus = 'IN_STOCK' | 'RESERVED' | 'SOLD'

export const PRODUCT_ITEM_STATUS_LABEL: Record<ProductItemStatus, string> = {
  IN_STOCK: '재고',
  RESERVED: '예약',
  SOLD: '판매 완료',
}

/**
 * 확정으로 만들어진 제품 개체 하나.
 *
 * Backend 원본: `production/dto/ProductionItemResponse.java`.
 * **QR 라벨 인쇄가 쓰는 응답이다.**
 *
 * ⚠️ **`qrToken` 은 인증 없이 열리는 조회 경로(`/qr/:qrToken`)의 열쇠다.** 브랜드는 이
 * 값을 제품에 인쇄해야 하므로 화면이 받아야 하지만, 아무에게나 보이면 그 제품의 이력을
 * 누구나 열 수 있다 — 서버가 배치 소유자에게만 준다(남의 배치는 403). 화면도 길게 늘어놓지
 * 않고 복사·열기 동작으로 다룬다.
 *
 * `productModelId` 는 없다. 배치 조회의 응답이고 배치가 이미 그 값을 갖고 있어, 개체마다
 * 반복하면 같은 값이 개수만큼 실린다.
 */
export interface ProductionItem {
  productItemId: number
  /** 사람이 읽는 개체 식별자 */
  itemCode: string
  /** 배치 안에서의 일련번호 (1부터) */
  serialNumber: number
  /** `/qr/{qrToken}` 으로 열린다 */
  qrToken: string
  /**
   * QR 이미지가 담고 있는 주소 그 자체. 서버가 조립한 절대 주소다.
   *
   * **토큰에 접두어를 붙이는 규칙을 화면이 따로 갖지 않는다.** 그 규칙이 두 곳이 되면
   * 서버 설정(`app.qr.base-url`)이 바뀔 때 한쪽만 바뀌고, 그때 인쇄된 라벨과 화면이
   * 서로 다른 주소를 가리킨다 — 이미 제품에 붙어 나간 QR 은 고칠 수 없다.
   */
  qrUrl: string
  status: ProductItemStatus
  createdAt: string
}

/**
 * 배치 열기 요청 (`POST /v1/production-batches`).
 *
 * 요청이 정할 수 없는 값이 셋 있다 — `producerId`(JWT), `producerWallet`(등록된 활성 지갑),
 * `status`(언제나 `DRAFT`). 수량 세 컬럼과 업사이클 비율도 받지 않는다: 확정 시점에 실제
 * 투입으로 계산한다. 요청이 정할 수 있으면 실제와 다른 비율을 적어 넣을 수 있고, 그 값이
 * 그대로 제품에 붙는다.
 */
export interface OpenProductionBatchRequest {
  /** 필수, 200자 이하 */
  batchName: string
  /** 내 제품이어야 한다 */
  productModelId: number
  /** 1개 이상 */
  producedQuantity: number
}

/**
 * 한 요청의 투입 상한 (`ProductionInputsRequest.MAX_INPUTS`).
 *
 * 상한이 없으면 한 번의 요청으로 소재 수천 건을 잠글 수 있다. 더 필요하면 나눠 부른다.
 */
export const PRODUCTION_INPUTS_MAX = 20

/**
 * 이 소재를 생산에 투입할 수 있는가.
 *
 * **폐기물에서 바로 온 소재(WASTE + INITIAL)는 투입할 수 없다.** 가공을 최소 한 번 거쳐야
 * 한다 — 막지 않으면 가공 이력이 없는 제품이 업사이클로 팔린다. 서버도 같은 규칙으로
 * 거절한다 (`PRODUCTION_MATERIAL_NOT_ELIGIBLE`).
 *
 * 화면이 같은 판단을 하는 이유는, 고를 수 없는 소재를 왜 못 고르는지 그 자리에서 말해야
 * 하기 때문이다. 서버에만 맡기면 사용자는 수량까지 입력하고 저장을 눌러야 이유를 안다.
 */
export function isProductionEligible(material: {
  rootOrigin: string
  generationType: string
}): boolean {
  return !(material.rootOrigin === 'WASTE' && material.generationType === 'INITIAL')
}

// TODO(5번/Production 담당): 산출물(production_output) 타입을 추가한다.
