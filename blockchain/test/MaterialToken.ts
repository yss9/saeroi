import { expect } from "chai";
import { ethers } from "hardhat";

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
      verifierRole
    };
  }

  //1. 소재 토큰 발행 성공
  it("mints a material token and stores material metadata", async function () {
    const { materialToken, supplier } = await deployMaterialTokenFixture();
    const tokenId = 1;
    const amount = 50_000;
    const metadataUri = "ipfs://material-denim-batch-1.json";

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(supplier.address, tokenId, amount, metadataUri)
    )
      .to.emit(materialToken, "MaterialMinted")
      .withArgs(supplier.address, supplier.address, tokenId, amount, metadataUri);

    expect(await materialToken.balanceOf(supplier.address, tokenId)).to.equal(
      amount
    );

    const material = await materialToken.materialInfo(tokenId);
    expect(material.metadataUri).to.equal(metadataUri);
    expect(material.totalMinted).to.equal(amount);
    expect(material.exists).to.equal(true);
  });

  //2. 소재 일부 전송 성공
  it("supports partial transfer between supplier and brand", async function () {
    const { materialToken, supplier, brand } = await deployMaterialTokenFixture();
    const tokenId = 1;

    await materialToken
      .connect(supplier)
      .mintMaterial(
        supplier.address,
        tokenId,
        50_000,
        "ipfs://material-denim-batch-1.json"
      );

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
        .mintMaterial(
          outsider.address,
          1,
          50_000,
          "ipfs://material-denim-batch-1.json"
        )
    ).to.be.revertedWithCustomError(
      materialToken,
      "AccessControlUnauthorizedAccount"
    );
  });

  //4. 같은 tokenId의 metadata 변경 실패
  it("keeps metadata immutable for the same token id", async function () {
    const { materialToken, supplier } = await deployMaterialTokenFixture();

    await materialToken
      .connect(supplier)
      .mintMaterial(
        supplier.address,
        1,
        50_000,
        "ipfs://material-denim-batch-1.json"
      );

    await expect(
      materialToken
        .connect(supplier)
        .mintMaterial(
          supplier.address,
          1,
          10_000,
          "ipfs://different-material.json"
        )
    ).to.be.revertedWith("MaterialToken: metadata mismatch");
  });
});
