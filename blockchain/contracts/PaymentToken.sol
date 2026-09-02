// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

/// @title PaymentToken
/// @notice 소재 거래와 가공비 결제에 사용하는 ERC-20 토큰(CFT)이다.
/// @dev SSAFY Besu 환경에서 사용할 결제 수단이며 AccessControl로 추가 발행 권한을 제한한다.
contract PaymentToken is ERC20, AccessControl {
    // 초기 발행 이후 공급량을 추가할 수 있는 역할이다.
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");

    // 초기 발행량은 1,000,000 CFT이며 ERC-20 기본 decimals 18을 사용한다.
    uint256 public constant INITIAL_SUPPLY = 1_000_000 * 10 ** 18;

    /// @param admin 초기 물량을 받고 관리자·추가 발행 권한을 보유할 지갑
    constructor(address admin) ERC20("Circular Fashion Token", "CFT") {
        require(admin != address(0), "PaymentToken: admin is zero address");

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(MINTER_ROLE, admin);

        // 초기 물량은 admin이 보유하고 배포 후 분배 스크립트로 업체 지갑에 전달한다.
        _mint(admin, INITIAL_SUPPLY);
    }

    /// @notice MINTER_ROLE 보유자가 지정한 지갑에 결제 토큰을 추가 발행한다.
    /// @param to 추가 발행량을 받을 지갑
    /// @param amount 발행 수량(최소 단위)
    function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) {
        require(to != address(0), "PaymentToken: to is zero address");
        require(amount > 0, "PaymentToken: amount is zero");

        _mint(to, amount);
    }
}
