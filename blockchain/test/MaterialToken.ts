import { expect } from "chai";
import { ethers } from "hardhat";

const Status = {
  NONE: 0,
  REGISTERED: 1,
  LISTED: 2,
  IN_ESCROW: 3,
  PROCESSED: 4
};

const BurnReason = {
  PROCESSED: 0,
  DISCARDED: 1
};

const METADATA_URI = "ipfs://material-denim-batch-1.json";
const DOCUMENT_HASH = ethers.keccak256(
  ethers.toUtf8Bytes("denim-batch-1-certificate.pdf")
);

describe("소재 토큰(MaterialToken)", function () {
  async function deployMaterialTokenFixture() {
    const [admin, supplier, brand, processor, outsider, lockManager] =
      await ethers.getSigners();

    const MaterialToken = await ethers.getContractFactory("MaterialToken");
    const materialToken = await MaterialToken.deploy(
      "ipfs://material/{id}.json",
      admin.address
    );

    await materialToken.waitForDeployment();

    const supplierRole = await materialToken.SUPPLIER_ROLE();
    const brandRole = await materialToken.BRAND_ROLE();
    const processorRole = await materialToken.PROCESSOR_ROLE();
    const statusManagerRole = await materialToken.STATUS_MANAGER_ROLE();
    const materialLockManagerRole =
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE();

    await materialToken.grantRole(supplierRole, supplier.address);
    await materialToken.grantRole(brandRole, brand.address);
    await materialToken.grantRole(processorRole, processor.address);
    await materialToken.grantRole(
      materialLockManagerRole,
      lockManager.address
    );

    return {
      materialToken,
      admin,
      supplier,
      brand,
      processor,
      outsider,
      lockManager,
      supplierRole,
      brandRole,
      processorRole,
      statusManagerRole,
      materialLockManagerRole
    };
  }

  async function mintedFixture() {
    const base = await deployMaterialTokenFixture();
    const tokenId = 1;

    await base.materialToken
      .connect(base.supplier)
      .mintMaterial(
        base.supplier.address,
        tokenId,
        50_000,
        METADATA_URI,
        DOCUMENT_HASH
      );

    return { ...base, tokenId };
  }

  it("소재 토큰을 발행하고 메타데이터를 저장한다", async function () {
    const { materialToken, supplier } = await deployMaterialTokenFixture();
    const tokenId = 1;
    const amount = 50_000;

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(supplier.address, tokenId, amount, METADATA_URI, DOCUMENT_HASH)
    )
      .to.emit(materialToken, "MaterialMinted")
      .withArgs(
        supplier.address,
        supplier.address,
        tokenId,
        amount,
        METADATA_URI,
        DOCUMENT_HASH
      );

    expect(await materialToken.balanceOf(supplier.address, tokenId)).to.equal(
      amount
    );

    const material = await materialToken.materialInfo(tokenId);
    expect(material.metadataUri).to.equal(METADATA_URI);
    expect(material.documentHash).to.equal(DOCUMENT_HASH);
    expect(material.totalMinted).to.equal(amount);
    expect(material.totalBurned).to.equal(0);
    expect(material.status).to.equal(Status.REGISTERED);
    expect(material.exists).to.equal(true);
  });

  it("공급자가 브랜드에 소재 일부를 전송할 수 있다", async function () {
    const { materialToken, supplier, brand, tokenId } = await mintedFixture();

    await materialToken
      .connect(supplier)
      .safeTransferFrom(supplier.address, brand.address, tokenId, 20_000, "0x");

    expect(await materialToken.balanceOf(supplier.address, tokenId)).to.equal(
      30_000
    );
    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(
      20_000
    );
  });

  it("공급자 권한이 없는 계정의 발행을 거부한다", async function () {
    const { materialToken, outsider } = await deployMaterialTokenFixture();

    await expect(
      materialToken
        .connect(outsider)
        .mintMaterial(outsider.address, 1, 50_000, METADATA_URI, DOCUMENT_HASH)
    ).to.be.revertedWithCustomError(
      materialToken,
      "AccessControlUnauthorizedAccount"
    );
  });

  it("같은 토큰 ID의 메타데이터를 변경할 수 없다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(
          supplier.address,
          tokenId,
          10_000,
          "ipfs://different-material.json",
          DOCUMENT_HASH
        )
    ).to.be.revertedWith("MaterialToken: metadata mismatch");
  });

  it("같은 토큰 ID의 증빙 문서 해시를 변경할 수 없다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();
    const otherHash = ethers.keccak256(ethers.toUtf8Bytes("forged.pdf"));

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(supplier.address, tokenId, 10_000, METADATA_URI, otherHash)
    ).to.be.revertedWith("MaterialToken: documentHash mismatch");
  });

  it("증빙 문서 해시가 없으면 발행을 거부한다", async function () {
    const { materialToken, supplier } = await deployMaterialTokenFixture();

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(supplier.address, 1, 50_000, METADATA_URI, ethers.ZeroHash)
    ).to.be.revertedWith("MaterialToken: documentHash is empty");
  });

  it("소재의 증빙 문서 해시가 일치하는지 검증한다", async function () {
    const { materialToken, tokenId } = await mintedFixture();
    const forged = ethers.keccak256(ethers.toUtf8Bytes("forged.pdf"));

    expect(await materialToken.verifyDocument(tokenId, DOCUMENT_HASH)).to.equal(
      true
    );
    expect(await materialToken.verifyDocument(tokenId, forged)).to.equal(false);
  });

  it("상태 관리자가 판매 등록 상태를 거쳐 에스크로 상태로 전환한다", async function () {
    const { materialToken, admin, tokenId } = await mintedFixture();

    await materialToken.connect(admin).setMaterialStatus(tokenId, Status.LISTED);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.IN_ESCROW);

    const material = await materialToken.materialInfo(tokenId);
    expect(material.status).to.equal(Status.IN_ESCROW);
  });

  it("허용되지 않은 상태 전환을 거부한다", async function () {
    const { materialToken, admin, tokenId } = await mintedFixture();

    await expect(
      materialToken.connect(admin).setMaterialStatus(tokenId, Status.IN_ESCROW)
    ).to.be.revertedWith("MaterialToken: invalid status transition");
  });

  it("상태 관리자 권한이 없는 계정의 상태 변경을 거부한다", async function () {
    const { materialToken, outsider, tokenId } = await mintedFixture();

    await expect(
      materialToken.connect(outsider).setMaterialStatus(tokenId, Status.LISTED)
    ).to.be.revertedWithCustomError(
      materialToken,
      "AccessControlUnauthorizedAccount"
    );
  });

  it("에스크로 진행 중인 소재의 전송을 차단한다", async function () {
    const { materialToken, admin, supplier, brand, tokenId } =
      await mintedFixture();

    await materialToken.connect(admin).setMaterialStatus(tokenId, Status.LISTED);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.IN_ESCROW);

    await expect(
      materialToken
        .connect(supplier)
        .safeTransferFrom(supplier.address, brand.address, tokenId, 1_000, "0x")
    ).to.be.revertedWith("MaterialToken: material is in escrow");
  });

  it("에스크로가 해제되면 소재를 다시 전송할 수 있다", async function () {
    const { materialToken, admin, supplier, brand, tokenId } =
      await mintedFixture();

    await materialToken.connect(admin).setMaterialStatus(tokenId, Status.LISTED);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.IN_ESCROW);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.REGISTERED);

    await materialToken
      .connect(supplier)
      .safeTransferFrom(supplier.address, brand.address, tokenId, 1_000, "0x");

    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(
      1_000
    );
  });

  it("잠긴 소재 수량은 가용 수량에서 제외하고 직접 전송을 막는다", async function () {
    const { materialToken, supplier, brand, lockManager, tokenId } =
      await mintedFixture();

    await expect(
      materialToken
        .connect(lockManager)
        .lockMaterial(supplier.address, tokenId, 20_000)
    )
      .to.emit(materialToken, "MaterialLocked")
      .withArgs(lockManager.address, supplier.address, tokenId, 20_000);

    expect(
      await materialToken.lockedBalanceOf(supplier.address, tokenId)
    ).to.equal(20_000);
    expect(await materialToken.availableBalance(supplier.address, tokenId)).to.equal(
      30_000
    );

    await expect(
      materialToken
        .connect(supplier)
        .safeTransferFrom(supplier.address, brand.address, tokenId, 31_000, "0x")
    ).to.be.revertedWith("MaterialToken: amount exceeds available balance");
  });

  it("잠금 권한이 없는 계정의 소재 잠금을 거부한다", async function () {
    const { materialToken, supplier, outsider, tokenId } = await mintedFixture();

    await expect(
      materialToken
        .connect(outsider)
        .lockMaterial(supplier.address, tokenId, 1_000)
    ).to.be.revertedWithCustomError(
      materialToken,
      "AccessControlUnauthorizedAccount"
    );
  });

  it("소재 일부를 소각하면 잔여 공급량이 감소한다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await expect(
      materialToken
        .connect(supplier)
        .burnMaterial(supplier.address, tokenId, 20_000, BurnReason.PROCESSED)
    )
      .to.emit(materialToken, "MaterialBurned")
      .withArgs(
        supplier.address,
        supplier.address,
        tokenId,
        20_000,
        BurnReason.PROCESSED,
        30_000
      );

    expect(await materialToken.balanceOf(supplier.address, tokenId)).to.equal(
      30_000
    );
    expect(await materialToken.remainingSupply(tokenId)).to.equal(30_000);

    const material = await materialToken.materialInfo(tokenId);
    expect(material.totalBurned).to.equal(20_000);
    expect(material.status).to.equal(Status.REGISTERED);
  });

  it("가공 목적으로 전량 소각하면 소재를 가공 완료 상태로 변경한다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await materialToken
      .connect(supplier)
      .burnMaterial(supplier.address, tokenId, 50_000, BurnReason.PROCESSED);

    const material = await materialToken.materialInfo(tokenId);
    expect(material.status).to.equal(Status.PROCESSED);
    expect(await materialToken.remainingSupply(tokenId)).to.equal(0);
  });

  it("다른 소유자의 소재를 소각할 수 없다", async function () {
    const { materialToken, supplier, outsider, tokenId } = await mintedFixture();

    await expect(
      materialToken
        .connect(outsider)
        .burnMaterial(supplier.address, tokenId, 1_000, BurnReason.DISCARDED)
    ).to.be.revertedWith("MaterialToken: caller is not owner nor approved");
  });

  it("잠긴 소재는 소각할 수 없다", async function () {
    const { materialToken, supplier, lockManager, tokenId } =
      await mintedFixture();

    await materialToken
      .connect(lockManager)
      .lockMaterial(supplier.address, tokenId, 10_000);

    await expect(
      materialToken
        .connect(supplier)
        .burnMaterial(supplier.address, tokenId, 41_000, BurnReason.DISCARDED)
    ).to.be.revertedWith("MaterialToken: amount exceeds available balance");
  });

  it("가공 완료된 소재의 추가 발행을 거부한다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await materialToken
      .connect(supplier)
      .burnMaterial(supplier.address, tokenId, 50_000, BurnReason.PROCESSED);

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(
          supplier.address,
          tokenId,
          10_000,
          METADATA_URI,
          DOCUMENT_HASH
        )
    ).to.be.revertedWith("MaterialToken: status does not allow minting");
  });
});
