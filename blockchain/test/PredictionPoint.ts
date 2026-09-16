import { expect } from "chai";
import { ethers } from "hardhat";

describe("PredictionPoint", function () {
  it("관리자에게 초기 물량과 발행 권한을 부여한다", async function () {
    const [admin] = await ethers.getSigners();
    const Point = await ethers.getContractFactory("PredictionPoint");
    const point = await Point.deploy(admin.address, ethers.parseEther("1000"));

    expect(await point.name()).to.equal("Prediction Point");
    expect(await point.symbol()).to.equal("PPT");
    expect(await point.balanceOf(admin.address)).to.equal(ethers.parseEther("1000"));
    expect(await point.hasRole(await point.MINTER_ROLE(), admin.address)).to.equal(true);
  });

  it("MINTER_ROLE만 포인트를 추가 발행할 수 있다", async function () {
    const [admin, consumer] = await ethers.getSigners();
    const Point = await ethers.getContractFactory("PredictionPoint");
    const point = await Point.deploy(admin.address, 0);

    await expect(point.connect(consumer).mint(consumer.address, 100)).to.be.reverted;
    await point.mint(consumer.address, 100);
    expect(await point.balanceOf(consumer.address)).to.equal(100);
  });

  it("0 주소 관리자와 수령인 발행을 거부한다", async function () {
    const [admin] = await ethers.getSigners();
    const Point = await ethers.getContractFactory("PredictionPoint");

    await expect(Point.deploy(ethers.ZeroAddress, 0)).to.be.revertedWith("PredictionPoint: zero admin");
    const point = await Point.deploy(admin.address, 0);
    await expect(point.mint(ethers.ZeroAddress, 1)).to.be.revertedWith("PredictionPoint: zero recipient");
  });
});
