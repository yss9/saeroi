// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ERC1155} from "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";

/// @title MaterialToken
/// @notice 소재 배치와 그 수량을 gram 단위로 관리하는 ERC-1155 토큰이다.
/// @dev DB의 material.id와 온체인 tokenId는 별도 식별자다. tokenId는 이 컨트랙트가 순서대로 발급하며,
///      백엔드는 MaterialMinted 이벤트를 읽어 DB의 chain_id, contract_address, token_id를 갱신한다.
contract MaterialToken is ERC1155, AccessControl {
    bytes32 public constant SUPPLIER_ROLE = keccak256("SUPPLIER_ROLE");
    bytes32 public constant PROCESSOR_ROLE = keccak256("PROCESSOR_ROLE");
    bytes32 public constant BRAND_ROLE = keccak256("BRAND_ROLE");
    bytes32 public constant MATERIAL_LOCK_MANAGER_ROLE =
        keccak256("MATERIAL_LOCK_MANAGER_ROLE");

    /// @notice 소재가 처음 생성된 원천이다.
    enum RootOrigin {
        VIRGIN,
        WASTE,
        RECOVERED
    }

    /// @notice 소재가 플랫폼에서 생성된 방식이다.
    /// @dev 현재 mintMaterial은 INITIAL만 만든다. 나머지는 Processing/Production 구현에서 사용한다.
    enum GenerationType {
        INITIAL,
        PROCESSED,
        PRODUCTION_SCRAP
    }

    /// @notice 소재 증빙의 단계별 확인 상태다.
    /// @dev 독립 Verifier는 두지 않는다. 각 단계의 실제 업무 주체가 자신의 지갑으로 직접 호출한다.
    enum VerificationState {
        REGISTERED,
        SOURCE_VERIFIED,
        TRANSACTION_VERIFIED,
        PROCESSING_VERIFIED,
        PRODUCT_LINKED
    }

    /// @notice 거래 상태와 분리된 소재 자체의 수명주기다.
    enum MaterialStatus {
        NONE,
        ACTIVE,
        DEPLETED
    }

    enum BurnReason {
        PROCESSING,
        PRODUCTION,
        DISCARDED
    }

    enum LockPurpose {
        MARKETPLACE,
        PROCESSING,
        PRODUCTION
    }

    enum LockStatus {
        NONE,
        ACTIVE,
        RELEASED,
        CONSUMED
    }

    struct MaterialInfo {
        string metadataUri;
        bytes32 evidenceHash;
        uint256 totalMinted;
        uint256 totalBurned;
        RootOrigin rootOrigin;
        GenerationType generationType;
        VerificationState verificationState;
        MaterialStatus status;
        address registrant;
        bool exists;
    }

    /// @notice 각 검증 단계에서 누가 어떤 근거로 서명했는지 보관한다.
    struct VerificationRecord {
        address signer;
        bytes32 evidenceHash;
        uint256 referenceId;
        uint64 verifiedAt;
    }

    /// @notice 하나의 판매글이나 작업이 점유한 소재 수량을 독립적으로 추적한다.
    struct MaterialLock {
        uint256 id;
        address operator;
        address owner;
        uint256 tokenId;
        uint256 initialAmount;
        uint256 remainingAmount;
        LockPurpose purpose;
        uint256 referenceId;
        LockStatus status;
    }

    uint256 public nextMaterialId = 1;
    uint256 public nextLockId = 1;

    mapping(uint256 tokenId => MaterialInfo) private _materials;
    mapping(uint256 lockId => MaterialLock) private _materialLocks;
    mapping(uint256 tokenId => mapping(VerificationState state => VerificationRecord))
        private _verificationRecords;
    mapping(address owner => mapping(uint256 tokenId => uint256 amount))
        private _lockedBalances;

    event MaterialMinted(
        address indexed operator,
        address indexed to,
        uint256 indexed tokenId,
        uint256 amount,
        RootOrigin rootOrigin,
        GenerationType generationType,
        string metadataUri,
        bytes32 evidenceHash
    );
    event MaterialBurned(
        address indexed operator,
        address indexed from,
        uint256 indexed tokenId,
        uint256 amount,
        BurnReason reason,
        uint256 remainingSupply
    );
    event MaterialDepleted(uint256 indexed tokenId);
    event SourceVerified(
        uint256 indexed tokenId,
        address indexed signer,
        bytes32 evidenceHash
    );
    event TransactionVerified(
        uint256 indexed tokenId,
        uint256 indexed tradeId,
        address indexed signer,
        bytes32 evidenceHash
    );
    event ProcessingVerified(
        uint256 indexed tokenId,
        uint256 indexed processingJobId,
        address indexed signer,
        bytes32 evidenceHash
    );
    event MaterialLockCreated(
        uint256 indexed lockId,
        address indexed operator,
        address indexed owner,
        uint256 tokenId,
        uint256 amount,
        LockPurpose purpose,
        uint256 referenceId
    );
    event MaterialLockReleased(
        uint256 indexed lockId,
        uint256 amount,
        uint256 remainingAmount
    );
    event MaterialLockConsumed(
        uint256 indexed lockId,
        address indexed to,
        uint256 amount,
        uint256 remainingAmount
    );

    constructor(string memory baseUri, address admin) ERC1155(baseUri) {
        require(admin != address(0), "MaterialToken: admin is zero address");

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(SUPPLIER_ROLE, admin);
        _grantRole(PROCESSOR_ROLE, admin);
        _grantRole(BRAND_ROLE, admin);
        _grantRole(MATERIAL_LOCK_MANAGER_ROLE, admin);
    }

    /// @notice 공급자가 최초 소재를 발행한다.
    /// @param to 발행 수량을 받을 지갑
    /// @param amount 발행 수량(gram)
    /// @param rootOrigin 원재료의 최초 원천
    /// @param metadataUri 상세 메타데이터 위치
    /// @param evidenceHash 증빙 manifest의 SHA-256 값(bytes32)
    /// @return tokenId 컨트랙트가 새로 발급한 소재 ID
    function mintMaterial(
        address to,
        uint256 amount,
        RootOrigin rootOrigin,
        string calldata metadataUri,
        bytes32 evidenceHash
    ) external onlyRole(SUPPLIER_ROLE) returns (uint256 tokenId) {
        require(to != address(0), "MaterialToken: to is zero address");
        require(amount > 0, "MaterialToken: amount is zero");
        require(evidenceHash != bytes32(0), "MaterialToken: evidenceHash is empty");

        tokenId = nextMaterialId++;
        _materials[tokenId] = MaterialInfo({
            metadataUri: metadataUri,
            evidenceHash: evidenceHash,
            totalMinted: amount,
            totalBurned: 0,
            rootOrigin: rootOrigin,
            generationType: GenerationType.INITIAL,
            verificationState: VerificationState.REGISTERED,
            status: MaterialStatus.ACTIVE,
            registrant: _msgSender(),
            exists: true
        });

        _mint(to, tokenId, amount, "");

        emit MaterialMinted(
            _msgSender(),
            to,
            tokenId,
            amount,
            rootOrigin,
            GenerationType.INITIAL,
            metadataUri,
            evidenceHash
        );
    }

    /// @notice 최초 등록 Supplier가 발행 당시 증빙과 일치함을 직접 서명한다.
    function verifySource(uint256 tokenId, bytes32 evidenceHash)
        external
        onlyRole(SUPPLIER_ROLE)
    {
        MaterialInfo storage material = _materials[tokenId];
        require(material.exists, "MaterialToken: material does not exist");
        require(material.registrant == _msgSender(), "MaterialToken: caller is not registrant");
        require(
            material.verificationState == VerificationState.REGISTERED,
            "MaterialToken: invalid verification state"
        );
        require(material.evidenceHash == evidenceHash, "MaterialToken: evidenceHash mismatch");

        material.verificationState = VerificationState.SOURCE_VERIFIED;
        _storeVerification(
            tokenId,
            VerificationState.SOURCE_VERIFIED,
            evidenceHash,
            0
        );
        emit SourceVerified(tokenId, _msgSender(), evidenceHash);
    }

    /// @notice 소재를 인수한 Brand가 해당 거래의 증빙을 자신의 지갑으로 서명한다.
    function verifyTransaction(
        uint256 tokenId,
        uint256 tradeId,
        bytes32 evidenceHash
    ) external onlyRole(BRAND_ROLE) {
        MaterialInfo storage material = _materials[tokenId];
        require(material.exists, "MaterialToken: material does not exist");
        require(
            material.verificationState == VerificationState.SOURCE_VERIFIED,
            "MaterialToken: invalid verification state"
        );
        require(balanceOf(_msgSender(), tokenId) > 0, "MaterialToken: brand has no material balance");
        require(tradeId > 0, "MaterialToken: tradeId is zero");
        require(evidenceHash != bytes32(0), "MaterialToken: evidenceHash is empty");

        material.verificationState = VerificationState.TRANSACTION_VERIFIED;
        _storeVerification(
            tokenId,
            VerificationState.TRANSACTION_VERIFIED,
            evidenceHash,
            tradeId
        );
        emit TransactionVerified(tokenId, tradeId, _msgSender(), evidenceHash);
    }

    /// @notice Processor가 가공 결과 manifest를 자신의 지갑으로 서명한다.
    /// @dev Child Material 생성과 Brand 승인 흐름은 Processing 구현 단계에서 별도로 추가한다.
    function verifyProcessing(
        uint256 tokenId,
        uint256 processingJobId,
        bytes32 evidenceHash
    ) external onlyRole(PROCESSOR_ROLE) {
        MaterialInfo storage material = _materials[tokenId];
        require(material.exists, "MaterialToken: material does not exist");
        require(
            material.verificationState == VerificationState.TRANSACTION_VERIFIED,
            "MaterialToken: invalid verification state"
        );
        require(processingJobId > 0, "MaterialToken: processingJobId is zero");
        require(evidenceHash != bytes32(0), "MaterialToken: evidenceHash is empty");

        material.verificationState = VerificationState.PROCESSING_VERIFIED;
        _storeVerification(
            tokenId,
            VerificationState.PROCESSING_VERIFIED,
            evidenceHash,
            processingJobId
        );
        emit ProcessingVerified(
            tokenId,
            processingJobId,
            _msgSender(),
            evidenceHash
        );
    }

    /// @notice 작업 단위의 잠금 레코드를 만들고 owner의 가용 수량을 줄인다.
    /// @dev 잠금을 만든 operator만 같은 잠금을 해제하거나 소비할 수 있다.
    function createMaterialLock(
        address owner,
        uint256 tokenId,
        uint256 amount,
        LockPurpose purpose,
        uint256 referenceId
    ) external onlyRole(MATERIAL_LOCK_MANAGER_ROLE) returns (uint256 lockId) {
        require(owner != address(0), "MaterialToken: owner is zero address");
        require(amount > 0, "MaterialToken: amount is zero");
        require(referenceId > 0, "MaterialToken: referenceId is zero");
        require(_materials[tokenId].exists, "MaterialToken: material does not exist");
        require(
            _materials[tokenId].status == MaterialStatus.ACTIVE,
            "MaterialToken: material is not active"
        );
        require(
            availableBalance(owner, tokenId) >= amount,
            "MaterialToken: insufficient available balance"
        );

        lockId = nextLockId++;
        _materialLocks[lockId] = MaterialLock({
            id: lockId,
            operator: _msgSender(),
            owner: owner,
            tokenId: tokenId,
            initialAmount: amount,
            remainingAmount: amount,
            purpose: purpose,
            referenceId: referenceId,
            status: LockStatus.ACTIVE
        });
        _lockedBalances[owner][tokenId] += amount;

        emit MaterialLockCreated(
            lockId,
            _msgSender(),
            owner,
            tokenId,
            amount,
            purpose,
            referenceId
        );
    }

    /// @notice 잠긴 수량 일부 또는 전부를 다시 사용 가능한 잔액으로 돌린다.
    function releaseMaterialLock(uint256 lockId, uint256 amount)
        external
        onlyRole(MATERIAL_LOCK_MANAGER_ROLE)
    {
        MaterialLock storage materialLock = _activeOwnedLock(lockId);
        require(amount > 0, "MaterialToken: amount is zero");
        require(
            materialLock.remainingAmount >= amount,
            "MaterialToken: insufficient lock amount"
        );

        materialLock.remainingAmount -= amount;
        _lockedBalances[materialLock.owner][materialLock.tokenId] -= amount;
        if (materialLock.remainingAmount == 0) {
            materialLock.status = LockStatus.RELEASED;
        }

        emit MaterialLockReleased(lockId, amount, materialLock.remainingAmount);
    }

    /// @notice 잠긴 수량을 차감하면서 수령자에게 소재 소유권을 이전한다.
    function consumeMaterialLock(uint256 lockId, address to, uint256 amount)
        external
        onlyRole(MATERIAL_LOCK_MANAGER_ROLE)
    {
        require(to != address(0), "MaterialToken: to is zero address");
        require(amount > 0, "MaterialToken: amount is zero");

        MaterialLock storage materialLock = _activeOwnedLock(lockId);
        require(
            materialLock.remainingAmount >= amount,
            "MaterialToken: insufficient lock amount"
        );

        materialLock.remainingAmount -= amount;
        _lockedBalances[materialLock.owner][materialLock.tokenId] -= amount;
        if (materialLock.remainingAmount == 0) {
            materialLock.status = LockStatus.CONSUMED;
        }

        _safeTransferFrom(materialLock.owner, to, materialLock.tokenId, amount, "");

        emit MaterialLockConsumed(lockId, to, amount, materialLock.remainingAmount);
    }

    /// @notice 보유자의 가용 소재를 소각하고 누적 소각량을 기록한다.
    function burnMaterial(
        address from,
        uint256 tokenId,
        uint256 amount,
        BurnReason reason
    ) external {
        require(
            from == _msgSender() || isApprovedForAll(from, _msgSender()),
            "MaterialToken: caller is not owner nor approved"
        );
        require(amount > 0, "MaterialToken: amount is zero");

        MaterialInfo storage material = _materials[tokenId];
        require(material.exists, "MaterialToken: material does not exist");
        require(
            material.status == MaterialStatus.ACTIVE,
            "MaterialToken: material is not active"
        );

        material.totalBurned += amount;
        _burn(from, tokenId, amount);

        uint256 remaining = material.totalMinted - material.totalBurned;
        if (remaining == 0) {
            material.status = MaterialStatus.DEPLETED;
            emit MaterialDepleted(tokenId);
        }

        emit MaterialBurned(_msgSender(), from, tokenId, amount, reason, remaining);
    }

    function materialInfo(uint256 tokenId)
        external
        view
        returns (MaterialInfo memory)
    {
        return _materials[tokenId];
    }

    function materialLockInfo(uint256 lockId)
        external
        view
        returns (MaterialLock memory)
    {
        return _materialLocks[lockId];
    }

    function verificationRecord(
        uint256 tokenId,
        VerificationState state
    ) external view returns (VerificationRecord memory) {
        return _verificationRecords[tokenId][state];
    }

    function remainingSupply(uint256 tokenId) public view returns (uint256) {
        MaterialInfo storage material = _materials[tokenId];
        return material.totalMinted - material.totalBurned;
    }

    function lockedBalanceOf(address owner, uint256 tokenId)
        public
        view
        returns (uint256)
    {
        return _lockedBalances[owner][tokenId];
    }

    function availableBalance(address owner, uint256 tokenId)
        public
        view
        returns (uint256)
    {
        return balanceOf(owner, tokenId) - _lockedBalances[owner][tokenId];
    }

    /// @notice 전달받은 SHA-256 값이 발행 당시 저장한 증빙 해시와 같은지 확인한다.
    /// @dev Solidity에서 파일을 해싱하지 않는다. 백엔드가 계산한 32-byte 값을 비교한다.
    function verifyEvidence(uint256 tokenId, bytes32 hashToCheck)
        external
        view
        returns (bool)
    {
        MaterialInfo storage material = _materials[tokenId];
        return material.exists && material.evidenceHash == hashToCheck;
    }

    function _activeOwnedLock(uint256 lockId)
        private
        view
        returns (MaterialLock storage materialLock)
    {
        materialLock = _materialLocks[lockId];
        require(materialLock.status == LockStatus.ACTIVE, "MaterialToken: lock is not active");
        require(materialLock.operator == _msgSender(), "MaterialToken: caller is not lock operator");
    }

    function _storeVerification(
        uint256 tokenId,
        VerificationState state,
        bytes32 evidenceHash,
        uint256 referenceId
    ) private {
        _verificationRecords[tokenId][state] = VerificationRecord({
            signer: _msgSender(),
            evidenceHash: evidenceHash,
            referenceId: referenceId,
            verifiedAt: uint64(block.timestamp)
        });
    }

    /// @dev 발행 외의 전송과 소각에서 잠긴 수량을 침범하지 못하게 한다.
    function _update(
        address from,
        address to,
        uint256[] memory ids,
        uint256[] memory values
    ) internal override {
        if (from != address(0)) {
            for (uint256 i = 0; i < ids.length; i++) {
                // safeBatchTransferFrom에 같은 tokenId가 반복되어도 합계가 가용량을 넘지 못하게 한다.
                uint256 requestedAmount;
                for (uint256 j = 0; j <= i; j++) {
                    if (ids[j] == ids[i]) {
                        requestedAmount += values[j];
                    }
                }
                require(
                    availableBalance(from, ids[i]) >= requestedAmount,
                    "MaterialToken: amount exceeds available balance"
                );
            }
        }

        super._update(from, to, ids, values);
    }

    function supportsInterface(bytes4 interfaceId)
        public
        view
        override(ERC1155, AccessControl)
        returns (bool)
    {
        return super.supportsInterface(interfaceId);
    }

    function uri(uint256 tokenId) public view override returns (string memory) {
        return _materials[tokenId].metadataUri;
    }
}
