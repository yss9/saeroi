import { expect } from "chai";
import { ethers } from "hardhat";

const METADATA_URI = "ipfs://material-denim-batch-1.json";
const DOCUMENT_HASH = ethers.keccak256(
  ethers.toUtf8Bytes("denim-batch-1-certificate.pdf")
);

const OrderStatus = {
  NONE: 0,
  PAID: 1,
  COMPLETED: 2,
  CANCELLED: 3
};

describe("마켓플레이스(Marketplace)", function () {
  async function deployMarketplaceFixture() {
    const [admin, supplier, brand, anotherBrand, outsider] =
      await ethers.getSigners();

    const MaterialToken = await ethers.getContractFactory("MaterialToken");
    const materialToken = await MaterialToken.deploy(
      "ipfs://material/{id}.json",
      admin.address
    );
    await materialToken.waitForDeployment();

    const PaymentToken = await ethers.getContractFactory("PaymentToken");
    const paymentToken = await PaymentToken.deploy(admin.address);
    await paymentToken.waitForDeployment();

    const Marketplace = await ethers.getContractFactory("Marketplace");
    const marketplace = await Marketplace.deploy(
      await materialToken.getAddress(),
      await paymentToken.getAddress()
    );
    await marketplace.waitForDeployment();

    const supplierRole = await materialToken.SUPPLIER_ROLE();
    const brandRole = await materialToken.BRAND_ROLE();
    const materialLockManagerRole =
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE();

    await materialToken.grantRole(supplierRole, supplier.address);
    await materialToken.grantRole(brandRole, brand.address);
    await materialToken.grantRole(brandRole, anotherBrand.address);
    await materialToken.grantRole(
      materialLockManagerRole,
      await marketplace.getAddress()
    );

    const tokenId = 1;
    await materialToken
      .connect(supplier)
      .mintMaterial(
        supplier.address,
        tokenId,
        50_000,
        METADATA_URI,
        DOCUMENT_HASH
      );

    await paymentToken.transfer(brand.address, ethers.parseEther("100000"));
    await paymentToken.transfer(anotherBrand.address, ethers.parseEther("100000"));

    await materialToken
      .connect(supplier)
      .setApprovalForAll(await marketplace.getAddress(), true);

    return {
      materialToken,
      paymentToken,
      marketplace,
      admin,
      supplier,
      brand,
      anotherBrand,
      outsider,
      tokenId
    };
  }

  it("판매 등록 시 판매자 소재 수량을 잠그고 가용 수량을 줄인다", async function () {
    const { materialToken, marketplace, supplier, tokenId } =
      await deployMarketplaceFixture();
    const unitPrice = ethers.parseEther("2");

    await expect(
      marketplace.connect(supplier).createListing(tokenId, 20_000, unitPrice)
    )
      .to.emit(marketplace, "ListingCreated")
      .withArgs(1, tokenId, supplier.address, 20_000, unitPrice);

    const listing = await marketplace.getListing(1);
    expect(listing.seller).to.equal(supplier.address);
    expect(listing.initialQuantity).to.equal(20_000);
    expect(listing.remainingQuantity).to.equal(20_000);
    expect(listing.unitPrice).to.equal(unitPrice);
    expect(listing.active).to.equal(true);

    expect(await marketplace.lockedBalance(supplier.address, tokenId)).to.equal(
      20_000
    );
    expect(
      await materialToken.lockedBalanceOf(supplier.address, tokenId)
    ).to.equal(20_000);
    expect(await marketplace.availableBalance(supplier.address, tokenId)).to.equal(
      30_000
    );
  });

  it("잠긴 수량을 제외한 가용 수량보다 큰 판매 등록을 거부한다", async function () {
    const { marketplace, supplier, tokenId } = await deployMarketplaceFixture();
    const unitPrice = ethers.parseEther("1");

    await marketplace.connect(supplier).createListing(tokenId, 20_000, unitPrice);

    await expect(
      marketplace.connect(supplier).createListing(tokenId, 31_000, unitPrice)
    ).to.be.revertedWith("Marketplace: insufficient available balance");
  });

  it("판매 등록으로 잠긴 소재를 지갑에서 직접 전송할 수 없다", async function () {
    const { materialToken, marketplace, supplier, brand, tokenId } =
      await deployMarketplaceFixture();

    await marketplace
      .connect(supplier)
      .createListing(tokenId, 20_000, ethers.parseEther("1"));

    await expect(
      materialToken
        .connect(supplier)
        .safeTransferFrom(supplier.address, brand.address, tokenId, 31_000, "0x")
    ).to.be.revertedWith("MaterialToken: amount exceeds available balance");
  });

  it("판매자가 판매글을 취소하면 남은 수량의 잠금을 해제한다", async function () {
    const { materialToken, marketplace, supplier, tokenId } =
      await deployMarketplaceFixture();

    await marketplace
      .connect(supplier)
      .createListing(tokenId, 20_000, ethers.parseEther("1"));

    await expect(marketplace.connect(supplier).cancelListing(1))
      .to.emit(marketplace, "ListingCancelled")
      .withArgs(1, supplier.address);

    const listing = await marketplace.getListing(1);
    expect(listing.active).to.equal(false);
    expect(listing.remainingQuantity).to.equal(0);
    expect(await marketplace.lockedBalance(supplier.address, tokenId)).to.equal(0);
    expect(
      await materialToken.lockedBalanceOf(supplier.address, tokenId)
    ).to.equal(0);
  });

  it("구매 주문은 결제 토큰을 예치하고 판매글 잔량을 줄인다", async function () {
    const { paymentToken, marketplace, supplier, brand, tokenId } =
      await deployMarketplaceFixture();
    const unitPrice = ethers.parseEther("3");
    const quantity = 5_000;
    const paymentAmount = unitPrice * BigInt(quantity);

    await marketplace.connect(supplier).createListing(tokenId, 20_000, unitPrice);
    await paymentToken
      .connect(brand)
      .approve(await marketplace.getAddress(), paymentAmount);

    await expect(marketplace.connect(brand).createOrder(1, quantity))
      .to.emit(marketplace, "OrderCreated")
      .withArgs(1, 1, brand.address, quantity, paymentAmount);

    const listing = await marketplace.getListing(1);
    const order = await marketplace.getOrder(1);

    expect(listing.remainingQuantity).to.equal(15_000);
    expect(listing.active).to.equal(true);
    expect(order.status).to.equal(OrderStatus.PAID);
    expect(order.paymentAmount).to.equal(paymentAmount);
    expect(
      await paymentToken.balanceOf(await marketplace.getAddress())
    ).to.equal(paymentAmount);
    expect(await marketplace.lockedBalance(supplier.address, tokenId)).to.equal(
      20_000
    );
  });

  it("구매자 수령 확인 시 소재 이전, 결제 정산, 잠금 해제를 한 번에 처리한다", async function () {
    const { materialToken, paymentToken, marketplace, supplier, brand, tokenId } =
      await deployMarketplaceFixture();
    const unitPrice = ethers.parseEther("2");
    const quantity = 10_000;
    const paymentAmount = unitPrice * BigInt(quantity);

    await marketplace.connect(supplier).createListing(tokenId, 20_000, unitPrice);
    await paymentToken
      .connect(brand)
      .approve(await marketplace.getAddress(), paymentAmount);
    await marketplace.connect(brand).createOrder(1, quantity);

    await expect(marketplace.connect(brand).confirmReceipt(1))
      .to.emit(marketplace, "OrderCompleted")
      .withArgs(1);

    const order = await marketplace.getOrder(1);
    expect(order.status).to.equal(OrderStatus.COMPLETED);
    expect(await materialToken.balanceOf(supplier.address, tokenId)).to.equal(
      40_000
    );
    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(
      quantity
    );
    expect(await marketplace.lockedBalance(supplier.address, tokenId)).to.equal(
      10_000
    );
    expect(
      await materialToken.lockedBalanceOf(supplier.address, tokenId)
    ).to.equal(10_000);
    expect(await paymentToken.balanceOf(supplier.address)).to.equal(paymentAmount);
    expect(await paymentToken.balanceOf(await marketplace.getAddress())).to.equal(0);
  });

  it("구매자가 아닌 계정의 수령 확인과 중복 수령 확인을 거부한다", async function () {
    const { paymentToken, marketplace, supplier, brand, outsider, tokenId } =
      await deployMarketplaceFixture();
    const unitPrice = ethers.parseEther("1");
    const quantity = 1_000;
    const paymentAmount = unitPrice * BigInt(quantity);

    await marketplace.connect(supplier).createListing(tokenId, 2_000, unitPrice);
    await paymentToken
      .connect(brand)
      .approve(await marketplace.getAddress(), paymentAmount);
    await marketplace.connect(brand).createOrder(1, quantity);

    await expect(
      marketplace.connect(outsider).confirmReceipt(1)
    ).to.be.revertedWith("Marketplace: caller is not buyer");

    await marketplace.connect(brand).confirmReceipt(1);

    await expect(
      marketplace.connect(brand).confirmReceipt(1)
    ).to.be.revertedWith("Marketplace: order is not paid");
  });
});
