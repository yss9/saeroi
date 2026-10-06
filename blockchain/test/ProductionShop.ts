import { expect } from "chai";
import { ethers } from "hardhat";
import { anyValue } from "@nomicfoundation/hardhat-chai-matchers/withArgs";

const hash = (value: string) => ethers.keccak256(ethers.toUtf8Bytes(value));
let operationSequence = 0;
const op = () => hash(`operation-${++operationSequence}`);

describe("ProductionRegistry Shop 연결", function () {
  async function fixture() {
    const [admin, supplier, brand, otherBrand] = await ethers.getSigners();
    const Token = await ethers.getContractFactory("MaterialToken");
    const token = await Token.deploy("ipfs://materials/{id}", admin.address);
    const Production = await ethers.getContractFactory("ProductionRegistry");
    const production = await Production.deploy(await token.getAddress(), admin.address);

    await token.grantRole(await token.SUPPLIER_ROLE(), supplier.address);
    await token.grantRole(await token.BRAND_ROLE(), brand.address);
    await token.grantRole(await token.MATERIAL_LOCK_MANAGER_ROLE(), await production.getAddress());
    await token.grantRole(await token.MATERIAL_WORKFLOW_ROLE(), await production.getAddress());
    await production.grantRole(await production.BRAND_ROLE(), brand.address);
    await production.grantRole(await production.BRAND_ROLE(), otherBrand.address);
    await token.connect(supplier).mintMaterial(brand.address, 40_000, 0, "ipfs://virgin", hash("source"), op());

    return { brand, otherBrand, production };
  }

  async function confirmBatch(
    production: any,
    brand: any,
    productQuantity: number,
    batchId = 1,
    inputAmount = 20_000
  ) {
    await production.connect(brand).createBatch(productQuantity, op());
    await production.connect(brand).submitPlanAndLock(
      batchId,
      hash(`production-evidence-${batchId}`),
      [1],
      [inputAmount],
      [inputAmount],
      [0],
      [0],
      [""],
      [ethers.ZeroHash]
    , op());
    await production.connect(brand).confirmProduction(batchId, op());
  }

  it("확정된 Batch에 ProductItem을 연결하고 공개 Passport 증명을 조회한다", async function () {
    const { brand, production } = await fixture();
    await confirmBatch(production, brand, 2);

    await expect(production.connect(brand).linkProductItems(1, [101, 102], op()))
      .to.emit(production, "ProductLinked")
      .withArgs(1, 101, brand.address, anyValue);

    const proof = await production.getProductPassportProof(101);
    expect(proof.productItemId).to.equal(101);
    expect(proof.batchId).to.equal(1);
    expect(proof.brand).to.equal(brand.address);
    expect(proof.upcycledRatioBps).to.equal(0);
    expect(proof.evidenceHash).to.equal(hash("production-evidence-1"));
    expect(proof.confirmedAt).to.be.greaterThan(0);
    expect(await production.getProductItems(1)).to.deep.equal([101n, 102n]);
  });

  it("Batch 소유 Brand가 아니면 ProductItem을 연결할 수 없다", async function () {
    const { brand, otherBrand, production } = await fixture();
    await confirmBatch(production, brand, 1);

    await expect(production.connect(otherBrand).linkProductItems(1, [101], op()))
      .to.be.revertedWith("Production: caller is not brand");
  });

  it("ProductItem 개수 불일치와 중복 연결을 거부한다", async function () {
    const { brand, production } = await fixture();
    await confirmBatch(production, brand, 2);

    await expect(production.connect(brand).linkProductItems(1, [101], op()))
      .to.be.revertedWith("Production: product count mismatch");
    await expect(production.connect(brand).linkProductItems(1, [101, 101], op()))
      .to.be.revertedWith("Production: duplicate product");

    await production.connect(brand).linkProductItems(1, [101, 102], op());
    await expect(production.connect(brand).linkProductItems(1, [103, 104], op()))
      .to.be.revertedWith("Production: products already linked");
  });

  it("하나의 ProductItem ID를 서로 다른 Batch에 중복 연결할 수 없다", async function () {
    const { brand, production } = await fixture();
    await confirmBatch(production, brand, 1, 1, 20_000);
    await confirmBatch(production, brand, 1, 2, 20_000);
    await production.connect(brand).linkProductItems(1, [101], op());

    await expect(production.connect(brand).linkProductItems(2, [101], op()))
      .to.be.revertedWith("Production: product already linked");
  });

  it("연결되지 않은 ProductItem의 Passport 증명을 반환하지 않는다", async function () {
    const { production } = await fixture();

    await expect(production.getProductPassportProof(999))
      .to.be.revertedWith("Production: product is not linked");
  });
});
