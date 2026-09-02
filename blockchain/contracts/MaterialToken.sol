// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ERC1155} from "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";

/// @title MaterialToken
/// @notice 원단과 폐소재의 수량을 gram 단위로 관리하는 ERC-1155 토큰이다.
/// @dev 하나의 tokenId는 하나의 Material을 뜻하며, 지갑별 balance가 해당 소재의 보유량이다.
///      AccessControl로 발행·상태 변경·수량 잠금 권한을 분리한다.
contract MaterialToken is ERC1155, AccessControl {
    // 최초 Material 발행 권한. 현재 mintMaterial은 공급자 역할만 호출할 수 있다.
    bytes32 public constant SUPPLIER_ROLE = keccak256("SUPPLIER_ROLE");
    // 추후 Processing 결과 등록 및 Child Material 생성에 사용할 역할이다.
    bytes32 public constant PROCESSOR_ROLE = keccak256("PROCESSOR_ROLE");
    // 추후 Processing 승인과 Production 확정에 사용할 역할이다.
    bytes32 public constant BRAND_ROLE = keccak256("BRAND_ROLE");
    // Marketplace, Escrow 컨트랙트가 Material의 거래 상태를 변경할 때 사용한다.
    bytes32 public constant STATUS_MANAGER_ROLE = keccak256("STATUS_MANAGER_ROLE");
    // Marketplace 등 작업 컨트랙트가 특정 소유자의 일부 수량을 잠글 때 사용한다.
    bytes32 public constant MATERIAL_LOCK_MANAGER_ROLE =
        keccak256("MATERIAL_LOCK_MANAGER_ROLE");

    /// @notice Material 전체에 적용되는 현재 PoC 상태다.
    /// @dev 수량별 사용 가능 여부는 이 상태가 아니라 _lockedBalances로 별도 관리한다.
    enum MaterialStatus {
        NONE,        //0 등록되지 않음
        REGISTERED,  //1 발행됨, 거래 가능
        LISTED,      //2 판매 등록됨
        IN_ESCROW,   //3 거래 진행중, 이동 금지
        PROCESSED    //4 전량 가공 투입되어 소진
    }

    /// @notice Balance를 소각한 목적을 MaterialBurned Event에 남기기 위한 구분값이다.
    enum BurnReason {
        PROCESSED,   //0 가공에 투입
        DISCARDED    //1 폐기
    }

    /// @notice tokenId별 변경 불가능한 메타데이터와 누적 발행·소각 정보를 보관한다.
    struct MaterialInfo {
        string metadataUri;
        bytes32 documentHash;
        uint256 totalMinted;
        uint256 totalBurned;
        MaterialStatus status;
        bool exists;
    }

    // tokenId 단위 정보. 지갑별 실제 보유량은 ERC-1155 balanceOf가 기준이다.
    mapping(uint256 tokenId => MaterialInfo) private _materials;
    // owner가 보유한 tokenId 중 Marketplace 등의 작업에 예약되어 전송할 수 없는 수량이다.
    mapping(address owner => mapping(uint256 tokenId => uint256 amount)) private _lockedBalances;

    event MaterialMinted(
        address indexed operator,
        address indexed to,
        uint256 indexed tokenId,
        uint256 amount,
        string metadataUri,
        bytes32 documentHash
    );

    event MaterialStatusChanged(
        address indexed operator,
        uint256 indexed tokenId,
        MaterialStatus previousStatus,
        MaterialStatus newStatus
    );

    event MaterialBurned(
        address indexed operator,
        address indexed from,
        uint256 indexed tokenId,
        uint256 amount,
        BurnReason reason,
        uint256 remainingSupply
    );

    event MaterialLocked(
        address indexed operator,
        address indexed owner,
        uint256 indexed tokenId,
        uint256 amount
    );

    event MaterialUnlocked(
        address indexed operator,
        address indexed owner,
        uint256 indexed tokenId,
        uint256 amount
    );

    constructor(string memory baseUri, address admin) ERC1155(baseUri) {
        require(admin != address(0), "MaterialToken: admin is zero address");

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(SUPPLIER_ROLE, admin);
        _grantRole(PROCESSOR_ROLE, admin);
        _grantRole(BRAND_ROLE, admin);
        _grantRole(STATUS_MANAGER_ROLE, admin);
        _grantRole(MATERIAL_LOCK_MANAGER_ROLE, admin);
    }

    /// @notice owner의 Material 일부를 다른 용도로 사용할 수 없도록 예약한다.
    /// @dev 잠금 후에도 ERC-1155 소유자는 바뀌지 않으며 availableBalance만 감소한다.
    /// @param owner 잠글 Material을 보유한 지갑
    /// @param tokenId 잠글 Material ID
    /// @param amount 잠글 수량(gram)
    function lockMaterial(address owner, uint256 tokenId, uint256 amount)
        external
        onlyRole(MATERIAL_LOCK_MANAGER_ROLE)
    {
        require(owner != address(0), "MaterialToken: owner is zero address");
        require(amount > 0, "MaterialToken: amount is zero");
        require(_materials[tokenId].exists, "MaterialToken: material does not exist");
        require(
            availableBalance(owner, tokenId) >= amount,
            "MaterialToken: insufficient available balance"
        );

        _lockedBalances[owner][tokenId] += amount;

        emit MaterialLocked(_msgSender(), owner, tokenId, amount);
    }

    /// @notice 이전에 예약한 Material 수량을 다시 사용할 수 있도록 해제한다.
    /// @param owner 잠금이 기록된 지갑
    /// @param tokenId 잠금을 해제할 Material ID
    /// @param amount 해제할 수량(gram)
    function unlockMaterial(address owner, uint256 tokenId, uint256 amount)
        external
        onlyRole(MATERIAL_LOCK_MANAGER_ROLE)
    {
        require(owner != address(0), "MaterialToken: owner is zero address");
        require(amount > 0, "MaterialToken: amount is zero");
        require(
            _lockedBalances[owner][tokenId] >= amount,
            "MaterialToken: insufficient locked balance"
        );

        _lockedBalances[owner][tokenId] -= amount;

        emit MaterialUnlocked(_msgSender(), owner, tokenId, amount);
    }

    /// @notice 공급자가 신규 Material을 발행하고 최초 메타데이터와 증빙 해시를 저장한다.
    /// @dev 동일 tokenId 추가 발행 시 최초 metadataUri와 documentHash를 변경할 수 없다.
    /// @param to 발행 수량을 받을 지갑
    /// @param tokenId Material ID
    /// @param amount 발행 수량(gram)
    /// @param metadataUri 상세 Metadata 위치
    /// @param documentHash 원본 증빙 파일의 해시
    function mintMaterial(
        address to,
        uint256 tokenId,
        uint256 amount,
        string calldata metadataUri,
        bytes32 documentHash
    ) external onlyRole(SUPPLIER_ROLE) {//supplier만 발행 가능
        require(to != address(0), "MaterialToken: to is zero address");
        require(amount > 0, "MaterialToken: amount is zero");
        require(documentHash != bytes32(0), "MaterialToken: documentHash is empty");

        MaterialInfo storage material = _materials[tokenId];
        if (!material.exists) {//처음 정한 메타데이터와 증빙 해시의 무결성 보장
            material.metadataUri = metadataUri;
            material.documentHash = documentHash;
            material.exists = true;

            _setStatus(tokenId, MaterialStatus.REGISTERED);
        } else {
            require(
                keccak256(bytes(material.metadataUri)) == keccak256(bytes(metadataUri)),
                "MaterialToken: metadata mismatch"
            );
            require(
                material.documentHash == documentHash,
                "MaterialToken: documentHash mismatch"
            );
            //거래중이거나 소진된 소재에는 추가 발행할 수 없다.
            require(
                material.status == MaterialStatus.REGISTERED,
                "MaterialToken: status does not allow minting"
            );
        }

        material.totalMinted += amount;

        _mint(to, tokenId, amount, "");

        emit MaterialMinted(_msgSender(), to, tokenId, amount, metadataUri, documentHash);
    }

    /// @notice 권한을 받은 작업 컨트랙트가 Material의 거래 상태를 변경한다.
    /// @dev 허용되는 전이는 _isValidTransition에서 제한한다.
    function setMaterialStatus(uint256 tokenId, MaterialStatus newStatus)
        external
        onlyRole(STATUS_MANAGER_ROLE)
    {
        MaterialInfo storage material = _materials[tokenId];

        require(material.exists, "MaterialToken: material does not exist");
        require(
            _isValidTransition(material.status, newStatus),
            "MaterialToken: invalid status transition"
        );

        _setStatus(tokenId, newStatus);
    }

    /// @notice 보유자의 Material 수량을 소각하고 누적 소각량을 기록한다.
    /// @dev owner 또는 위임받은 operator만 호출할 수 있고 잠긴 수량은 소각할 수 없다.
    ///      현재는 범용 함수이며 추후 Processing/Production 흐름에서 호출하도록 확장한다.
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
            material.status != MaterialStatus.IN_ESCROW,
            "MaterialToken: material is in escrow"
        );

        material.totalBurned += amount;

        _burn(from, tokenId, amount);

        uint256 remaining = material.totalMinted - material.totalBurned;

        //전량 가공에 투입되면 소진 상태로 넘긴다.
        if (remaining == 0 && reason == BurnReason.PROCESSED) {
            _setStatus(tokenId, MaterialStatus.PROCESSED);
        }

        emit MaterialBurned(_msgSender(), from, tokenId, amount, reason, remaining);
    }

    function materialInfo(uint256 tokenId) external view returns (
        string memory metadataUri,
        bytes32 documentHash,
        uint256 totalMinted,
        uint256 totalBurned,
        MaterialStatus status,
        bool exists
    ) {
        MaterialInfo storage material = _materials[tokenId];
        return (
            material.metadataUri,
            material.documentHash,
            material.totalMinted,
            material.totalBurned,
            material.status,
            material.exists
        );
    }

    /// @notice 전체 발행량에서 전체 소각량을 뺀 현재 유통 수량을 반환한다.
    function remainingSupply(uint256 tokenId) public view returns (uint256) {
        MaterialInfo storage material = _materials[tokenId];
        return material.totalMinted - material.totalBurned;
    }

    /// @notice 특정 지갑의 잠긴 Material 수량을 반환한다.
    function lockedBalanceOf(address owner, uint256 tokenId) public view returns (uint256) {
        return _lockedBalances[owner][tokenId];
    }

    /// @notice 특정 지갑이 전송·소각에 사용할 수 있는 Material 수량을 반환한다.
    /// @dev ERC-1155 balance - locked balance로 계산한다.
    function availableBalance(address owner, uint256 tokenId) public view returns (uint256) {
        return balanceOf(owner, tokenId) - _lockedBalances[owner][tokenId];
    }

    /// @notice 입력한 파일 해시가 최초 등록한 증빙 해시와 같은지 확인한다.
    /// @dev 독립 검증자의 승인이 아니라 단순 무결성 비교 함수다.
    function verifyDocument(uint256 tokenId, bytes32 hashToCheck)
        external
        view
        returns (bool)
    {
        MaterialInfo storage material = _materials[tokenId];
        return material.exists && material.documentHash == hashToCheck;
    }

    function _setStatus(uint256 tokenId, MaterialStatus newStatus) private {
        MaterialStatus previousStatus = _materials[tokenId].status;
        _materials[tokenId].status = newStatus;

        emit MaterialStatusChanged(_msgSender(), tokenId, previousStatus, newStatus);
    }

    function _isValidTransition(MaterialStatus from, MaterialStatus to)
        private
        pure
        returns (bool)
    {
        if (from == MaterialStatus.REGISTERED) {
            return to == MaterialStatus.LISTED;
        }

        if (from == MaterialStatus.LISTED) {
            //판매 취소 또는 거래 시작
            return to == MaterialStatus.REGISTERED || to == MaterialStatus.IN_ESCROW;
        }

        if (from == MaterialStatus.IN_ESCROW) {
            //거래 완료 또는 취소
            return to == MaterialStatus.REGISTERED || to == MaterialStatus.LISTED;
        }

        //PROCESSED 는 burnMaterial 로만 진입한다.
        return false;
    }

    /// @dev ERC-1155의 발행·전송·소각 공통 경로를 가로채 가용 수량과 상태를 검증한다.
    ///      from이 zero address인 발행은 잠금 검사를 생략하고, 전송과 소각은 잠긴 수량을 침범할 수 없다.
    function _update(
        address from,
        address to,
        uint256[] memory ids,
        uint256[] memory values
    ) internal override {
        //발행(from=0)은 잠금 검사가 필요 없다. 전송과 소각은 잠긴 수량을 침범할 수 없다.
        if (from != address(0)) {
            for (uint256 i = 0; i < ids.length; i++) {
                require(
                    availableBalance(from, ids[i]) >= values[i],
                    "MaterialToken: amount exceeds available balance"
                );
            }
        }

        if (from != address(0) && to != address(0)) {
            for (uint256 i = 0; i < ids.length; i++) {
                MaterialStatus status = _materials[ids[i]].status;

                require(
                    status != MaterialStatus.IN_ESCROW,
                    "MaterialToken: material is in escrow"
                );
                require(
                    status != MaterialStatus.PROCESSED,
                    "MaterialToken: material is processed"
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
