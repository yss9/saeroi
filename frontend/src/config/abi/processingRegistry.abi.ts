// ⚠️ 이 파일은 자동 생성됩니다. 직접 수정하지 마세요.
//
//   생성 명령 : npm run gen:abi
//   원본      : blockchain/abi/ProcessingRegistry.json  (blockchain/master 가 Source of Truth)
//
// 컨트랙트가 재배포되어 ABI 가 바뀌면 blockchain 저장소를 pull 한 뒤 위 명령을 다시 실행합니다.

export const PROCESSING_REGISTRY_ABI = [
  "event MaterialProcessed(uint256 indexed jobId, uint256 indexed inputMaterialId, uint256 indexed outputMaterialId, uint256 outputAmount, address processor, address brand, uint256 occurredAt)",
  "event ProcessingApproved(uint256 indexed jobId, address indexed brand, uint256 inputAmount, uint256 outputAmount, uint256 lossAmount, uint256 occurredAt)",
  "event ProcessingConfirmed(uint256 indexed jobId, address indexed processor, address indexed brand, bytes32 evidenceHash, uint256 occurredAt)",
  "event ProcessingJobCreated(uint256 indexed jobId, address indexed brand, address indexed processor, uint256 inputAmount, uint256 occurredAt)",
  "event ProcessingReceived(uint256 indexed jobId, address indexed processor, uint256 occurredAt)",
  "event ProcessingRejected(uint256 indexed jobId, address indexed brand, uint256 occurredAt)",
  "event ProcessingResultSubmitted(uint256 indexed jobId, address indexed processor, bytes32 evidenceHash, uint256 outputAmount, uint256 occurredAt)",
  "event ProcessingShipped(uint256 indexed jobId, address indexed brand, bytes32 evidenceHash, uint256 occurredAt)",
  "event RoleAdminChanged(bytes32 indexed role, bytes32 indexed previousAdminRole, bytes32 indexed newAdminRole)",
  "event RoleGranted(bytes32 indexed role, address indexed account, address indexed sender)",
  "event RoleRevoked(bytes32 indexed role, address indexed account, address indexed sender)",
  "function BRAND_ROLE() view returns (bytes32)",
  "function DEFAULT_ADMIN_ROLE() view returns (bytes32)",
  "function PROCESSOR_ROLE() view returns (bytes32)",
  "function approveProcessingResult(uint256 jobId)",
  "function cancelJob(uint256 jobId)",
  "function confirmReceipt(uint256 jobId)",
  "function createJob(address processor, uint256[] tokenIds, uint256[] amounts, uint8[] processingTypes) returns (uint256 jobId)",
  "function getInputs(uint256 jobId) view returns (tuple(uint256 tokenId, uint256 amount, uint256 lockId, uint8 rootOrigin)[])",
  "function getJob(uint256 jobId) view returns (tuple(address brand, address processor, bytes32 shippingEvidenceHash, bytes32 resultEvidenceHash, uint8 status, uint64 createdAt, uint64 updatedAt))",
  "function getOutputs(uint256 jobId) view returns (tuple(uint256 inputIndex, uint256 amount, uint8 grade, string metadataUri, bytes32 evidenceHash, uint256 tokenId)[])",
  "function getProcessingTypes(uint256 jobId) view returns (uint8[])",
  "function getRoleAdmin(bytes32 role) view returns (bytes32)",
  "function grantRole(bytes32 role, address account)",
  "function hasRole(bytes32 role, address account) view returns (bool)",
  "function inputTotal(uint256 jobId) view returns (uint256 total)",
  "function markShipped(uint256 jobId, bytes32 evidenceHash)",
  "function materialToken() view returns (address)",
  "function nextJobId() view returns (uint256)",
  "function outputTotal(uint256 jobId) view returns (uint256 total)",
  "function parentsOf(uint256 childId) view returns (uint256[])",
  "function rejectProcessingResult(uint256 jobId)",
  "function renounceRole(bytes32 role, address callerConfirmation)",
  "function revokeRole(bytes32 role, address account)",
  "function startProcessing(uint256 jobId)",
  "function submitProcessingResult(uint256 jobId, uint256[] inputIndexes, uint256[] amounts, uint8[] grades, string[] metadataUris, bytes32[] evidenceHashes, bytes32 resultEvidenceHash)",
  "function supportsInterface(bytes4 interfaceId) view returns (bool)",
] as const
