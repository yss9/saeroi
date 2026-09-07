// ⚠️ 이 파일은 자동 생성됩니다. 직접 수정하지 마세요.
//
//   생성 명령 : npm run gen:abi
//   원본      : blockchain/abi/Marketplace.json  (blockchain/master 가 Source of Truth)
//
// 컨트랙트가 재배포되어 ABI 가 바뀌면 blockchain 저장소를 pull 한 뒤 위 명령을 다시 실행합니다.

export const MARKETPLACE_ABI = [
  "event DisputeOpened(uint256 indexed orderId, address indexed opener, bytes32 reasonHash, uint256 occurredAt)",
  "event DisputeSettled(uint256 indexed orderId, uint8 resolution, address seller, address buyer, uint256 occurredAt)",
  "event DisputeSigned(uint256 indexed orderId, address indexed signer, uint8 resolution, uint256 occurredAt)",
  "event ListingCancelled(uint256 indexed listingId, address indexed seller, uint256 releasedQuantity)",
  "event ListingCreated(uint256 indexed listingId, uint256 indexed materialId, uint256 indexed materialLockId, address seller, uint256 quantity, uint256 pricePerKg)",
  "event MaterialTransferred(uint256 indexed orderId, address indexed seller, address indexed buyer, uint256 materialId, uint256 quantity)",
  "event OrderCreated(uint256 indexed orderId, uint256 indexed listingId, address indexed buyer, uint256 quantity, uint256 totalPrice)",
  "event OrderStatusChanged(uint256 indexed orderId, uint8 previousStatus, uint8 newStatus, address indexed operator)",
  "event PaymentConfirmed(uint256 indexed orderId, bytes32 indexed paymentReferenceHash, address indexed operator)",
  "event ReceiptConfirmed(uint256 indexed orderId, address indexed buyer)",
  "event RoleAdminChanged(bytes32 indexed role, bytes32 indexed previousAdminRole, bytes32 indexed newAdminRole)",
  "event RoleGranted(bytes32 indexed role, address indexed account, address indexed sender)",
  "event RoleRevoked(bytes32 indexed role, address indexed account, address indexed sender)",
  "function DEFAULT_ADMIN_ROLE() view returns (bytes32)",
  "function PAYMENT_MANAGER_ROLE() view returns (bytes32)",
  "function availableBalance(address owner, uint256 materialId) view returns (uint256)",
  "function calculateTotalPrice(uint256 quantityGram, uint256 pricePerKg) pure returns (uint256)",
  "function cancelListing(uint256 listingId)",
  "function cancelOrder(uint256 orderId)",
  "function confirmReceipt(uint256 orderId)",
  "function createListing(uint256 materialId, uint256 quantity, uint256 pricePerKg) returns (uint256 listingId)",
  "function createOrder(uint256 listingId, uint256 quantity) returns (uint256 orderId)",
  "function getDispute(uint256 orderId) view returns (tuple(bytes32 reasonHash, uint8 sellerChoice, uint8 buyerChoice, uint64 openedAt, uint64 settledAt))",
  "function getListing(uint256 listingId) view returns (tuple(uint256 id, uint256 materialId, uint256 materialLockId, address seller, uint256 initialQuantity, uint256 remainingQuantity, uint256 reservedQuantity, uint256 pricePerKg, uint8 status))",
  "function getOrder(uint256 orderId) view returns (tuple(uint256 id, uint256 listingId, address seller, address buyer, uint256 materialId, uint256 quantity, uint256 totalPrice, bytes32 paymentReferenceHash, uint8 status))",
  "function getRoleAdmin(bytes32 role) view returns (bytes32)",
  "function grantRole(bytes32 role, address account)",
  "function hasRole(bytes32 role, address account) view returns (bool)",
  "function markPaid(uint256 orderId, bytes32 paymentReferenceHash)",
  "function markShipped(uint256 orderId)",
  "function materialToken() view returns (address)",
  "function nextListingId() view returns (uint256)",
  "function nextOrderId() view returns (uint256)",
  "function openDispute(uint256 orderId, bytes32 reasonHash)",
  "function renounceRole(bytes32 role, address callerConfirmation)",
  "function revokeRole(bytes32 role, address account)",
  "function signDisputeResolution(uint256 orderId, uint8 resolution)",
  "function supportsInterface(bytes4 interfaceId) view returns (bool)",
] as const
