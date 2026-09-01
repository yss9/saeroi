// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {IERC1155} from "@openzeppelin/contracts/token/ERC1155/IERC1155.sol";

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

contract Marketplace {
    enum OrderStatus {
        NONE,
        PAID,
        COMPLETED,
        CANCELLED
    }

    struct Listing {
        uint256 id;
        uint256 materialId;
        address seller;
        uint256 initialQuantity;
        uint256 remainingQuantity;
        uint256 unitPrice;
        bool active;
    }

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

    uint8 private constant MATERIAL_STATUS_REGISTERED = 1;

    IMaterialToken public immutable materialToken;
    IERC20 public immutable paymentToken;

    uint256 public nextListingId = 1;
    uint256 public nextOrderId = 1;

    mapping(uint256 listingId => Listing listing) private _listings;
    mapping(uint256 orderId => Order order) private _orders;
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

    constructor(address materialTokenAddress, address paymentTokenAddress) {
        require(materialTokenAddress != address(0), "Marketplace: material token is zero address");
        require(paymentTokenAddress != address(0), "Marketplace: payment token is zero address");

        materialToken = IMaterialToken(materialTokenAddress);
        paymentToken = IERC20(paymentTokenAddress);
    }

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

    function availableBalance(address owner, uint256 materialId) external view returns (uint256) {
        return materialToken.availableBalance(owner, materialId);
    }

    function getListing(uint256 listingId) external view returns (Listing memory) {
        return _listings[listingId];
    }

    function getOrder(uint256 orderId) external view returns (Order memory) {
        return _orders[orderId];
    }
}
