// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

//PaymentToken - erc20. 소재 거래 대금 결제용 토큰
//SSAFY Besu 네트워크는 네이티브 코인을 지급받을 수 없으므로 결제 수단을 직접 발행한다.
//accesscontrol - 추가 발행 권한 제한
contract PaymentToken is ERC20, AccessControl {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");

    //초기 발행량 1,000,000 CFT (decimals 18)
    uint256 public constant INITIAL_SUPPLY = 1_000_000 * 10 ** 18;

    constructor(address admin) ERC20("Circular Fashion Token", "CFT") {
        require(admin != address(0), "PaymentToken: admin is zero address");

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(MINTER_ROLE, admin);

        //초기 물량은 admin 이 보유하고, 이후 업체 지갑으로 분배한다.
        _mint(admin, INITIAL_SUPPLY);
    }

    //업체가 늘어나 결제 물량이 부족할 때 추가 발행
    function mint(address to, uint256 amount) external onlyRole(MINTER_ROLE) {
        require(to != address(0), "PaymentToken: to is zero address");
        require(amount > 0, "PaymentToken: amount is zero");

        _mint(to, amount);
    }
}
