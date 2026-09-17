// ⚠️ 이 파일은 자동 생성됩니다. 직접 수정하지 마세요.
//
//   생성 명령 : npm run gen:abi
//   원본      : blockchain/abi/PredictionMarket.json  (blockchain/master 가 Source of Truth)
//
// 컨트랙트가 재배포되어 ABI 가 바뀌면 blockchain 저장소를 pull 한 뒤 위 명령을 다시 실행합니다.

export const PREDICTION_MARKET_ABI = [
  "event BetPlaced(uint256 indexed marketId, address indexed bettor, uint256 indexed bucketIndex, uint256 amount)",
  "event MarketCanceled(uint256 indexed marketId)",
  "event MarketCreated(uint256 indexed marketId, uint256 indexed batchId, uint256 indexed productModelId, address brand, uint256 productionQuantity, uint256 unitPrice, uint64 bettingEndsAt, uint64 salesStartAt, uint64 salesEndAt)",
  "event MarketResolved(uint256 indexed marketId, uint256 soldQuantity, uint256 indexed winningBucket, bytes32 resultHash, bool refundOnly)",
  "event RefundClaimed(uint256 indexed marketId, address indexed bettor, uint256 amount)",
  "event RewardClaimed(uint256 indexed marketId, address indexed bettor, uint256 amount)",
  "event RoleAdminChanged(bytes32 indexed role, bytes32 indexed previousAdminRole, bytes32 indexed newAdminRole)",
  "event RoleGranted(bytes32 indexed role, address indexed account, address indexed sender)",
  "event RoleRevoked(bytes32 indexed role, address indexed account, address indexed sender)",
  "function DEFAULT_ADMIN_ROLE() view returns (bytes32)",
  "function MARKET_CREATOR_ROLE() view returns (bytes32)",
  "function ORACLE_ROLE() view returns (bytes32)",
  "function batchMarketId(uint256) view returns (uint256)",
  "function bucketCount(uint256 marketId) view returns (uint256)",
  "function bucketPool(uint256, uint256) view returns (uint256)",
  "function cancelMarket(uint256 marketId)",
  "function claim(uint256 marketId) returns (uint256 amount)",
  "function claimed(uint256, address) view returns (bool)",
  "function createMarket(uint256 batchId, uint256 productModelId, uint256 unitPrice, uint64 bettingEndsAt, uint64 salesStartAt, uint64 salesEndAt, uint256[] bucketUpperBounds) returns (uint256 marketId)",
  "function getBucketUpperBounds(uint256 marketId) view returns (uint256[])",
  "function getMarket(uint256 marketId) view returns (tuple(uint256 batchId, uint256 productModelId, uint256 unitPrice, uint256 productionQuantity, address brand, uint64 bettingEndsAt, uint64 salesStartAt, uint64 salesEndAt, uint8 status, uint32 winningBucket, uint256 soldQuantity, bytes32 resultHash, uint256 totalPool, uint256 remainingPayout, uint256 remainingWinningStake, bool refundOnly))",
  "function getRoleAdmin(bytes32 role) view returns (bytes32)",
  "function grantRole(bytes32 role, address account)",
  "function hasRole(bytes32 role, address account) view returns (bool)",
  "function nextMarketId() view returns (uint256)",
  "function placeBet(uint256 marketId, uint256 bucketIndex, uint256 amount)",
  "function predictionToken() view returns (address)",
  "function previewBucket(uint256 marketId, uint256 soldQuantity) view returns (uint256)",
  "function productionRegistry() view returns (address)",
  "function renounceRole(bytes32 role, address callerConfirmation)",
  "function revokeRole(bytes32 role, address account)",
  "function submitResult(uint256 marketId, uint256 soldQuantity, bytes32 resultHash)",
  "function supportsInterface(bytes4 interfaceId) view returns (bool)",
  "function userBucketStake(uint256, address, uint256) view returns (uint256)",
  "function userTotalStake(uint256, address) view returns (uint256)",
] as const
