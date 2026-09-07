// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {IERC1155} from "@openzeppelin/contracts/token/ERC1155/IERC1155.sol";

/// @dev Marketplace가 사용하는 MaterialToken 기능만 선언한 최소 인터페이스다.
interface IMaterialToken is IERC1155 {
    function hasRole(bytes32 role, address account) external view returns (bool);
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
    bytes32 private constant SUPPLIER_ROLE = keccak256("SUPPLIER_ROLE");
    bytes32 private constant BRAND_ROLE = keccak256("BRAND_ROLE");

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
        SHIPPED,
        RECEIVED,
        COMPLETED,
        CANCELLED,
        DISPUTED
    }

    enum DisputeResolution { NONE, RETURN_TO_SELLER, TRANSFER_TO_BUYER }

    struct Listing {
        uint256 id;
        uint256 materialId;
        uint256 materialLockId;
        address seller;
        uint256 initialQuantity;
        uint256 remainingQuantity;
        uint256 reservedQuantity;
        ListingStatus status;
    }

    struct Order {
        uint256 id;
        uint256 listingId;
        address seller;
        address buyer;
        uint256 materialId;
        uint256 quantity;
        OrderStatus status;
    }

    struct Dispute {
        bytes32 reasonHash;
        DisputeResolution sellerChoice;
        DisputeResolution buyerChoice;
        uint64 openedAt;
        uint64 settledAt;
    }

    uint8 private constant LOCK_PURPOSE_MARKETPLACE = 0;

    IMaterialToken public immutable materialToken;
    uint256 public nextListingId = 1;
    uint256 public nextOrderId = 1;

    mapping(uint256 listingId => Listing listing) private _listings;
    mapping(uint256 orderId => Order order) private _orders;
    mapping(uint256 orderId => Dispute dispute) private _disputes;

    event ListingCreated(
        uint256 indexed listingId,
        uint256 indexed materialId,
        uint256 indexed materialLockId,
        address seller,
        uint256 quantity
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
        uint256 quantity
    );
    event OrderStatusChanged(
        uint256 indexed orderId,
        OrderStatus previousStatus,
        OrderStatus newStatus,
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
    event DisputeOpened(uint256 indexed orderId, address indexed opener, bytes32 reasonHash, uint256 occurredAt);
    event DisputeSigned(uint256 indexed orderId, address indexed signer, DisputeResolution resolution, uint256 occurredAt);
    event DisputeSettled(uint256 indexed orderId, DisputeResolution resolution, address seller, address buyer, uint256 occurredAt);

    constructor(address materialTokenAddress, address admin) {
        require(materialTokenAddress != address(0), "Marketplace: material token is zero address");
        require(admin != address(0), "Marketplace: admin is zero address");

        materialToken = IMaterialToken(materialTokenAddress);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    /// @notice 공급자가 보유 소재 일부를 판매 등록하고 판매글 전용 잠금을 만든다.
    function createListing(
        uint256 materialId,
        uint256 quantity
    ) external returns (uint256 listingId) {
        require(
            materialToken.hasRole(SUPPLIER_ROLE, _msgSender()),
            "Marketplace: seller role required"
        );
        require(quantity > 0, "Marketplace: quantity is zero");
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
            status: ListingStatus.ACTIVE
        });

        emit ListingCreated(
            listingId,
            materialId,
            lockId,
            _msgSender(),
            quantity
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
    function createOrder(uint256 listingId, uint256 quantity)
        external
        returns (uint256 orderId)
    {
        require(materialToken.hasRole(BRAND_ROLE, _msgSender()), "Marketplace: buyer role required");
        Listing storage listing = _listings[listingId];
        require(listing.status == ListingStatus.ACTIVE, "Marketplace: listing is not active");
        require(listing.seller != _msgSender(), "Marketplace: seller cannot buy own listing");
        require(quantity > 0, "Marketplace: quantity is zero");
        require(
            listing.remainingQuantity >= quantity,
            "Marketplace: insufficient listing quantity"
        );

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
            status: OrderStatus.CREATED
        });

        emit OrderCreated(
            orderId,
            listingId,
            _msgSender(),
            quantity
        );
    }

    /// @notice 오프체인 결제를 마친 주문을 판매자가 발송 처리한다. 금액과 결제 정보는 온체인에 저장하지 않는다.
    function markShipped(uint256 orderId) external {
        Order storage order = _orders[orderId];
        require(order.seller == _msgSender(), "Marketplace: caller is not seller");
        require(order.status == OrderStatus.CREATED, "Marketplace: order is not shippable");

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

    /// @notice 발송 전 주문을 구매자 또는 판매자가 취소한다.
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

    /// @notice 배송 이후 거래 당사자가 증빙 Manifest 해시를 근거로 분쟁을 연다.
    function openDispute(uint256 orderId, bytes32 reasonHash) external {
        Order storage order = _orders[orderId];
        require(order.status == OrderStatus.SHIPPED, "Marketplace: order is not disputable");
        require(order.buyer == _msgSender() || order.seller == _msgSender(), "Marketplace: caller is not trade participant");
        require(reasonHash != bytes32(0), "Marketplace: reason is empty");
        Dispute storage dispute = _disputes[orderId];
        require(dispute.openedAt == 0, "Marketplace: dispute already exists");
        dispute.reasonHash = reasonHash;
        dispute.openedAt = uint64(block.timestamp);
        _changeOrderStatus(order, OrderStatus.DISPUTED);
        emit DisputeOpened(orderId, _msgSender(), reasonHash, block.timestamp);
    }

    /// @notice ADMIN 판정 없이 Seller와 Buyer가 동일한 처리 결과에 각각 서명한다.
    function settleDispute(uint256 orderId, DisputeResolution resolution) external {
        Order storage order = _orders[orderId];
        require(order.status == OrderStatus.DISPUTED, "Marketplace: order is not disputed");
        require(resolution != DisputeResolution.NONE, "Marketplace: resolution is empty");
        Dispute storage dispute = _disputes[orderId];
        if (_msgSender() == order.seller) dispute.sellerChoice = resolution;
        else if (_msgSender() == order.buyer) dispute.buyerChoice = resolution;
        else revert("Marketplace: caller is not trade participant");
        emit DisputeSigned(orderId, _msgSender(), resolution, block.timestamp);
        if (dispute.sellerChoice == resolution && dispute.buyerChoice == resolution) {
            Listing storage listing = _listings[order.listingId];
            listing.reservedQuantity -= order.quantity;
            dispute.settledAt = uint64(block.timestamp);
            if (resolution == DisputeResolution.RETURN_TO_SELLER) {
                materialToken.releaseMaterialLock(listing.materialLockId, order.quantity);
                _changeOrderStatus(order, OrderStatus.CANCELLED);
            } else {
                materialToken.consumeMaterialLock(listing.materialLockId, order.buyer, order.quantity);
                _changeOrderStatus(order, OrderStatus.COMPLETED);
                emit MaterialTransferred(orderId, order.seller, order.buyer, order.materialId, order.quantity);
            }
            emit DisputeSettled(orderId, resolution, order.seller, order.buyer, block.timestamp);
        }
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

    function getDispute(uint256 orderId) external view returns (Dispute memory) { return _disputes[orderId]; }

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
