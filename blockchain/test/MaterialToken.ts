import { expect } from "chai";
import { ethers } from "hardhat";

const Status = {
  NONE: 0,
  REGISTERED: 1,
  VERIFIED: 2,
  LISTED: 3,
  IN_ESCROW: 4,
  PROCESSED: 5
};

const BurnReason = {
  PROCESSED: 0,
  DISCARDED: 1
};

const METADATA_URI = "ipfs://material-denim-batch-1.json";
const DOCUMENT_HASH = ethers.keccak256(
  ethers.toUtf8Bytes("denim-batch-1-certificate.pdf")
);

describe("MaterialToken", function () {
  async function deployMaterialTokenFixture() {
    const [admin, supplier, brand, processor, verifier, outsider] =
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
    const verifierRole = await materialToken.VERIFIER_ROLE();
    const statusManagerRole = await materialToken.STATUS_MANAGER_ROLE();

    await materialToken.grantRole(supplierRole, supplier.address);
    await materialToken.grantRole(brandRole, brand.address);
    await materialToken.grantRole(processorRole, processor.address);
    await materialToken.grantRole(verifierRole, verifier.address);

    return {
      materialToken,
      admin,
      supplier,
      brand,
      processor,
      verifier,
      outsider,
      supplierRole,
      brandRole,
      processorRole,
      verifierRole,
      statusManagerRole
    };
  }

  //공통: 1번 소재를 50,000 발행한 상태
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

  //1. 소재 토큰 발행 성공
  it("mints a material token and stores material metadata", async function () {
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

  //2. 소재 일부 전송 성공
  it("supports partial transfer between supplier and brand", async function () {
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

  //3. 권한 없는 사용자의 발행 실패
  it("rejects minting from an account without supplier role", async function () {
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

  //4. 같은 tokenId의 metadata 변경 실패
  it("keeps metadata immutable for the same token id", async function () {
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

  //5. 증빙 해시 변경 실패
  it("keeps the document hash immutable for the same token id", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();
    const otherHash = ethers.keccak256(ethers.toUtf8Bytes("forged.pdf"));

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(supplier.address, tokenId, 10_000, METADATA_URI, otherHash)
    ).to.be.revertedWith("MaterialToken: documentHash mismatch");
  });

  //6. 빈 증빙 해시로는 발행 불가
  it("rejects minting without a document hash", async function () {
    const { materialToken, supplier } = await deployMaterialTokenFixture();

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(supplier.address, 1, 50_000, METADATA_URI, ethers.ZeroHash)
    ).to.be.revertedWith("MaterialToken: documentHash is empty");
  });

  //7. 증빙 파일 위조 검증
  it("verifies the document hash of a material", async function () {
    const { materialToken, tokenId } = await mintedFixture();
    const forged = ethers.keccak256(ethers.toUtf8Bytes("forged.pdf"));

    expect(await materialToken.verifyDocument(tokenId, DOCUMENT_HASH)).to.equal(
      true
    );
    expect(await materialToken.verifyDocument(tokenId, forged)).to.equal(false);
  });

  //8. 검증자가 소재를 승인한다
  it("lets a verifier move a material to verified status", async function () {
    const { materialToken, verifier, tokenId } = await mintedFixture();

    await expect(materialToken.connect(verifier).verifyMaterial(tokenId))
      .to.emit(materialToken, "MaterialStatusChanged")
      .withArgs(verifier.address, tokenId, Status.REGISTERED, Status.VERIFIED);

    const material = await materialToken.materialInfo(tokenId);
    expect(material.status).to.equal(Status.VERIFIED);
  });

  //9. 검증 권한이 없으면 승인 불가
  it("rejects verification from an account without verifier role", async function () {
    const { materialToken, outsider, tokenId } = await mintedFixture();

    await expect(
      materialToken.connect(outsider).verifyMaterial(tokenId)
    ).to.be.revertedWithCustomError(
      materialToken,
      "AccessControlUnauthorizedAccount"
    );
  });

  //10. 이미 검증된 소재는 다시 검증할 수 없다
  it("rejects verifying a material twice", async function () {
    const { materialToken, verifier, tokenId } = await mintedFixture();

    await materialToken.connect(verifier).verifyMaterial(tokenId);

    await expect(
      materialToken.connect(verifier).verifyMaterial(tokenId)
    ).to.be.revertedWith("MaterialToken: material is not in registered status");
  });

  //11. 판매 등록과 거래 시작 상태 전이
  it("moves through listed and escrow status", async function () {
    const { materialToken, admin, verifier, tokenId } = await mintedFixture();

    await materialToken.connect(verifier).verifyMaterial(tokenId);
    await materialToken.connect(admin).setMaterialStatus(tokenId, Status.LISTED);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.IN_ESCROW);

    const material = await materialToken.materialInfo(tokenId);
    expect(material.status).to.equal(Status.IN_ESCROW);
  });

  //12. 허용되지 않은 상태 전이는 실패
  it("rejects an invalid status transition", async function () {
    const { materialToken, admin, tokenId } = await mintedFixture();

    //REGISTERED 에서 바로 LISTED 로 갈 수 없다
    await expect(
      materialToken.connect(admin).setMaterialStatus(tokenId, Status.LISTED)
    ).to.be.revertedWith("MaterialToken: invalid status transition");
  });

  //13. 상태 변경 권한이 없으면 실패
  it("rejects status change from an account without status manager role", async function () {
    const { materialToken, verifier, outsider, tokenId } = await mintedFixture();

    await materialToken.connect(verifier).verifyMaterial(tokenId);

    await expect(
      materialToken.connect(outsider).setMaterialStatus(tokenId, Status.LISTED)
    ).to.be.revertedWithCustomError(
      materialToken,
      "AccessControlUnauthorizedAccount"
    );
  });

  //14. 거래중인 소재는 이동할 수 없다 (이중 판매 방지)
  it("blocks transfer while the material is in escrow", async function () {
    const { materialToken, admin, verifier, supplier, brand, tokenId } =
      await mintedFixture();

    await materialToken.connect(verifier).verifyMaterial(tokenId);
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

  //15. 거래가 끝나면 다시 이동할 수 있다
  it("allows transfer again after escrow is released", async function () {
    const { materialToken, admin, verifier, supplier, brand, tokenId } =
      await mintedFixture();

    await materialToken.connect(verifier).verifyMaterial(tokenId);
    await materialToken.connect(admin).setMaterialStatus(tokenId, Status.LISTED);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.IN_ESCROW);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.VERIFIED);

    await materialToken
      .connect(supplier)
      .safeTransferFrom(supplier.address, brand.address, tokenId, 1_000, "0x");

    expect(await materialToken.balanceOf(brand.address, tokenId)).to.equal(
      1_000
    );
  });

  //16. 소각하면 잔량이 줄어든다
  it("burns part of a material and reduces the remaining supply", async function () {
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
    //일부만 태웠으므로 아직 소진 상태가 아니다
    expect(material.status).to.equal(Status.REGISTERED);
  });

  //17. 전량 가공 투입되면 소진 상태가 된다
  it("marks the material as processed when fully burned for processing", async function () {
    const { materialToken, supplier, tokenId } = await mintedFixture();

    await materialToken
      .connect(supplier)
      .burnMaterial(supplier.address, tokenId, 50_000, BurnReason.PROCESSED);

    const material = await materialToken.materialInfo(tokenId);
    expect(material.status).to.equal(Status.PROCESSED);
    expect(await materialToken.remainingSupply(tokenId)).to.equal(0);
  });

  //18. 남의 소재는 소각할 수 없다
  it("rejects burning someone else's material", async function () {
    const { materialToken, supplier, outsider, tokenId } = await mintedFixture();

    await expect(
      materialToken
        .connect(outsider)
        .burnMaterial(supplier.address, tokenId, 1_000, BurnReason.DISCARDED)
    ).to.be.revertedWith("MaterialToken: caller is not owner nor approved");
  });

  //19. 거래중인 소재는 소각할 수 없다
  it("rejects burning while the material is in escrow", async function () {
    const { materialToken, admin, verifier, supplier, tokenId } =
      await mintedFixture();

    await materialToken.connect(verifier).verifyMaterial(tokenId);
    await materialToken.connect(admin).setMaterialStatus(tokenId, Status.LISTED);
    await materialToken
      .connect(admin)
      .setMaterialStatus(tokenId, Status.IN_ESCROW);

    await expect(
      materialToken
        .connect(supplier)
        .burnMaterial(supplier.address, tokenId, 1_000, BurnReason.DISCARDED)
    ).to.be.revertedWith("MaterialToken: material is in escrow");
  });

  //20. 소진된 소재에는 추가 발행할 수 없다
  it("rejects minting into a processed material", async function () {
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
