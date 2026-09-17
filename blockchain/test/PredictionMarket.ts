import { expect } from "chai";
import { ethers } from "hardhat";
import { time } from "@nomicfoundation/hardhat-network-helpers";

const hash = (value: string) => ethers.keccak256(ethers.toUtf8Bytes(value));
const point = (value: string) => ethers.parseEther(value);

describe("PredictionMarket", function () {
  async function fixture() {
    const [admin, supplier, brand, oracle, consumerA, consumerB, consumerC, outsider] =
      await ethers.getSigners();

    const MaterialToken = await ethers.getContractFactory("MaterialToken");
    const materialToken = await MaterialToken.deploy("ipfs://materials/{id}", admin.address);
    const ProductionRegistry = await ethers.getContractFactory("ProductionRegistry");
    const production = await ProductionRegistry.deploy(await materialToken.getAddress(), admin.address);

    await materialToken.grantRole(await materialToken.SUPPLIER_ROLE(), supplier.address);
    await materialToken.grantRole(await materialToken.BRAND_ROLE(), brand.address);
    await materialToken.grantRole(
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
      await production.getAddress()
    );
    await materialToken.grantRole(
      await materialToken.MATERIAL_WORKFLOW_ROLE(),
      await production.getAddress()
    );
    await production.grantRole(await production.BRAND_ROLE(), brand.address);
    await materialToken
      .connect(supplier)
      .mintMaterial(brand.address, 20_000, 0, "ipfs://virgin", hash("source"));

    const PredictionPoint = await ethers.getContractFactory("PredictionPoint");
    const predictionPoint = await PredictionPoint.deploy(admin.address, point("1000000"));
    const PredictionMarket = await ethers.getContractFactory("PredictionMarket");
    const market = await PredictionMarket.deploy(
      await predictionPoint.getAddress(),
      await production.getAddress(),
      admin.address
    );
    await market.grantRole(await market.ORACLE_ROLE(), oracle.address);

    for (const consumer of [consumerA, consumerB, consumerC, brand, oracle]) {
      await predictionPoint.transfer(consumer.address, point("1000"));
      await predictionPoint.connect(consumer).approve(await market.getAddress(), ethers.MaxUint256);
    }

    async function confirmBatch(productQuantity = 10) {
      await production.connect(brand).createBatch(productQuantity);
      await production.connect(brand).submitPlanAndLock(
        1,
        hash("production"),
        [1],
        [20_000],
        [18_000],
        [1_000],
        [1_000],
        ["ipfs://scrap"],
        [hash("scrap")]
      );
      await production.connect(brand).confirmProduction(1);
    }

    async function createMarket(bounds = [2, 5, 10]) {
      const now = await time.latest();
      const bettingEndsAt = now + 100;
      const salesStartAt = now + 200;
      const salesEndAt = now + 1_000;
      await market.createMarket(1, 101, 59_000, bettingEndsAt, salesStartAt, salesEndAt, bounds);
      return { bettingEndsAt, salesStartAt, salesEndAt };
    }

    return {
      admin,
      brand,
      oracle,
      consumerA,
      consumerB,
      consumerC,
      outsider,
      production,
      predictionPoint,
      market,
      confirmBatch,
      createMarket
    };
  }

  it("확정 배치의 생산수량으로 변경 불가능한 판매량 구간 시장을 만든다", async function () {
    const { brand, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    const period = await createMarket();

    const created = await market.getMarket(1);
    expect(created.batchId).to.equal(1);
    expect(created.productModelId).to.equal(101);
    expect(created.unitPrice).to.equal(59_000);
    expect(created.productionQuantity).to.equal(10);
    expect(created.brand).to.equal(brand.address);
    expect(created.bettingEndsAt).to.equal(period.bettingEndsAt);
    expect(created.salesStartAt).to.equal(period.salesStartAt);
    expect(created.salesEndAt).to.equal(period.salesEndAt);
    expect(await market.getBucketUpperBounds(1)).to.deep.equal([2n, 5n, 10n]);
    expect(await market.batchMarketId(1)).to.equal(1);
  });

  it("미확정 배치, 잘못된 구간과 동일 배치의 중복 시장을 거부한다", async function () {
    const { brand, production, market, confirmBatch, createMarket } = await fixture();
    const now = await time.latest();

    await production.connect(brand).createBatch(10);
    await expect(
      market.createMarket(1, 101, 59_000, now + 100, now + 200, now + 1_000, [2, 5, 10])
    ).to.be.revertedWith("PredictionMarket: batch is not confirmed");

    await production.connect(brand).submitPlanAndLock(
      1, hash("production"), [1], [20_000], [18_000], [1_000], [1_000], ["ipfs://scrap"], [hash("scrap")]
    );
    await production.connect(brand).confirmProduction(1);
    await expect(
      market.createMarket(1, 101, 59_000, now + 100, now + 200, now + 1_000, [5, 5, 10])
    ).to.be.revertedWith("PredictionMarket: buckets not increasing");
    await expect(
      market.createMarket(1, 101, 59_000, now + 100, now + 200, now + 1_000, [2, 5, 9])
    ).to.be.revertedWith("PredictionMarket: buckets must cover production");

    await createMarket();
    await expect(
      market.createMarket(1, 102, 60_000, now + 100, now + 200, now + 1_000, [2, 5, 10])
    ).to.be.revertedWith("PredictionMarket: batch already used");
    void confirmBatch;
  });

  it("소비자 베팅을 에스크로하고 구간별·사용자별 포지션을 기록한다", async function () {
    const { consumerA, predictionPoint, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    await createMarket();
    const before = await predictionPoint.balanceOf(consumerA.address);

    await expect(market.connect(consumerA).placeBet(1, 1, point("100")))
      .to.emit(market, "BetPlaced")
      .withArgs(1, consumerA.address, 1, point("100"));

    expect(await predictionPoint.balanceOf(consumerA.address)).to.equal(before - point("100"));
    expect(await predictionPoint.balanceOf(await market.getAddress())).to.equal(point("100"));
    expect(await market.userBucketStake(1, consumerA.address, 1)).to.equal(point("100"));
    expect(await market.userTotalStake(1, consumerA.address)).to.equal(point("100"));
    expect(await market.bucketPool(1, 1)).to.equal(point("100"));
  });

  it("브랜드·오라클·관리자와 마감 이후 베팅을 컨트랙트에서 거부한다", async function () {
    const { admin, brand, oracle, consumerA, predictionPoint, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    const { bettingEndsAt } = await createMarket();
    await predictionPoint.approve(await market.getAddress(), ethers.MaxUint256);

    await expect(market.connect(brand).placeBet(1, 0, 1)).to.be.revertedWith("PredictionMarket: brand cannot bet");
    await expect(market.connect(oracle).placeBet(1, 0, 1)).to.be.revertedWith("PredictionMarket: oracle cannot bet");
    await expect(market.connect(admin).placeBet(1, 0, 1)).to.be.revertedWith("PredictionMarket: admin cannot bet");

    await time.increaseTo(bettingEndsAt);
    await expect(market.connect(consumerA).placeBet(1, 0, 1)).to.be.revertedWith("PredictionMarket: betting is closed");
  });

  it("판매 종료 후 오라클 결과로 정답 구간을 판정하고 승자에게 전체 풀을 분배한다", async function () {
    const { oracle, consumerA, consumerB, consumerC, predictionPoint, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    const { salesEndAt } = await createMarket();
    await market.connect(consumerA).placeBet(1, 1, point("100"));
    await market.connect(consumerB).placeBet(1, 1, point("300"));
    await market.connect(consumerC).placeBet(1, 2, point("600"));

    await time.increaseTo(salesEndAt + 1);
    await expect(market.connect(oracle).submitResult(1, 4, hash("sales-ledger")))
      .to.emit(market, "MarketResolved")
      .withArgs(1, 4, 1, hash("sales-ledger"), false);

    const beforeA = await predictionPoint.balanceOf(consumerA.address);
    const beforeB = await predictionPoint.balanceOf(consumerB.address);
    await market.connect(consumerA).claim(1);
    await market.connect(consumerB).claim(1);
    expect(await predictionPoint.balanceOf(consumerA.address)).to.equal(beforeA + point("250"));
    expect(await predictionPoint.balanceOf(consumerB.address)).to.equal(beforeB + point("750"));
    expect(await predictionPoint.balanceOf(await market.getAddress())).to.equal(0);
    await expect(market.connect(consumerA).claim(1)).to.be.revertedWith("PredictionMarket: already claimed");
    await expect(market.connect(consumerC).claim(1)).to.be.revertedWith("PredictionMarket: no winning stake");
  });

  it("판매 종료 전 판정, 생산량 초과와 중복 결과 제출을 거부한다", async function () {
    const { oracle, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    const { salesEndAt } = await createMarket();

    await expect(market.connect(oracle).submitResult(1, 4, hash("early"))).to.be.revertedWith(
      "PredictionMarket: sales period is not over"
    );
    await time.increaseTo(salesEndAt + 1);
    await expect(market.connect(oracle).submitResult(1, 11, hash("too-many"))).to.be.revertedWith(
      "PredictionMarket: sales exceed production"
    );
    await market.connect(oracle).submitResult(1, 4, hash("final"));
    await expect(market.connect(oracle).submitResult(1, 4, hash("again"))).to.be.revertedWith(
      "PredictionMarket: market is not open"
    );
  });

  it("정답 구간에 베팅이 없으면 모든 참가자가 원금을 환불받는다", async function () {
    const { oracle, consumerA, consumerB, predictionPoint, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    const { salesEndAt } = await createMarket();
    await market.connect(consumerA).placeBet(1, 0, point("100"));
    await market.connect(consumerB).placeBet(1, 1, point("200"));
    await time.increaseTo(salesEndAt + 1);
    await market.connect(oracle).submitResult(1, 9, hash("sales-ledger"));

    const beforeA = await predictionPoint.balanceOf(consumerA.address);
    const beforeB = await predictionPoint.balanceOf(consumerB.address);
    await market.connect(consumerA).claim(1);
    await market.connect(consumerB).claim(1);
    expect(await predictionPoint.balanceOf(consumerA.address)).to.equal(beforeA + point("100"));
    expect(await predictionPoint.balanceOf(consumerB.address)).to.equal(beforeB + point("200"));
    expect(await predictionPoint.balanceOf(await market.getAddress())).to.equal(0);
  });

  it("활성 베팅이 있는 시장은 조기 취소할 수 없고 판매 종료 후 취소·환불할 수 있다", async function () {
    const { consumerA, predictionPoint, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    const { salesEndAt } = await createMarket();
    await market.connect(consumerA).placeBet(1, 0, point("100"));

    await expect(market.cancelMarket(1)).to.be.revertedWith("PredictionMarket: active bets exist");
    await time.increaseTo(salesEndAt + 1);
    await market.cancelMarket(1);
    const before = await predictionPoint.balanceOf(consumerA.address);
    await market.connect(consumerA).claim(1);
    expect(await predictionPoint.balanceOf(consumerA.address)).to.equal(before + point("100"));
  });

  it("베팅 후 오라클 권한을 받은 주소는 해당 시장 결과를 제출할 수 없다", async function () {
    const { consumerA, market, confirmBatch, createMarket } = await fixture();
    await confirmBatch();
    const { salesEndAt } = await createMarket();
    await market.connect(consumerA).placeBet(1, 0, point("100"));
    await market.grantRole(await market.ORACLE_ROLE(), consumerA.address);
    await time.increaseTo(salesEndAt + 1);

    await expect(market.connect(consumerA).submitResult(1, 1, hash("conflict"))).to.be.revertedWith(
      "PredictionMarket: bettor cannot resolve"
    );
  });
});
