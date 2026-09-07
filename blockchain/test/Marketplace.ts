import { expect } from "chai";
import { createHash } from "node:crypto";
import { ethers } from "hardhat";

const METADATA_URI = "ipfs://material-denim-batch-1.json";
const EVIDENCE_HASH = `0x${createHash("sha256")
  .update("denim-batch-1-manifest.json")
  .digest("hex")}`;
const DISPUTE_HASH = `0x${createHash("sha256")
  .update("delivery-dispute-1")
  .digest("hex")}`;

const ListingStatus = { NONE: 0, ACTIVE: 1, SOLD_OUT: 2, CANCELLED: 3 };
const OrderStatus = {
  NONE: 0,
  CREATED: 1,
  SHIPPED: 2,
  RECEIVED: 3,
  COMPLETED: 4,
  CANCELLED: 5,
  DISPUTED: 6
};
const LockStatus = { NONE: 0, ACTIVE: 1, RELEASED: 2, CONSUMED: 3, EXPIRED: 4 };
const DisputeResolution = { NONE: 0, RETURN_TO_SELLER: 1, TRANSFER_TO_BUYER: 2 };

describe("마켓플레이스(Marketplace)", function () {
  async function deployFixture() {
    const [admin, supplier, brand, anotherBrand, outsider] =
      await ethers.getSigners();

    const MaterialToken = await ethers.getContractFactory("MaterialToken");
    const materialToken = await MaterialToken.deploy(
      "ipfs://material/{id}.json",
      admin.address
    );
    await materialToken.waitForDeployment();

    const Marketplace = await ethers.getContractFactory("Marketplace");
    const marketplace = await Marketplace.deploy(
      await materialToken.getAddress(),
      admin.address
    );
    await marketplace.waitForDeployment();

    await materialToken.grantRole(
      await materialToken.SUPPLIER_ROLE(),
      supplier.address
    );
    await materialToken.grantRole(
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
      await marketplace.getAddress()
    );
    await materialToken.grantRole(await materialToken.BRAND_ROLE(), brand.address);
    await materialToken.grantRole(await materialToken.BRAND_ROLE(), anotherBrand.address);
    await materialToken
      .connect(supplier)
      .mintMaterial(
        supplier.address,
        50_000,
        1,
        METADATA_URI,
        EVIDENCE_HASH
      );

    return {
      materialToken,
      marketplace,
      admin,
      supplier,
      brand,
      anotherBrand,
      outsider,
      tokenId: 1n
    };
  }

  it("판매 등록 시 판매글 전용 잠금을 만들고 가용 수량을 줄인다", async function () {
    const { materialToken, marketplace, supplier, tokenId } =
      await deployFixture();

    await expect(
      marketplace.connect(supplier).createListing(tokenId, 20_000)
    )
      .to.emit(marketplace, "ListingCreated")
      .withArgs(1, tokenId, 1, supplier.address, 20_000);

    const listing = await marketplace.getListing(1);
    const materialLock = await materialToken.materialLockInfo(1);
    expect(listing.materialLockId).to.equal(1);
    expect(listing.remainingQuantity).to.equal(20_000);
    expect(listing.reservedQuantity).to.equal(0);
    expect(listing.status).to.equal(ListingStatus.ACTIVE);
    expect(materialLock.referenceId).to.equal(1);
    expect(await materialToken.availableBalance(supplier.address, tokenId)).to.equal(30_000);
  });

  it("판매글마다 서로 다른 잠금 ID를 발급한다", async function () {
    const { marketplace, supplier, tokenId } = await deployFixture();

    await marketplace.connect(supplier).createListing(tokenId, 10_000);
    await marketplace.connect(supplier).createListing(tokenId, 15_000);

    expect((await marketplace.getListing(1)).materialLockId).to.equal(1);
    expect((await marketplace.getListing(2)).materialLockId).to.equal(2);
  });

  it("가용 수량보다 큰 판매 등록을 거부한다", async function () {
    const { marketplace, supplier, tokenId } = await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 20_000);

    await expect(
      marketplace.connect(supplier).createListing(tokenId, 31_000)
    ).to.be.revertedWith("Marketplace: insufficient available balance");
  });

  it("판매자가 판매글을 취소하면 미예약 수량의 잠금을 해제한다", async function () {
    const { materialToken, marketplace, supplier, tokenId } =
      await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 20_000);

    await expect(marketplace.connect(supplier).cancelListing(1))
      .to.emit(marketplace, "ListingCancelled")
      .withArgs(1, supplier.address, 20_000);

    expect((await marketplace.getListing(1)).status).to.equal(ListingStatus.CANCELLED);
    expect((await materialToken.materialLockInfo(1)).status).to.equal(LockStatus.RELEASED);
    expect(await materialToken.availableBalance(supplier.address, tokenId)).to.equal(50_000);
  });

  it("주문을 CREATED 상태로 만들고 판매글 잔량을 예약한다", async function () {
    const { marketplace, supplier, brand, tokenId } = await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 20_000);

    await expect(marketplace.connect(brand).createOrder(1, 5_000))
      .to.emit(marketplace, "OrderCreated")
      .withArgs(1, 1, brand.address, 5_000);

    const listing = await marketplace.getListing(1);
    const order = await marketplace.getOrder(1);
    expect(listing.remainingQuantity).to.equal(15_000);
    expect(listing.reservedQuantity).to.equal(5_000);
    expect(order.status).to.equal(OrderStatus.CREATED);
    expect(Object.keys(order).some((key) => key === "totalPrice" || key === "paymentReferenceHash")).to.equal(false);
  });

  it("온체인 ABI에 결제 금액·결제 확인 함수가 없다", async function () {
    const { marketplace } = await deployFixture();
    const abiNames = marketplace.interface.fragments
      .filter((fragment) => fragment.type === "function" || fragment.type === "event")
      .map((fragment) => ("name" in fragment ? fragment.name : ""));
    expect(abiNames).not.to.include("markPaid");
    expect(abiNames).not.to.include("calculateTotalPrice");
    expect(abiNames).not.to.include("PaymentConfirmed");
  });

  it("오프체인 결제 완료 후 발송·수령 순서로 무게 토큰만 이전한다", async function () {
    const { materialToken, marketplace, supplier, brand, tokenId } =
      await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 20_000);
    await marketplace.connect(brand).createOrder(1, 10_000);

    await marketplace.connect(supplier).markShipped(1);
    expect((await marketplace.getOrder(1)).status).to.equal(OrderStatus.SHIPPED);

    await expect(marketplace.connect(brand).confirmReceipt(1))
      .to.emit(marketplace, "ReceiptConfirmed")
      .withArgs(1, brand.address)
      .and.to.emit(marketplace, "MaterialTransferred")
      .withArgs(1, supplier.address, brand.address, tokenId, 10_000);

    expect((await marketplace.getOrder(1)).status).to.equal(OrderStatus.COMPLETED);
    expect((await marketplace.getListing(1)).reservedQuantity).to.equal(0);
    expect(await materialToken.balanceOf(supplier.address, tokenId)).to.equal(40_000);
    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(10_000);
    expect((await materialToken.materialLockInfo(1)).remainingAmount).to.equal(10_000);
  });

  it("Brand 권한이 없는 계정은 주문을 만들 수 없다", async function () {
    const { marketplace, supplier, outsider, tokenId } =
      await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 2_000);

    await expect(marketplace.connect(outsider).createOrder(1, 1_000))
      .to.be.revertedWith("Marketplace: buyer role required");
  });

  it("판매자가 아닌 계정의 발송과 구매자가 아닌 계정의 수령 확인을 거부한다", async function () {
    const { marketplace, supplier, brand, outsider, tokenId } =
      await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 2_000);
    await marketplace.connect(brand).createOrder(1, 1_000);

    await expect(marketplace.connect(outsider).markShipped(1)).to.be.revertedWith(
      "Marketplace: caller is not seller"
    );
    await marketplace.connect(supplier).markShipped(1);
    await expect(marketplace.connect(outsider).confirmReceipt(1)).to.be.revertedWith(
      "Marketplace: caller is not buyer"
    );
  });

  it("발송 전 주문을 취소하면 예약 수량을 판매글 잔량으로 돌린다", async function () {
    const { marketplace, supplier, brand, tokenId } = await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 20_000);
    await marketplace.connect(brand).createOrder(1, 5_000);

    await marketplace.connect(brand).cancelOrder(1);

    const listing = await marketplace.getListing(1);
    expect((await marketplace.getOrder(1)).status).to.equal(OrderStatus.CANCELLED);
    expect(listing.remainingQuantity).to.equal(20_000);
    expect(listing.reservedQuantity).to.equal(0);
    expect(listing.status).to.equal(ListingStatus.ACTIVE);
  });

  it("예약 주문이 있는 판매글 취소 시 예약분만 유지하고 주문 취소 때 마저 해제한다", async function () {
    const { materialToken, marketplace, supplier, brand, tokenId } =
      await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 20_000);
    await marketplace.connect(brand).createOrder(1, 5_000);

    await marketplace.connect(supplier).cancelListing(1);
    expect((await materialToken.materialLockInfo(1)).remainingAmount).to.equal(5_000);
    expect((await marketplace.getListing(1)).reservedQuantity).to.equal(5_000);

    await marketplace.connect(brand).cancelOrder(1);
    expect((await materialToken.materialLockInfo(1)).status).to.equal(LockStatus.RELEASED);
    expect(await materialToken.availableBalance(supplier.address, tokenId)).to.equal(50_000);
  });

  it("배송 후 분쟁은 관리자 판정 없이 양 당사자의 동일한 서명으로 Buyer 이전한다", async function () {
    const { materialToken, marketplace, supplier, brand, tokenId } = await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 10_000);
    await marketplace.connect(brand).createOrder(1, 5_000);
    await marketplace.connect(supplier).markShipped(1);
    const reasonHash = ethers.keccak256(ethers.toUtf8Bytes("delivery dispute"));
    await marketplace.connect(brand).openDispute(1, reasonHash);
    await marketplace.connect(supplier).settleDispute(1, DisputeResolution.TRANSFER_TO_BUYER);
    expect((await marketplace.getOrder(1)).status).to.equal(OrderStatus.DISPUTED);
    await expect(marketplace.connect(brand).settleDispute(1, DisputeResolution.TRANSFER_TO_BUYER))
      .to.emit(marketplace, "DisputeSettled");
    expect((await marketplace.getOrder(1)).status).to.equal(OrderStatus.COMPLETED);
    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(5_000);
  });

  it("분쟁 합의가 RETURN_TO_SELLER이면 주문 수량의 Lock을 해제한다", async function () {
    const { materialToken, marketplace, supplier, brand, tokenId } = await deployFixture();
    await marketplace.connect(supplier).createListing(tokenId, 5_000);
    await marketplace.connect(brand).createOrder(1, 5_000);
    await marketplace.connect(supplier).markShipped(1);
    await marketplace.connect(supplier).openDispute(1, DISPUTE_HASH);
    await marketplace.connect(supplier).settleDispute(1, DisputeResolution.RETURN_TO_SELLER);
    await marketplace.connect(brand).settleDispute(1, DisputeResolution.RETURN_TO_SELLER);
    expect((await marketplace.getOrder(1)).status).to.equal(OrderStatus.CANCELLED);
    expect((await materialToken.materialLockInfo(1)).status).to.equal(LockStatus.RELEASED);
    expect(await materialToken.availableBalance(supplier.address, tokenId)).to.equal(50_000);
  });
});
