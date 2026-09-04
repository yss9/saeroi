// Marketplace ABI. 형식과 관리 원칙은 materialToken.abi.ts 참고.
//
// 📌 결제는 이 컨트랙트가 하지 않는다 (정책 08절).
// 대금은 카카오페이 PG 로 오프체인 결제하고, 온체인에는 소유권 이전 결과만 남는다.
// 그래서 `markPaid` 는 결제 사실을 **기록만** 하며 PAYMENT_MANAGER_ROLE 을 요구한다 —
// 즉 **서버가 호출한다. 프론트에서 부르지 않는다.**

export const MARKETPLACE_ABI = [
  // ── 판매 등록 ────────────────────────────────────────
  // quantity 는 gram, pricePerKg 는 원 단위 정수다.
  // 등록 시 MARKETPLACE Lock 이 함께 생성된다 (정책 06.3).
  'function createListing(uint256 materialId, uint256 quantity, uint256 pricePerKg) returns (uint256 listingId)',
  'function cancelListing(uint256 listingId)',
  'function getListing(uint256 listingId) view returns (tuple(uint256 id, uint256 materialId, uint256 materialLockId, address seller, uint256 initialQuantity, uint256 remainingQuantity, uint256 reservedQuantity, uint256 pricePerKg, uint8 status))',
  'function nextListingId() view returns (uint256)',

  // ── 주문 ─────────────────────────────────────────────
  'function createOrder(uint256 listingId, uint256 quantity) returns (uint256 orderId)',
  'function cancelOrder(uint256 orderId)',
  'function getOrder(uint256 orderId) view returns (tuple(uint256 id, uint256 listingId, address seller, address buyer, uint256 materialId, uint256 quantity, uint256 totalPrice, bytes32 paymentReferenceHash, uint8 status))',
  'function nextOrderId() view returns (uint256)',

  // ── 배송 · 수령 ──────────────────────────────────────
  // Seller 가 발송을 기록하고, Buyer 수령 확인 시점에 소유권이 이전된다 (정책 07.2).
  'function markShipped(uint256 orderId)',
  'function confirmReceipt(uint256 orderId)',

  // ── 조회 ─────────────────────────────────────────────
  'function availableBalance(address owner, uint256 materialId) view returns (uint256)',
  'function calculateTotalPrice(uint256 quantityGram, uint256 pricePerKg) pure returns (uint256)',

  // ── 이벤트 ───────────────────────────────────────────
  // createListing / createOrder 의 반환값은 영수증에서 직접 못 읽으므로
  // id 는 이 이벤트에서 꺼낸다.
  'event ListingCreated(uint256 indexed listingId, uint256 indexed materialId, uint256 indexed materialLockId, address seller, uint256 quantity, uint256 pricePerKg)',
  'event ListingCancelled(uint256 indexed listingId, address indexed seller, uint256 releasedQuantity)',
  'event OrderCreated(uint256 indexed orderId, uint256 indexed listingId, address indexed buyer, uint256 quantity, uint256 totalPrice)',
  'event OrderStatusChanged(uint256 indexed orderId, uint8 previousStatus, uint8 newStatus, address indexed operator)',
  'event PaymentConfirmed(uint256 indexed orderId, bytes32 indexed paymentReferenceHash, address indexed operator)',
  'event ReceiptConfirmed(uint256 indexed orderId, address indexed buyer)',
  'event MaterialTransferred(uint256 indexed orderId, address indexed seller, address indexed buyer, uint256 materialId, uint256 quantity)',
] as const
