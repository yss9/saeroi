import { expect } from "chai";
import { createHash } from "node:crypto";
import { ethers } from "hardhat";

const METADATA_URI = "ipfs://material-denim-batch-1.json";
const EVIDENCE_HASH = `0x${createHash("sha256")
  .update("denim-batch-1-manifest.json")
  .digest("hex")}`;
const TRANSACTION_EVIDENCE_HASH = `0x${createHash("sha256")
  .update("material-trade-1-manifest.json")
  .digest("hex")}`;
const PROCESSING_EVIDENCE_HASH = `0x${createHash("sha256")
  .update("processing-job-1-manifest.json")
  .digest("hex")}`;

const RootOrigin = { VIRGIN: 0, WASTE: 1, RECOVERED: 2 };
const GenerationType = { INITIAL: 0, PROCESSED: 1, PRODUCTION_SCRAP: 2 };
const MaterialStatus = { NONE: 0, ACTIVE: 1, DEPLETED: 2 };
const VerificationState = {
  REGISTERED: 0,
  SOURCE_VERIFIED: 1,
  TRANSACTION_VERIFIED: 2,
  PROCESSING_VERIFIED: 3,
  PRODUCT_LINKED: 4
};
const BurnReason = { PROCESSING: 0, PRODUCTION: 1, DISCARDED: 2 };
const LockPurpose = { MARKETPLACE: 0, PROCESSING: 1, PRODUCTION: 2 };
const LockStatus = { NONE: 0, ACTIVE: 1, RELEASED: 2, CONSUMED: 3 };

describe("소재 토큰(MaterialToken)", function () {
  async function deployFixture() {
    const [
      admin,
      supplier,
      brand,
      processor,
      lockManager,
      anotherLockManager,
      outsider
    ] = await ethers.getSigners();
    const MaterialToken = await ethers.getContractFactory("MaterialToken");
    const materialToken = await MaterialToken.deploy(
      "ipfs://material/{id}.json",
      admin.address
    );
    await materialToken.waitForDeployment();

    await materialToken.grantRole(
      await materialToken.SUPPLIER_ROLE(),
      supplier.address
    );
    await materialToken.grantRole(
      await materialToken.BRAND_ROLE(),
      brand.address
    );
    await materialToken.grantRole(
      await materialToken.PROCESSOR_ROLE(),
      processor.address
    );
    await materialToken.grantRole(
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
      lockManager.address
    );
    await materialToken.grantRole(
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
      anotherLockManager.address
    );

    return {
      materialToken,
      admin,
      supplier,
      brand,
      processor,
      lockManager,
      anotherLockManager,
      outsider
    };
  }

  async function mintedFixture() {
    const fixture = await deployFixture();
    await fixture.materialToken
      .connect(fixture.supplier)
      .mintMaterial(
        fixture.supplier.address,
        50_000,
        RootOrigin.RECOVERED,
        METADATA_URI,
        EVIDENCE_HASH
      );
    return { ...fixture, tokenId: 1n };
  }

  it("컨트랙트가 소재 ID를 발급하고 원천·생성 유형·SHA-256 증빙을 저장한다", async function () {
    const { materialToken, supplier } = await deployFixture();

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(
          supplier.address,
          50_000,
          RootOrigin.RECOVERED,
          METADATA_URI,
          EVIDENCE_HASH
        )
    )
      .to.emit(materialToken, "MaterialMinted")
      .withArgs(
        supplier.address,
        supplier.address,
        1,
        50_000,
        RootOrigin.RECOVERED,
        GenerationType.INITIAL,
        METADATA_URI,
        EVIDENCE_HASH
      );

    const material = await materialToken.materialInfo(1);
    expect(material.metadataUri).to.equal(METADATA_URI);
    expect(material.evidenceHash).to.equal(EVIDENCE_HASH);
    expect(material.totalMinted).to.equal(50_000);
    expect(material.totalBurned).to.equal(0);
    expect(material.rootOrigin).to.equal(RootOrigin.RECOVERED);
    expect(material.generationType).to.equal(GenerationType.INITIAL);
    expect(material.verificationState).to.equal(VerificationState.REGISTERED);
    expect(material.status).to.equal(MaterialStatus.ACTIVE);
    expect(material.registrant).to.equal(supplier.address);
    expect(material.exists).to.equal(true);
  });

  it("서로 다른 소재에 중복되지 않는 ID를 순서대로 발급한다", async function () {
    const { materialToken, supplier } = await deployFixture();

    await materialToken
      .connect(supplier)
      .mintMaterial(
        supplier.address,
        10_000,
        RootOrigin.VIRGIN,
        "ipfs://material/one.json",
        EVIDENCE_HASH
      );
    await materialToken
      .connect(supplier)
      .mintMaterial(
        supplier.address,
        20_000,
        RootOrigin.WASTE,
        "ipfs://material/two.json",
        EVIDENCE_HASH
      );

    expect(await materialToken.nextMaterialId()).to.equal(3);
    expect(await materialToken.balanceOf(supplier.address, 1)).to.equal(10_000);
    expect(await materialToken.balanceOf(supplier.address, 2)).to.equal(20_000);
  });

  it("공급자가 브랜드에 가용 소재 일부를 전송할 수 있다", async function () {
    const { materialToken, supplier, brand, tokenId } = await mintedFixture();

    await materialToken
      .connect(supplier)
      .safeTransferFrom(supplier.address, brand.address, tokenId, 12_000, "0x");

    expect(await materialToken.balanceOf(supplier.address, tokenId)).to.equal(38_000);
    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(12_000);
  });

  it("공급자 권한이 없는 계정의 발행을 거부한다", async function () {
    const { materialToken, outsider } = await deployFixture();

    await expect(
      materialToken
        .connect(outsider)
        .mintMaterial(
          outsider.address,
          1_000,
          RootOrigin.WASTE,
          METADATA_URI,
          EVIDENCE_HASH
        )
    )
      .to.be.revertedWithCustomError(materialToken, "AccessControlUnauthorizedAccount")
      .withArgs(outsider.address, await materialToken.SUPPLIER_ROLE());
  });

  it("증빙 SHA-256 값이 없으면 발행을 거부한다", async function () {
    const { materialToken, supplier } = await deployFixture();

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(
          supplier.address,
          1_000,
          RootOrigin.WASTE,
          METADATA_URI,
          ethers.ZeroHash
        )
    ).to.be.revertedWith("MaterialToken: evidenceHash is empty");
  });

  it("저장된 증빙 SHA-256 값과 입력값의 일치 여부를 확인한다", async function () {
    const { materialToken, tokenId } = await mintedFixture();
    const otherHash = `0x${createHash("sha256").update("other").digest("hex")}`;

    expect(await materialToken.verifyEvidence(tokenId, EVIDENCE_HASH)).to.equal(true);
    expect(await materialToken.verifyEvidence(tokenId, otherHash)).to.equal(false);
  });

  it("최초 등록 Supplier가 원천 증빙을 서명하고 SOURCE_VERIFIED로 변경한다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await expect(
      materialToken.connect(supplier).verifySource(tokenId, EVIDENCE_HASH)
    )
      .to.emit(materialToken, "SourceVerified")
      .withArgs(tokenId, supplier.address, EVIDENCE_HASH);

    const material = await materialToken.materialInfo(tokenId);
    const record = await materialToken.verificationRecord(
      tokenId,
      VerificationState.SOURCE_VERIFIED
    );
    expect(material.verificationState).to.equal(VerificationState.SOURCE_VERIFIED);
    expect(record.signer).to.equal(supplier.address);
    expect(record.evidenceHash).to.equal(EVIDENCE_HASH);
    expect(record.referenceId).to.equal(0);
    expect(record.verifiedAt).to.be.greaterThan(0);
  });

  it("발행하지 않은 Supplier와 중복 원천 검증을 거부한다", async function () {
    const { materialToken, supplier, outsider, tokenId } = await mintedFixture();
    await materialToken.grantRole(
      await materialToken.SUPPLIER_ROLE(),
      outsider.address
    );

    await expect(
      materialToken.connect(outsider).verifySource(tokenId, EVIDENCE_HASH)
    ).to.be.revertedWith("MaterialToken: caller is not registrant");

    await materialToken.connect(supplier).verifySource(tokenId, EVIDENCE_HASH);
    await expect(
      materialToken.connect(supplier).verifySource(tokenId, EVIDENCE_HASH)
    ).to.be.revertedWith("MaterialToken: invalid verification state");
  });

  it("소재를 인수한 Brand가 거래 증빙을 서명하고 TRANSACTION_VERIFIED로 변경한다", async function () {
    const { materialToken, supplier, brand, tokenId } = await mintedFixture();
    await materialToken.connect(supplier).verifySource(tokenId, EVIDENCE_HASH);
    await materialToken
      .connect(supplier)
      .safeTransferFrom(supplier.address, brand.address, tokenId, 10_000, "0x");

    await expect(
      materialToken
        .connect(brand)
        .verifyTransaction(tokenId, 101, TRANSACTION_EVIDENCE_HASH)
    )
      .to.emit(materialToken, "TransactionVerified")
      .withArgs(tokenId, 101, brand.address, TRANSACTION_EVIDENCE_HASH);

    const record = await materialToken.verificationRecord(
      tokenId,
      VerificationState.TRANSACTION_VERIFIED
    );
    expect((await materialToken.materialInfo(tokenId)).verificationState).to.equal(
      VerificationState.TRANSACTION_VERIFIED
    );
    expect(record.signer).to.equal(brand.address);
    expect(record.referenceId).to.equal(101);
  });

  it("소재 잔액이 없는 Brand의 거래 검증을 거부한다", async function () {
    const { materialToken, supplier, brand, tokenId } = await mintedFixture();
    await materialToken.connect(supplier).verifySource(tokenId, EVIDENCE_HASH);

    await expect(
      materialToken
        .connect(brand)
        .verifyTransaction(tokenId, 101, TRANSACTION_EVIDENCE_HASH)
    ).to.be.revertedWith("MaterialToken: brand has no material balance");
  });

  it("Processor가 가공 증빙을 서명하고 PROCESSING_VERIFIED로 변경한다", async function () {
    const { materialToken, supplier, brand, processor, tokenId } =
      await mintedFixture();
    await materialToken.connect(supplier).verifySource(tokenId, EVIDENCE_HASH);
    await materialToken
      .connect(supplier)
      .safeTransferFrom(supplier.address, brand.address, tokenId, 10_000, "0x");
    await materialToken
      .connect(brand)
      .verifyTransaction(tokenId, 101, TRANSACTION_EVIDENCE_HASH);

    await expect(
      materialToken
        .connect(processor)
        .verifyProcessing(tokenId, 202, PROCESSING_EVIDENCE_HASH)
    )
      .to.emit(materialToken, "ProcessingVerified")
      .withArgs(tokenId, 202, processor.address, PROCESSING_EVIDENCE_HASH);

    const record = await materialToken.verificationRecord(
      tokenId,
      VerificationState.PROCESSING_VERIFIED
    );
    expect((await materialToken.materialInfo(tokenId)).verificationState).to.equal(
      VerificationState.PROCESSING_VERIFIED
    );
    expect(record.signer).to.equal(processor.address);
    expect(record.referenceId).to.equal(202);
    expect(record.evidenceHash).to.equal(PROCESSING_EVIDENCE_HASH);
  });

  it("Processor 권한이 없는 계정의 가공 검증을 거부한다", async function () {
    const { materialToken, supplier, brand, outsider, tokenId } =
      await mintedFixture();
    await materialToken.connect(supplier).verifySource(tokenId, EVIDENCE_HASH);
    await materialToken
      .connect(supplier)
      .safeTransferFrom(supplier.address, brand.address, tokenId, 10_000, "0x");
    await materialToken
      .connect(brand)
      .verifyTransaction(tokenId, 101, TRANSACTION_EVIDENCE_HASH);

    await expect(
      materialToken
        .connect(outsider)
        .verifyProcessing(tokenId, 202, PROCESSING_EVIDENCE_HASH)
    )
      .to.be.revertedWithCustomError(materialToken, "AccessControlUnauthorizedAccount")
      .withArgs(outsider.address, await materialToken.PROCESSOR_ROLE());
  });

  it("업무별 잠금을 별도 ID와 용도·참조 ID로 기록한다", async function () {
    const { materialToken, supplier, lockManager, tokenId } = await mintedFixture();

    await materialToken
      .connect(lockManager)
      .createMaterialLock(
        supplier.address,
        tokenId,
        10_000,
        LockPurpose.MARKETPLACE,
        101
      );
    await materialToken
      .connect(lockManager)
      .createMaterialLock(
        supplier.address,
        tokenId,
        5_000,
        LockPurpose.PROCESSING,
        202
      );

    const firstLock = await materialToken.materialLockInfo(1);
    const secondLock = await materialToken.materialLockInfo(2);
    expect(firstLock.operator).to.equal(lockManager.address);
    expect(firstLock.remainingAmount).to.equal(10_000);
    expect(firstLock.purpose).to.equal(LockPurpose.MARKETPLACE);
    expect(firstLock.referenceId).to.equal(101);
    expect(secondLock.purpose).to.equal(LockPurpose.PROCESSING);
    expect(secondLock.referenceId).to.equal(202);
    expect(await materialToken.lockedBalanceOf(supplier.address, tokenId)).to.equal(15_000);
    expect(await materialToken.availableBalance(supplier.address, tokenId)).to.equal(35_000);
  });

  it("잠금을 만든 작업자 외에는 해당 잠금을 해제할 수 없다", async function () {
    const {
      materialToken,
      supplier,
      lockManager,
      anotherLockManager,
      tokenId
    } = await mintedFixture();
    await materialToken
      .connect(lockManager)
      .createMaterialLock(
        supplier.address,
        tokenId,
        10_000,
        LockPurpose.MARKETPLACE,
        1
      );

    await expect(
      materialToken.connect(anotherLockManager).releaseMaterialLock(1, 1_000)
    ).to.be.revertedWith("MaterialToken: caller is not lock operator");
  });

  it("잠금 일부를 해제하고 전량 해제하면 RELEASED 상태가 된다", async function () {
    const { materialToken, supplier, lockManager, tokenId } = await mintedFixture();
    await materialToken
      .connect(lockManager)
      .createMaterialLock(
        supplier.address,
        tokenId,
        10_000,
        LockPurpose.MARKETPLACE,
        1
      );

    await materialToken.connect(lockManager).releaseMaterialLock(1, 4_000);
    expect((await materialToken.materialLockInfo(1)).remainingAmount).to.equal(6_000);
    expect((await materialToken.materialLockInfo(1)).status).to.equal(LockStatus.ACTIVE);

    await materialToken.connect(lockManager).releaseMaterialLock(1, 6_000);
    expect((await materialToken.materialLockInfo(1)).status).to.equal(LockStatus.RELEASED);
    expect(await materialToken.availableBalance(supplier.address, tokenId)).to.equal(50_000);
  });

  it("잠금을 소비하면 잠금 수량 감소와 소유권 이전이 함께 처리된다", async function () {
    const { materialToken, supplier, brand, lockManager, tokenId } =
      await mintedFixture();
    await materialToken
      .connect(lockManager)
      .createMaterialLock(
        supplier.address,
        tokenId,
        10_000,
        LockPurpose.MARKETPLACE,
        1
      );

    await materialToken
      .connect(lockManager)
      .consumeMaterialLock(1, brand.address, 10_000);

    expect((await materialToken.materialLockInfo(1)).status).to.equal(LockStatus.CONSUMED);
    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(10_000);
    expect(await materialToken.lockedBalanceOf(supplier.address, tokenId)).to.equal(0);
  });

  it("잠긴 수량을 침범하는 직접 전송과 소각을 거부한다", async function () {
    const { materialToken, supplier, brand, lockManager, tokenId } =
      await mintedFixture();
    await materialToken
      .connect(lockManager)
      .createMaterialLock(
        supplier.address,
        tokenId,
        10_000,
        LockPurpose.PRODUCTION,
        1
      );

    await expect(
      materialToken
        .connect(supplier)
        .safeTransferFrom(supplier.address, brand.address, tokenId, 41_000, "0x")
    ).to.be.revertedWith("MaterialToken: amount exceeds available balance");
    await expect(
      materialToken
        .connect(supplier)
        .burnMaterial(supplier.address, tokenId, 41_000, BurnReason.DISCARDED)
    ).to.be.revertedWith("MaterialToken: amount exceeds available balance");
  });

  it("배치 전송에 같은 소재 ID를 반복해도 잠긴 수량을 침범할 수 없다", async function () {
    const { materialToken, supplier, brand, lockManager, tokenId } =
      await mintedFixture();
    await materialToken
      .connect(lockManager)
      .createMaterialLock(
        supplier.address,
        tokenId,
        10_000,
        LockPurpose.MARKETPLACE,
        1
      );

    await expect(
      materialToken
        .connect(supplier)
        .safeBatchTransferFrom(
          supplier.address,
          brand.address,
          [tokenId, tokenId],
          [25_000, 25_000],
          "0x"
        )
    ).to.be.revertedWith("MaterialToken: amount exceeds available balance");
  });

  it("소재 일부를 소각하면 잔여 공급량이 감소한다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await materialToken
      .connect(supplier)
      .burnMaterial(supplier.address, tokenId, 12_000, BurnReason.PROCESSING);

    expect(await materialToken.remainingSupply(tokenId)).to.equal(38_000);
    expect((await materialToken.materialInfo(tokenId)).status).to.equal(MaterialStatus.ACTIVE);
  });

  it("전량 소각된 소재를 DEPLETED 상태로 변경한다", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await expect(
      materialToken
        .connect(supplier)
        .burnMaterial(supplier.address, tokenId, 50_000, BurnReason.PRODUCTION)
    )
      .to.emit(materialToken, "MaterialDepleted")
      .withArgs(tokenId);

    expect((await materialToken.materialInfo(tokenId)).status).to.equal(MaterialStatus.DEPLETED);
  });

  it("다른 소유자의 소재를 소각할 수 없다", async function () {
    const { materialToken, supplier, outsider, tokenId } = await mintedFixture();

    await expect(
      materialToken
        .connect(outsider)
        .burnMaterial(supplier.address, tokenId, 1_000, BurnReason.DISCARDED)
    ).to.be.revertedWith("MaterialToken: caller is not owner nor approved");
  });
});
