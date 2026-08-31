// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ERC1155} from "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";
//MaterialToken - erc1155. 원단 무게 토큰화의 기능
//accesscontrol - 접근 제한
contract MaterialToken is ERC1155, AccessControl {
    bytes32 public constant SUPPLIER_ROLE = keccak256("SUPPLIER_ROLE");
    bytes32 public constant PROCESSOR_ROLE = keccak256("PROCESSOR_ROLE");
    bytes32 public constant BRAND_ROLE = keccak256("BRAND_ROLE");
    bytes32 public constant VERIFIER_ROLE = keccak256("VERIFIER_ROLE");
    //Marketplace, Escrow 컨트랙트에 나중에 부여할 상태 변경 권한
    bytes32 public constant STATUS_MANAGER_ROLE = keccak256("STATUS_MANAGER_ROLE");

    //소재의 생애 주기
    enum MaterialStatus {
        NONE,        //0 등록되지 않음
        REGISTERED,  //1 발행됨, 검증 전
        VERIFIED,    //2 검증 완료, 거래 가능
        LISTED,      //3 판매 등록됨
        IN_ESCROW,   //4 거래 진행중, 이동 금지
        PROCESSED    //5 전량 가공 투입되어 소진
    }

    //소각 사유. 표준 이벤트만으로는 왜 없어졌는지 알 수 없어 함께 기록한다.
    enum BurnReason {
        PROCESSED,   //0 가공에 투입
        DISCARDED    //1 폐기
    }

    //토큰 기본 정보
    struct MaterialInfo {
        string metadataUri;
        bytes32 documentHash;
        uint256 totalMinted;
        uint256 totalBurned;
        MaterialStatus status;
        bool exists;
    }

    mapping(uint256 tokenId => MaterialInfo) private _materials;

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

    constructor(string memory baseUri, address admin) ERC1155(baseUri) {
        require(admin != address(0), "MaterialToken: admin is zero address");

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(SUPPLIER_ROLE, admin);
        _grantRole(PROCESSOR_ROLE, admin);
        _grantRole(BRAND_ROLE, admin);
        _grantRole(VERIFIER_ROLE, admin);
        _grantRole(STATUS_MANAGER_ROLE, admin);
    }

    //소재 토큰 발행
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
                material.status == MaterialStatus.REGISTERED ||
                    material.status == MaterialStatus.VERIFIED,
                "MaterialToken: status does not allow minting"
            );
        }

        material.totalMinted += amount;

        _mint(to, tokenId, amount, "");

        emit MaterialMinted(_msgSender(), to, tokenId, amount, metadataUri, documentHash);
    }

    //검증자가 소재를 승인한다. 승인 전에는 거래 단계로 넘어갈 수 없다.
    function verifyMaterial(uint256 tokenId) external onlyRole(VERIFIER_ROLE) {
        MaterialInfo storage material = _materials[tokenId];

        require(material.exists, "MaterialToken: material does not exist");
        require(
            material.status == MaterialStatus.REGISTERED,
            "MaterialToken: material is not in registered status"
        );

        _setStatus(tokenId, MaterialStatus.VERIFIED);
    }

    //Marketplace, Escrow 가 거래 진행 상태를 반영한다.
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

    //소재 소각. 가공 흐름에서 부모 소재를 없앨 때 사용한다.
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

    //현재 유통중인 수량
    function remainingSupply(uint256 tokenId) public view returns (uint256) {
        MaterialInfo storage material = _materials[tokenId];
        return material.totalMinted - material.totalBurned;
    }

    //오프체인 증빙 파일이 위조되지 않았는지 확인한다.
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
        if (from == MaterialStatus.VERIFIED) {
            return to == MaterialStatus.LISTED;
        }

        if (from == MaterialStatus.LISTED) {
            //판매 취소 또는 거래 시작
            return to == MaterialStatus.VERIFIED || to == MaterialStatus.IN_ESCROW;
        }

        if (from == MaterialStatus.IN_ESCROW) {
            //거래 완료 또는 취소
            return to == MaterialStatus.VERIFIED || to == MaterialStatus.LISTED;
        }

        //REGISTERED 는 verifyMaterial 로만, PROCESSED 는 burnMaterial 로만 진입한다.
        return false;
    }

    //거래중이거나 소진된 소재는 이동할 수 없다.
    function _update(
        address from,
        address to,
        uint256[] memory ids,
        uint256[] memory values
    ) internal override {
        //발행(from=0)과 소각(to=0)은 각 함수에서 이미 검사한다.
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
