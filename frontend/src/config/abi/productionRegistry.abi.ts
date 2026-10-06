// ⚠️ 이 파일은 자동 생성됩니다. 직접 수정하지 마세요.
//
//   생성 명령 : npm run gen:abi
//   원본      : blockchain/abi/ProductionRegistry.json  (blockchain/master 가 Source of Truth)
//
// 컨트랙트가 재배포되어 ABI 가 바뀌면 blockchain 저장소를 pull 한 뒤 위 명령을 다시 실행합니다.

export const PRODUCTION_REGISTRY_ABI = [
  "event MaterialConsumed(uint256 indexed materialId, uint256 indexed batchId, uint256 amount, address indexed brand, uint256 occurredAt)",
  "event OperationRecorded(bytes32 indexed operationId, bytes4 indexed selector, address indexed actor)",
  "event ProductLinked(uint256 indexed batchId, uint256 indexed productItemId, address indexed brand, uint256 occurredAt)",
  "event ProductionBatchCreated(uint256 indexed batchId, address indexed brand, uint256 productQuantity, bytes32 evidenceHash, uint256 occurredAt)",
  "event ProductionCanceled(uint256 indexed batchId, address indexed brand, uint256 occurredAt)",
  "event ProductionConfirmed(uint256 indexed batchId, address indexed brand, uint256 productQuantity, bytes32 evidenceHash, uint256 occurredAt)",
  "event ProductionPlanSubmitted(uint256 indexed batchId, address indexed brand, uint256 occurredAt)",
  "event ProductionScrapCreated(uint256 indexed batchId, uint256 indexed parentMaterialId, uint256 indexed scrapMaterialId, uint256 amount, address brand, uint256 occurredAt)",
  "event RoleAdminChanged(bytes32 indexed role, bytes32 indexed previousAdminRole, bytes32 indexed newAdminRole)",
  "event RoleGranted(bytes32 indexed role, address indexed account, address indexed sender)",
  "event RoleRevoked(bytes32 indexed role, address indexed account, address indexed sender)",
  "function BRAND_ROLE() view returns (bytes32)",
  "function DEFAULT_ADMIN_ROLE() view returns (bytes32)",
  "function cancelProduction(uint256 batchId, bytes32 operationId)",
  "function confirmProduction(uint256 batchId, bytes32 operationId)",
  "function createBatch(uint256 productQuantity, bytes32 operationId) returns (uint256 batchId)",
  "function getBatch(uint256 batchId) view returns (tuple(address brand, uint256 productQuantity, bytes32 evidenceHash, uint8 status, uint64 createdAt, uint64 confirmedAt, uint16 upcycledRatioBps))",
  "function getMaterialUses(uint256 batchId) view returns (tuple(uint256 tokenId, uint256 inputAmount, uint256 productUsed, uint256 scrap, uint256 waste, uint256 lockId, uint8 rootOrigin, string scrapMetadataUri, bytes32 scrapEvidenceHash, uint256 scrapTokenId)[])",
  "function getProductItems(uint256 batchId) view returns (uint256[])",
  "function getProductPassportProof(uint256 productItemId) view returns (tuple(uint256 productItemId, uint256 batchId, address brand, uint16 upcycledRatioBps, bytes32 evidenceHash, uint64 confirmedAt))",
  "function getRoleAdmin(bytes32 role) view returns (bytes32)",
  "function grantRole(bytes32 role, address account)",
  "function hasRole(bytes32 role, address account) view returns (bool)",
  "function isOperationProcessed(bytes32 operationId) view returns (bool)",
  "function linkProductItems(uint256 batchId, uint256[] productItemIds, bytes32 operationId)",
  "function markFailed(uint256 batchId, bytes32 operationId)",
  "function materialToken() view returns (address)",
  "function nextBatchId() view returns (uint256)",
  "function parentsOfScrap(uint256 scrapId) view returns (uint256[])",
  "function productItemBatch(uint256) view returns (uint256)",
  "function renounceRole(bytes32 role, address callerConfirmation)",
  "function revokeRole(bytes32 role, address account)",
  "function submitPlanAndLock(uint256 batchId, bytes32 evidenceHash, uint256[] tokenIds, uint256[] inputAmounts, uint256[] productUsed, uint256[] scrap, uint256[] waste, string[] scrapMetadataUris, bytes32[] scrapEvidenceHashes, bytes32 operationId)",
  "function supportsInterface(bytes4 interfaceId) view returns (bool)",
] as const
