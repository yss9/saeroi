// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {IERC1155} from "@openzeppelin/contracts/token/ERC1155/IERC1155.sol";

/// @dev Marketplace가 사용하는 MaterialToken 기능만 선언한 최소 인터페이스다.
interface IMaterialToken is IERC1155 {
    function createMaterialLock(
        address owner,
        uint256 tokenId,
        uint256 amount,
        uint8 purpose,
        uint256 referenceId
    ) external returns (uint256 lockId);

    function releaseMaterialLock(uint256 lockId, uint256 amount) external;
    function consumeMaterialLock(uint256 lockId, address to, uint256 amount) external;
    function availableBalance(address owner, uint256 tokenId) external view returns (uint256);
}

/// @title Marketplace
/// @notice 소재 판매 잠금과 거래 상태, 수령 완료 후 소유권 이전을 관리한다.
/// @dev 결제 승인·취소·환불·정산은 백엔드와 카카오페이가 담당한다. 컨트랙트는 결제 토큰을 보유하지 않는다.
contract Marketplace is AccessControl {
    bytes32 public constant PAYMENT_MANAGER_ROLE =
        keccak256("PAYMENT_MANAGER_ROLE");

    enum ListingStatus {
        NONE,
        ACTIVE,
        SOLD_OUT,
        CANCELLED
    }

    /// @dev DISPUTED는 DB 상태와 ABI 호환을 위해 예약한다. 분쟁 처리 함수는 현재 범위에 포함하지 않는다.
    enum OrderStatus {
        NONE,
        CREATED,
        PAID,
        SHIPPED,
        RECEIVED,
        COMPLETED,
        CANCELLED,
        DISPUTED
    }

    struct Listing {
        uint256 id;
        uint256 materialId;
        uint256 materialLockId;
        address seller;
        uint256 initialQuantity;
        uint256 remainingQuantity;
        uint256 reservedQuantity;
        uint256 pricePerKg;
        ListingStatus status;
    }

    struct Order {
        uint256 id;
        uint256 listingId;
        address seller;
        address buyer;
        uint256 materialId;
        uint256 quantity;
        uint256 totalPrice;
        bytes32 paymentReferenceHash;
        OrderStatus status;
    }

    uint8 private constant LOCK_PURPOSE_MARKETPLACE = 0;

    IMaterialToken public immutable materialToken;
    uint256 public nextListingId = 1;
    uint256 public nextOrderId = 1;

    mapping(uint256 listingId => Listing listing) private _listings;
    mapping(uint256 orderId => Order order) private _orders;

    event ListingCreated(
        uint256 indexed listingId,
        uint256 indexed materialId,
        uint256 indexed materialLockId,
        address seller,
        uint256 quantity,
        uint256 pricePerKg
    );
    event ListingCancelled(
        uint256 indexed listingId,
        address indexed seller,
        uint256 releasedQuantity
    );
    event OrderCreated(
        uint256 indexed orderId,
        uint256 indexed listingId,
        address indexed buyer,
        uint256 quantity,
        uint256 totalPrice
    );
    event OrderStatusChanged(
        uint256 indexed orderId,
        OrderStatus previousStatus,
        OrderStatus newStatus,
        address indexed operator
    );
    event PaymentConfirmed(
        uint256 indexed orderId,
        bytes32 indexed paymentReferenceHash,
        address indexed operator
    );
    event ReceiptConfirmed(uint256 indexed orderId, address indexed buyer);
    event MaterialTransferred(
        uint256 indexed orderId,
        address indexed seller,
        address indexed buyer,
        uint256 materialId,
        uint256 quantity
    );

    constructor(address materialTokenAddress, address admin) {
        require(materialTokenAddress != address(0), "Marketplace: material token is zero address");
        require(admin != address(0), "Marketplace: admin is zero address");

        materialToken = IMaterialToken(materialTokenAddress);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(PAYMENT_MANAGER_ROLE, admin);
    }

    /// @notice 보유 소재 일부를 판매 등록하고 판매글 전용 잠금을 만든다.
    /// @param pricePerKg 1kg(1000g)당 정수 가격
    function createListing(
        uint256 materialId,
        uint256 quantity,
        uint256 pricePerKg
    ) external returns (uint256 listingId) {
        require(quantity > 0, "Marketplace: quantity is zero");
        require(pricePerKg > 0, "Marketplace: price per kg is zero");
        require(
            materialToken.availableBalance(_msgSender(), materialId) >= quantity,
            "Marketplace: insufficient available balance"
        );

        listingId = nextListingId++;
        uint256 lockId = materialToken.createMaterialLock(
            _msgSender(),
            materialId,
            quantity,
            LOCK_PURPOSE_MARKETPLACE,
            listingId
        );

        _listings[listingId] = Listing({
            id: listingId,
            materialId: materialId,
            materialLockId: lockId,
            seller: _msgSender(),
            initialQuantity: quantity,
            remainingQuantity: quantity,
            reservedQuantity: 0,
            pricePerKg: pricePerKg,
            status: ListingStatus.ACTIVE
        });

        emit ListingCreated(
            listingId,
            materialId,
            lockId,
            _msgSender(),
            quantity,
            pricePerKg
        );
    }

    /// @notice 판매를 중단하고 아직 주문에 예약되지 않은 수량만 해제한다.
    /// @dev 이미 생성된 주문 수량은 결제/수령 또는 주문 취소 때까지 계속 잠긴다.
    function cancelListing(uint256 listingId) external {
        Listing storage listing = _listings[listingId];
        require(listing.id != 0, "Marketplace: listing does not exist");
        require(listing.seller == _msgSender(), "Marketplace: caller is not seller");
        require(
            listing.status == ListingStatus.ACTIVE ||
                listing.status == ListingStatus.SOLD_OUT,
            "Marketplace: listing is not cancellable"
        );

        uint256 releaseQuantity = listing.remainingQuantity;
        listing.remainingQuantity = 0;
        listing.status = ListingStatus.CANCELLED;

        if (releaseQuantity > 0) {
            materialToken.releaseMaterialLock(
                listing.materialLockId,
                releaseQuantity
            );
        }

        emit ListingCancelled(listingId, _msgSender(), releaseQuantity);
    }

    /// @notice 구매 주문을 CREATED 상태로 만들고 판매글 잔량을 예약한다.
    /// @dev 총액은 gram 수량 × kg당 가격 ÷ 1000이며, 나머지가 있으면 1원 올림한다.
    function createOrder(uint256 listingId, uint256 quantity)
        external
        returns (uint256 orderId)
    {
        Listing storage listing = _listings[listingId];
        require(listing.status == ListingStatus.ACTIVE, "Marketplace: listing is not active");
        require(listing.seller != _msgSender(), "Marketplace: seller cannot buy own listing");
        require(quantity > 0, "Marketplace: quantity is zero");
        require(
            listing.remainingQuantity >= quantity,
            "Marketplace: insufficient listing quantity"
        );

        uint256 totalPrice = _calculateTotalPrice(quantity, listing.pricePerKg);
        listing.remainingQuantity -= quantity;
        listing.reservedQuantity += quantity;
        if (listing.remainingQuantity == 0) {
            listing.status = ListingStatus.SOLD_OUT;
        }

        orderId = nextOrderId++;
        _orders[orderId] = Order({
            id: orderId,
            listingId: listingId,
            seller: listing.seller,
            buyer: _msgSender(),
            materialId: listing.materialId,
            quantity: quantity,
            totalPrice: totalPrice,
            paymentReferenceHash: bytes32(0),
            status: OrderStatus.CREATED
        });

        emit OrderCreated(
            orderId,
            listingId,
            _msgSender(),
            quantity,
            totalPrice
        );
    }

    /// @notice 백엔드 결제 확인 계정이 외부 결제 성공을 온체인 거래 상태에 반영한다.
    function markPaid(uint256 orderId, bytes32 paymentReferenceHash)
        external
        onlyRole(PAYMENT_MANAGER_ROLE)
    {
        require(paymentReferenceHash != bytes32(0), "Marketplace: payment reference is empty");
        Order storage order = _orders[orderId];
        require(order.status == OrderStatus.CREATED, "Marketplace: order is not created");

        order.paymentReferenceHash = paymentReferenceHash;
        _changeOrderStatus(order, OrderStatus.PAID);
        emit PaymentConfirmed(orderId, paymentReferenceHash, _msgSender());
    }

    /// @notice 판매자가 결제 완료 주문을 발송 처리한다.
    function markShipped(uint256 orderId) external {
        Order storage order = _orders[orderId];
        require(order.seller == _msgSender(), "Marketplace: caller is not seller");
        require(order.status == OrderStatus.PAID, "Marketplace: order is not paid");

        _changeOrderStatus(order, OrderStatus.SHIPPED);
    }

    /// @notice 구매자가 수령을 확인하면 잠긴 소재를 구매자에게 이전하고 거래를 완료한다.
    function confirmReceipt(uint256 orderId) external {
        Order storage order = _orders[orderId];
        require(order.buyer == _msgSender(), "Marketplace: caller is not buyer");
        require(order.status == OrderStatus.SHIPPED, "Marketplace: order is not shipped");

        Listing storage listing = _listings[order.listingId];
        _changeOrderStatus(order, OrderStatus.RECEIVED);
        emit ReceiptConfirmed(orderId, _msgSender());
        listing.reservedQuantity -= order.quantity;
        materialToken.consumeMaterialLock(
            listing.materialLockId,
            order.buyer,
            order.quantity
        );
        _changeOrderStatus(order, OrderStatus.COMPLETED);

        emit MaterialTransferred(
            orderId,
            order.seller,
            order.buyer,
            order.materialId,
            order.quantity
        );
    }

    /// @notice 결제 전 주문을 구매자 또는 판매자가 취소한다.
    function cancelOrder(uint256 orderId) external {
        Order storage order = _orders[orderId];
        require(order.status == OrderStatus.CREATED, "Marketplace: order is not cancellable");
        require(
            order.buyer == _msgSender() || order.seller == _msgSender(),
            "Marketplace: caller is not trade participant"
        );

        Listing storage listing = _listings[order.listingId];
        listing.reservedQuantity -= order.quantity;
        if (listing.status == ListingStatus.CANCELLED) {
            materialToken.releaseMaterialLock(
                listing.materialLockId,
                order.quantity
            );
        } else {
            listing.remainingQuantity += order.quantity;
            listing.status = ListingStatus.ACTIVE;
        }

        _changeOrderStatus(order, OrderStatus.CANCELLED);
    }

    function calculateTotalPrice(uint256 quantityGram, uint256 pricePerKg)
        external
        pure
        returns (uint256)
    {
        return _calculateTotalPrice(quantityGram, pricePerKg);
    }

    function availableBalance(address owner, uint256 materialId)
        external
        view
        returns (uint256)
    {
        return materialToken.availableBalance(owner, materialId);
    }

    function getListing(uint256 listingId)
        external
        view
        returns (Listing memory)
    {
        return _listings[listingId];
    }

    function getOrder(uint256 orderId) external view returns (Order memory) {
        return _orders[orderId];
    }

    function _calculateTotalPrice(uint256 quantityGram, uint256 pricePerKg)
        private
        pure
        returns (uint256)
    {
        require(quantityGram > 0, "Marketplace: quantity is zero");
        require(pricePerKg > 0, "Marketplace: price per kg is zero");
        return (quantityGram * pricePerKg + 999) / 1000;
    }

    function _changeOrderStatus(Order storage order, OrderStatus newStatus)
        private
    {
        OrderStatus previousStatus = order.status;
        order.status = newStatus;
        emit OrderStatusChanged(
            order.id,
            previousStatus,
            newStatus,
            _msgSender()
        );
    }

    function supportsInterface(bytes4 interfaceId)
        public
        view
        override
        returns (bool)
    {
        return super.supportsInterface(interfaceId);
    }
}
