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

    //토큰 기본 정보
    struct MaterialInfo {
        string metadataUri;
        uint256 totalMinted;
        bool exists;
    }

    mapping(uint256 tokenId => MaterialInfo) private _materials;

    event MaterialMinted(
        address indexed operator,
        address indexed to,
        uint256 indexed tokenId,
        uint256 amount,
        string metadataUri
    );

    constructor(string memory baseUri, address admin) ERC1155(baseUri) {
        require(admin != address(0), "MaterialToken: admin is zero address");

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(SUPPLIER_ROLE, admin);
        _grantRole(PROCESSOR_ROLE, admin);
        _grantRole(BRAND_ROLE, admin);
        _grantRole(VERIFIER_ROLE, admin);
    }
    //소재 토큰 발행
    function mintMaterial(
        address to,
        uint256 tokenId,
        uint256 amount,
        string calldata metadataUri
    ) external onlyRole(SUPPLIER_ROLE) {//supplier만 발행 가능
        require(to != address(0), "MaterialToken: to is zero address");
        require(amount > 0, "MaterialToken: amount is zero");

        MaterialInfo storage material = _materials[tokenId];
        if (!material.exists) {//처음 정한 메타데이터의 무결성 보장
            material.metadataUri = metadataUri;
            material.exists = true;
        } else {
            require(
                keccak256(bytes(material.metadataUri)) == keccak256(bytes(metadataUri)),
                "MaterialToken: metadata mismatch"
            );
        }

        material.totalMinted += amount;

        _mint(to, tokenId, amount, "");

        emit MaterialMinted(_msgSender(), to, tokenId, amount, metadataUri);
    }

    function materialInfo(uint256 tokenId) external view returns (
        string memory metadataUri,
        uint256 totalMinted,
        bool exists
    ) {
        MaterialInfo storage material = _materials[tokenId];
        return (material.metadataUri, material.totalMinted, material.exists);
    }

    function supportsInterface(bytes4 interfaceId)
        public
        view
        override(ERC1155, AccessControl)
        returns (bool)
    {
        return super.supportsInterface(interfaceId);
    }
}
