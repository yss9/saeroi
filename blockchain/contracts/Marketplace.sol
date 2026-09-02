// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {IERC1155} from "@openzeppelin/contracts/token/ERC1155/IERC1155.sol";

/// @dev Marketplace가 사용하는 MaterialToken 기능만 선언한 최소 인터페이스다.
interface IMaterialToken is IERC1155 {
    function materialInfo(uint256 tokenId)
        external
        view
        returns (
            string memory metadataUri,
            bytes32 documentHash,
            uint256 totalMinted,
            uint256 totalBurned,
            uint8 status,
            bool exists
        );

    function lockMaterial(address owner, uint256 tokenId, uint256 amount) external;
    function unlockMaterial(address owner, uint256 tokenId, uint256 amount) external;
    function availableBalance(address owner, uint256 tokenId) external view returns (uint256);
}

/// @title Marketplace
/// @notice Material 판매 등록, 부분 구매, PaymentToken 예치, 수령 확인 후 정산을 처리한다.
/// @dev 판매 등록 시 Material 소유권은 seller에게 유지되고 해당 수량만 잠긴다.
///      구매자가 confirmReceipt를 호출하면 잠금 해제, Material 이전, 대금 정산을 한 트랜잭션에서 처리한다.
contract Marketplace {
    /// @notice 주문의 결제 및 완료 상태다.
    /// @dev 현재 구현은 배송 상태를 별도로 저장하지 않고 PAID에서 COMPLETED로 바로 전환한다.
    enum OrderStatus {
        NONE,
        PAID,
        COMPLETED,
        CANCELLED
    }

    /// @notice 판매자가 등록한 Material과 판매 가능 잔량을 보관한다.
    struct Listing {
        uint256 id;
        uint256 materialId;
        address seller;
        uint256 initialQuantity;
        uint256 remainingQuantity;
        uint256 unitPrice;
        bool active;
    }

    /// @notice 구매 시 생성되는 주문과 Escrow 결제 정보를 보관한다.
    struct Order {
        uint256 id;
        uint256 listingId;
        address seller;
        address buyer;
        uint256 materialId;
        uint256 quantity;
        uint256 paymentAmount;
        OrderStatus status;
    }

    // MaterialToken의 REGISTERED 상태 값. 현재 이 상태의 Material만 판매 등록할 수 있다.
    uint8 private constant MATERIAL_STATUS_REGISTERED = 1;

    // 배포 시 지정하며 이후 변경할 수 없는 MaterialToken과 PaymentToken 주소다.
    IMaterialToken public immutable materialToken;
    IERC20 public immutable paymentToken;

    uint256 public nextListingId = 1;
    uint256 public nextOrderId = 1;

    mapping(uint256 listingId => Listing listing) private _listings;
    mapping(uint256 orderId => Order order) private _orders;
    // Marketplace가 판매글별로 예약한 수량의 합이다. 실제 전송 제한은 MaterialToken의 Lock이 수행한다.
    mapping(address owner => mapping(uint256 materialId => uint256 amount)) public lockedBalance;

    event ListingCreated(
        uint256 indexed listingId,
        uint256 indexed materialId,
        address indexed seller,
        uint256 quantity,
        uint256 unitPrice
    );

    event ListingCancelled(uint256 indexed listingId, address indexed seller);

    event OrderCreated(
        uint256 indexed orderId,
        uint256 indexed listingId,
        address indexed buyer,
        uint256 quantity,
        uint256 paymentAmount
    );

    event ReceiptConfirmed(uint256 indexed orderId, address indexed buyer);
    event MaterialTransferred(
        uint256 indexed orderId,
        address indexed seller,
        address indexed buyer,
        uint256 materialId,
        uint256 quantity
    );
    event PaymentReleased(uint256 indexed orderId, address indexed seller, uint256 amount);
    event OrderCompleted(uint256 indexed orderId);

    /// @param materialTokenAddress Material 보유량과 Lock을 관리하는 ERC-1155 주소
    /// @param paymentTokenAddress 구매대금으로 사용하는 ERC-20 주소
    constructor(address materialTokenAddress, address paymentTokenAddress) {
        require(materialTokenAddress != address(0), "Marketplace: material token is zero address");
        require(paymentTokenAddress != address(0), "Marketplace: payment token is zero address");

        materialToken = IMaterialToken(materialTokenAddress);
        paymentToken = IERC20(paymentTokenAddress);
    }

    /// @notice 보유 Material 일부를 판매 등록하고 해당 수량을 잠근다.
    /// @param materialId 판매할 Material ID
    /// @param quantity 판매 등록 수량(gram)
    /// @param unitPrice gram당 PaymentToken 가격
    /// @return listingId 생성된 판매글 ID
    function createListing(uint256 materialId, uint256 quantity, uint256 unitPrice)
        external
        returns (uint256 listingId)
    {
        require(quantity > 0, "Marketplace: quantity is zero");
        require(unitPrice > 0, "Marketplace: unit price is zero");
        require(
            materialToken.balanceOf(msg.sender, materialId) >= quantity,
            "Marketplace: insufficient balance"
        );
        require(
            materialToken.availableBalance(msg.sender, materialId) >= quantity,
            "Marketplace: insufficient available balance"
        );

        (, , , , uint8 status, bool exists) = materialToken.materialInfo(materialId);
        require(exists, "Marketplace: material does not exist");
        require(status == MATERIAL_STATUS_REGISTERED, "Marketplace: material is not tradable");

        listingId = nextListingId++;

        lockedBalance[msg.sender][materialId] += quantity;
        materialToken.lockMaterial(msg.sender, materialId, quantity);

        _listings[listingId] = Listing({
            id: listingId,
            materialId: materialId,
            seller: msg.sender,
            initialQuantity: quantity,
            remainingQuantity: quantity,
            unitPrice: unitPrice,
            active: true
        });

        emit ListingCreated(listingId, materialId, msg.sender, quantity, unitPrice);
    }

    /// @notice 판매자가 판매글을 취소하고 아직 판매되지 않은 수량의 잠금을 해제한다.
    function cancelListing(uint256 listingId) external {
        Listing storage listing = _listings[listingId];

        require(listing.active, "Marketplace: listing is not active");
        require(listing.seller == msg.sender, "Marketplace: caller is not seller");

        uint256 releaseQuantity = listing.remainingQuantity;

        listing.active = false;
        listing.remainingQuantity = 0;

        if (releaseQuantity > 0) {
            lockedBalance[msg.sender][listing.materialId] -= releaseQuantity;
            materialToken.unlockMaterial(msg.sender, listing.materialId, releaseQuantity);
        }

        emit ListingCancelled(listingId, msg.sender);
    }

    /// @notice 판매글 일부 또는 전부를 구매하고 구매대금을 Marketplace에 예치한다.
    /// @dev 호출 전 buyer가 Marketplace에 paymentAmount 이상의 ERC-20 allowance를 부여해야 한다.
    /// @param listingId 구매할 판매글 ID
    /// @param quantity 구매 수량(gram)
    /// @return orderId 생성된 주문 ID
    function createOrder(uint256 listingId, uint256 quantity) external returns (uint256 orderId) {
        Listing storage listing = _listings[listingId];

        require(listing.active, "Marketplace: listing is not active");
        require(quantity > 0, "Marketplace: quantity is zero");
        require(quantity <= listing.remainingQuantity, "Marketplace: quantity exceeds listing");
        require(msg.sender != listing.seller, "Marketplace: buyer is seller");

        uint256 paymentAmount = quantity * listing.unitPrice;

        listing.remainingQuantity -= quantity;
        if (listing.remainingQuantity == 0) {
            listing.active = false;
        }

        orderId = nextOrderId++;
        _orders[orderId] = Order({
            id: orderId,
            listingId: listingId,
            seller: listing.seller,
            buyer: msg.sender,
            materialId: listing.materialId,
            quantity: quantity,
            paymentAmount: paymentAmount,
            status: OrderStatus.PAID
        });

        require(
            paymentToken.transferFrom(msg.sender, address(this), paymentAmount),
            "Marketplace: payment transfer failed"
        );

        emit OrderCreated(orderId, listingId, msg.sender, quantity, paymentAmount);
    }

    /// @notice 구매자가 정상 수령을 확인하면 Material과 예치 대금을 각각 이전한다.
    /// @dev seller는 사전에 Marketplace에 ERC-1155 operator 승인을 부여해야 한다.
    ///      COMPLETED로 먼저 변경하므로 동일 주문의 중복 정산을 막는다.
    function confirmReceipt(uint256 orderId) external {
        Order storage order = _orders[orderId];

        require(order.status == OrderStatus.PAID, "Marketplace: order is not paid");
        require(order.buyer == msg.sender, "Marketplace: caller is not buyer");

        order.status = OrderStatus.COMPLETED;

        lockedBalance[order.seller][order.materialId] -= order.quantity;
        materialToken.unlockMaterial(order.seller, order.materialId, order.quantity);
        materialToken.safeTransferFrom(
            order.seller,
            order.buyer,
            order.materialId,
            order.quantity,
            ""
        );

        require(
            paymentToken.transfer(order.seller, order.paymentAmount),
            "Marketplace: payment release failed"
        );

        emit ReceiptConfirmed(orderId, msg.sender);
        emit MaterialTransferred(
            orderId,
            order.seller,
            order.buyer,
            order.materialId,
            order.quantity
        );
        emit PaymentReleased(orderId, order.seller, order.paymentAmount);
        emit OrderCompleted(orderId);
    }

    /// @notice MaterialToken을 기준으로 owner의 실제 사용 가능 수량을 조회한다.
    function availableBalance(address owner, uint256 materialId) external view returns (uint256) {
        return materialToken.availableBalance(owner, materialId);
    }

    /// @notice 판매글 전체 정보를 조회한다.
    function getListing(uint256 listingId) external view returns (Listing memory) {
        return _listings[listingId];
    }

    /// @notice 주문 전체 정보를 조회한다.
    function getOrder(uint256 orderId) external view returns (Order memory) {
        return _orders[orderId];
    }
}
