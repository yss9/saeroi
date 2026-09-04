// MaterialToken (ERC-1155) ABI.
//
// 전체 artifact JSON 대신 **사람이 읽는 형식(human-readable ABI)** 을 쓴다.
// ethers v6 가 이 문자열 배열을 그대로 받는다.
//
//   - JSON artifact 는 한 컨트랙트당 1,000줄이 넘고 번들에 그대로 실린다.
//   - 여기 있는 시그니처가 곧 "프론트가 부르는 것의 전부" 라 검토 범위가 명확하다.
//   - 오타가 나면 ethers 가 호출 시점에 바로 던진다 (조용히 잘못 나가지 않는다).
//
// ⚠️ Source of Truth 는 blockchain/master 의 artifact 다. 컨트랙트가 재배포되어
// 시그니처가 바뀌면 여기도 함께 고쳐야 한다.
//
// TODO(1번/Blockchain 담당): 이벤트 로그를 파싱해야 하는 기능(Journey 온체인 대조 등)이
// 생기면 그때 전체 JSON 을 받아 이 파일을 대체한다. 지금은 호출에 필요한 만큼만 둔다.

export const MATERIAL_TOKEN_ABI = [
  // ── 잔량 조회 ────────────────────────────────────────
  // 정책 06절: available = balance − locked
  'function balanceOf(address account, uint256 id) view returns (uint256)',
  'function availableBalance(address owner, uint256 tokenId) view returns (uint256)',
  'function lockedBalanceOf(address owner, uint256 tokenId) view returns (uint256)',
  'function remainingSupply(uint256 tokenId) view returns (uint256)',

  // ── 소재 정보 ────────────────────────────────────────
  'function materialInfo(uint256 tokenId) view returns (tuple(string metadataUri, bytes32 evidenceHash, uint256 totalMinted, uint256 totalBurned, uint8 rootOrigin, uint8 generationType, uint8 verificationState, uint8 status, address registrant, bool exists))',
  'function uri(uint256 tokenId) view returns (string)',
  'function nextMaterialId() view returns (uint256)',

  // ── Lock ─────────────────────────────────────────────
  'function materialLockInfo(uint256 lockId) view returns (tuple(uint256 id, address operator, address owner, uint256 tokenId, uint256 initialAmount, uint256 remainingAmount, uint8 purpose, uint256 referenceId, uint8 status))',
  'function createMaterialLock(address owner, uint256 tokenId, uint256 amount, uint8 purpose, uint256 referenceId) returns (uint256 lockId)',
  'function releaseMaterialLock(uint256 lockId, uint256 amount)',
  'function consumeMaterialLock(uint256 lockId, address to, uint256 amount)',

  // ── 발행 · 소각 · 이전 ────────────────────────────────
  // amount 는 gram 정수다 (정책 03절). tokenId 는 인자가 아니라 **반환값** 이다.
  'function mintMaterial(address to, uint256 amount, uint8 rootOrigin, string metadataUri, bytes32 evidenceHash) returns (uint256 tokenId)',
  'function burnMaterial(address from, uint256 tokenId, uint256 amount, uint8 reason)',
  'function safeTransferFrom(address from, address to, uint256 id, uint256 value, bytes data)',
  'function setApprovalForAll(address operator, bool approved)',
  'function isApprovedForAll(address account, address operator) view returns (bool)',

  // ── 검증 기록 ────────────────────────────────────────
  'function verifySource(uint256 tokenId, bytes32 evidenceHash)',
  'function verifyTransaction(uint256 tokenId, uint256 tradeId, bytes32 evidenceHash)',
  'function verifyProcessing(uint256 tokenId, uint256 processingJobId, bytes32 evidenceHash)',
  'function verificationRecord(uint256 tokenId, uint8 state) view returns (tuple(address signer, bytes32 evidenceHash, uint256 referenceId, uint64 verifiedAt))',
  'function verifyEvidence(uint256 tokenId, bytes32 hashToCheck) view returns (bool)',

  // ── 권한 ─────────────────────────────────────────────
  // 화면에서 "이 사용자가 발행할 수 있는가" 를 미리 확인해 버튼을 잠글 때 쓴다.
  'function hasRole(bytes32 role, address account) view returns (bool)',
  'function SUPPLIER_ROLE() view returns (bytes32)',
  'function BRAND_ROLE() view returns (bytes32)',
  'function PROCESSOR_ROLE() view returns (bytes32)',

  // ── 이벤트 ───────────────────────────────────────────
  // mintMaterial 의 반환값은 트랜잭션 영수증에서 직접 못 읽는다.
  // tokenId 는 이 이벤트에서 꺼낸다.
  'event MaterialMinted(address indexed operator, address indexed to, uint256 indexed tokenId, uint256 amount, uint8 rootOrigin, uint8 generationType, string metadataUri, bytes32 evidenceHash)',
  'event MaterialBurned(address indexed operator, address indexed from, uint256 indexed tokenId, uint256 amount, uint8 reason, uint256 remainingSupply)',
  'event MaterialLockCreated(uint256 indexed lockId, address indexed operator, address indexed owner, uint256 tokenId, uint256 amount, uint8 purpose, uint256 referenceId)',
  'event MaterialLockReleased(uint256 indexed lockId, uint256 amount, uint256 remainingAmount)',
  'event MaterialLockConsumed(uint256 indexed lockId, address indexed to, uint256 amount, uint256 remainingAmount)',
  'event SourceVerified(uint256 indexed tokenId, address indexed signer, bytes32 evidenceHash)',
  'event TransactionVerified(uint256 indexed tokenId, uint256 indexed tradeId, address indexed signer, bytes32 evidenceHash)',
  'event ProcessingVerified(uint256 indexed tokenId, uint256 indexed processingJobId, address indexed signer, bytes32 evidenceHash)',
] as const
