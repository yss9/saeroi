import { expect } from "chai";
import { ethers } from "hardhat";

const hash = (value: string) => ethers.keccak256(ethers.toUtf8Bytes(value));

describe("Processing/Production 통합", function () {
  async function fixture() {
    const [admin, supplier, brand, processor, outsider] = await ethers.getSigners();
    const Token = await ethers.getContractFactory("MaterialToken");
    const token = await Token.deploy("ipfs://materials/{id}", admin.address);
    const Processing = await ethers.getContractFactory("ProcessingRegistry");
    const processing = await Processing.deploy(await token.getAddress(), admin.address);
    const Production = await ethers.getContractFactory("ProductionRegistry");
    const production = await Production.deploy(await token.getAddress(), admin.address);

    await token.grantRole(await token.SUPPLIER_ROLE(), supplier.address);
    await token.grantRole(await token.BRAND_ROLE(), brand.address);
    for (const registry of [processing, production]) {
      await token.grantRole(await token.MATERIAL_LOCK_MANAGER_ROLE(), await registry.getAddress());
      await token.grantRole(await token.MATERIAL_WORKFLOW_ROLE(), await registry.getAddress());
    }
    await processing.grantRole(await processing.BRAND_ROLE(), brand.address);
    await processing.grantRole(await processing.PROCESSOR_ROLE(), processor.address);
    await production.grantRole(await production.BRAND_ROLE(), brand.address);

    await token.connect(supplier).mintMaterial(brand.address, 20_000, 1, "ipfs://input", hash("source"));
    await token.connect(supplier).mintMaterial(brand.address, 20_000, 0, "ipfs://virgin", hash("virgin"));
    return { admin, supplier, brand, processor, outsider, token, processing, production };
  }

  it("배송·인수·결과 제출·Brand 승인 후 입력을 소각하고 여러 Child와 lineage를 만든다", async function () {
    const { brand, processor, token, processing } = await fixture();
    await processing.connect(brand).createJob(processor.address, [1], [20_000], [0]);
    await processing.connect(processor).acceptJob(1);
    await processing.connect(brand).markShipped(1, hash("shipping"));
    await processing.connect(processor).confirmReceipt(1);
    await processing.connect(processor).startProcessing(1);
    await processing.connect(processor).submitProcessingResult(
      1,
      [0, 0, 0],
      [14_000, 3_000, 1_000],
      [1, 2, 3],
      ["ipfs://denim", "ipfs://lining", "ipfs://metal"],
      [hash("denim"), hash("lining"), hash("metal")],
      hash("processing-result")
    );

    await expect(processing.connect(brand).approveProcessingResult(1)).to.emit(processing, "ProcessingApproved");
    expect((await processing.getJob(1)).status).to.equal(7);
    expect(await token.balanceOf(brand.address, 1)).to.equal(0);
    expect(await token.balanceOf(brand.address, 3)).to.equal(14_000);
    expect(await token.balanceOf(brand.address, 4)).to.equal(3_000);
    expect(await token.balanceOf(brand.address, 5)).to.equal(1_000);
    expect((await token.materialInfo(3)).grade).to.equal(1);
    expect(await processing.parentsOf(3)).to.deep.equal([1n]);
    await expect(processing.connect(brand).approveProcessingResult(1)).to.be.revertedWith("Processing: invalid status");
  });

  it("출력 수량 초과를 막고 반려 시 입력 잠금을 해제한다", async function () {
    const { brand, processor, token, processing } = await fixture();
    await processing.connect(brand).createJob(processor.address, [1], [20_000], [0]);
    await processing.connect(processor).acceptJob(1);
    await processing.connect(brand).markShipped(1, hash("shipping"));
    await processing.connect(processor).confirmReceipt(1);
    await processing.connect(processor).startProcessing(1);
    await expect(processing.connect(processor).submitProcessingResult(1, [0], [20_001], [1], ["ipfs://bad"], [hash("bad")], hash("result")))
      .to.be.revertedWith("Processing: output exceeds input");
    await processing.connect(processor).submitProcessingResult(1, [0], [18_000], [1], ["ipfs://ok"], [hash("ok")], hash("result"));
    await processing.connect(brand).rejectProcessingResult(1);
    expect(await token.availableBalance(brand.address, 1)).to.equal(20_000);
  });

  it("각 Output을 특정 Input에 귀속시켜 N대1 혼합을 막는다", async function () {
    const { supplier, brand, processor, token, processing } = await fixture();
    await processing.connect(brand).createJob(processor.address, [1, 2], [20_000, 10_000], [1, 2]);
    await processing.connect(processor).acceptJob(1);
    await processing.connect(brand).markShipped(1, hash("shipping"));
    await processing.connect(processor).confirmReceipt(1);
    await processing.connect(processor).startProcessing(1);
    await processing.connect(processor).submitProcessingResult(
      1, [0, 1], [18_000, 9_000], [1, 2], ["ipfs://waste-output", "ipfs://virgin-output"],
      [hash("waste-output"), hash("virgin-output")], hash("result")
    );
    await processing.connect(brand).approveProcessingResult(1);
    expect((await token.materialInfo(3)).rootOrigin).to.equal(1);
    expect((await token.materialInfo(4)).rootOrigin).to.equal(0);
    expect(await processing.parentsOf(3)).to.deep.equal([1n]);
    expect(await processing.parentsOf(4)).to.deep.equal([2n]);
  });

  it("배송 전 가공 요청이 취소되면 Job을 CANCELED로 닫고 Lock을 해제한다", async function () {
    const { brand, processor, token, processing } = await fixture();
    await processing.connect(brand).createJob(processor.address, [1], [20_000], [0]);
    await processing.connect(brand).cancelJob(1);
    expect((await processing.getJob(1)).status).to.equal(10);
    expect(await token.availableBalance(brand.address, 1)).to.equal(20_000);
  });

  it("지정 Processor가 수락해야만 Brand가 소재를 발송할 수 있다", async function () {
    const { admin, brand, processor, outsider, processing } = await fixture();
    await expect(
      processing.connect(brand).createJob(outsider.address, [1], [20_000], [0])
    ).to.be.revertedWith("Processing: processor role required");
    await processing.connect(brand).createJob(processor.address, [1], [20_000], [0]);

    await expect(
      processing.connect(brand).markShipped(1, hash("shipping"))
    ).to.be.revertedWith("Processing: invalid status");
    await expect(processing.connect(admin).acceptJob(1)).to.be.revertedWith(
      "Processing: caller is not processor"
    );
    await expect(processing.connect(processor).acceptJob(1)).to.emit(
      processing,
      "ProcessingJobAccepted"
    );
    expect((await processing.getJob(1)).status).to.equal(2);
    await processing.connect(brand).markShipped(1, hash("shipping"));
  });

  it("지정 Processor가 요청을 거절하면 Job을 종료하고 입력 잠금을 해제한다", async function () {
    const { brand, processor, token, processing } = await fixture();
    await processing.connect(brand).createJob(processor.address, [1], [20_000], [0]);

    await expect(processing.connect(processor).rejectJob(1)).to.emit(
      processing,
      "ProcessingJobDeclined"
    );
    expect((await processing.getJob(1)).status).to.equal(9);
    expect(await token.availableBalance(brand.address, 1)).to.equal(20_000);
    await expect(processing.connect(processor).acceptJob(1)).to.be.revertedWith(
      "Processing: invalid status"
    );
  });

  it("Processor 수락 후에도 배송 전이면 Brand가 취소하고 잠금을 해제할 수 있다", async function () {
    const { brand, processor, token, processing } = await fixture();
    await processing.connect(brand).createJob(processor.address, [1], [20_000], [0]);
    await processing.connect(processor).acceptJob(1);

    await processing.connect(brand).cancelJob(1);
    expect((await processing.getJob(1)).status).to.equal(10);
    expect(await token.availableBalance(brand.address, 1)).to.equal(20_000);
  });

  it("생산 수량 보존 후 소재를 한 번만 소비하고 Scrap 및 ProductItem 연결을 만든다", async function () {
    const { brand, token, production } = await fixture();
    await production.connect(brand).createBatch(2);
    await production.connect(brand).submitPlanAndLock(
      1, hash("batch"), [2], [20_000], [15_000], [3_000], [2_000], ["ipfs://scrap"], [hash("scrap")]
    );
    await expect(production.connect(brand).confirmProduction(1)).to.emit(production, "ProductionConfirmed");
    await production.connect(brand).linkProductItems(1, [101, 102]);
    expect((await production.getBatch(1)).status).to.equal(3);
    expect(await token.balanceOf(brand.address, 2)).to.equal(0);
    expect(await token.balanceOf(brand.address, 3)).to.equal(3_000);
    expect(await production.productItemBatch(101)).to.equal(1);
    expect(await production.getProductItems(1)).to.deep.equal([101n, 102n]);
    expect(await production.parentsOfScrap(3)).to.deep.equal([2n]);
    await expect(production.connect(brand).confirmProduction(1)).to.be.revertedWith("Production: invalid status");
  });

  it("생산 배분 불일치와 ProductItem 중복 연결을 거부한다", async function () {
    const { brand, production } = await fixture();
    await production.connect(brand).createBatch(1);
    await expect(production.connect(brand).submitPlanAndLock(1, hash("batch"), [1], [20_000], [10_000], [2_000], [1_000], ["ipfs://scrap"], [hash("scrap")]))
      .to.be.revertedWith("Production: quantity not conserved");
  });

  it("WASTE 원재료는 가공 없이 생산에 직접 투입할 수 없다", async function () {
    const { brand, production } = await fixture();
    await production.connect(brand).createBatch(1);
    await expect(production.connect(brand).submitPlanAndLock(
      1, hash("batch"), [1], [20_000], [18_000], [1_000], [1_000], ["ipfs://scrap"], [hash("scrap")]
    )).to.be.revertedWith("Production: waste must be processed");
  });

  it("가공된 WASTE와 VIRGIN의 제품 투입량으로 업사이클 비율을 계산한다", async function () {
    const { brand, processor, processing, production } = await fixture();
    await processing.connect(brand).createJob(processor.address, [1], [10_000], [0]);
    await processing.connect(processor).acceptJob(1);
    await processing.connect(brand).markShipped(1, hash("shipping"));
    await processing.connect(processor).confirmReceipt(1);
    await processing.connect(processor).startProcessing(1);
    await processing.connect(processor).submitProcessingResult(
      1, [0], [10_000], [1], ["ipfs://processed-waste"], [hash("processed-waste")], hash("result")
    );
    await processing.connect(brand).approveProcessingResult(1);

    await production.connect(brand).createBatch(1);
    await production.connect(brand).submitPlanAndLock(
      1, hash("mixed-batch"), [2, 3], [10_000, 10_000], [8_000, 8_000],
      [1_000, 1_000], [1_000, 1_000], ["ipfs://virgin-scrap", "ipfs://waste-scrap"],
      [hash("virgin-scrap"), hash("waste-scrap")]
    );
    await production.connect(brand).confirmProduction(1);
    expect((await production.getBatch(1)).upcycledRatioBps).to.equal(5_000);
  });

  it("Production 온체인 처리 실패 시 모든 잠금을 해제한다", async function () {
    const { brand, token, production } = await fixture();
    await production.connect(brand).createBatch(1);
    await production.connect(brand).submitPlanAndLock(
      1, hash("batch"), [2], [20_000], [18_000], [1_000], [1_000], ["ipfs://scrap"], [hash("scrap")]
    );
    await production.markFailed(1);
    expect((await production.getBatch(1)).status).to.equal(4);
    expect(await token.availableBalance(brand.address, 2)).to.equal(20_000);
  });
});
