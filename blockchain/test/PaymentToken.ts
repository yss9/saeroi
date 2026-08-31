import { expect } from "chai";
import { ethers } from "hardhat";

describe("PaymentToken", function () {
  async function deployPaymentTokenFixture() {
    const [admin, supplier, brand, outsider] = await ethers.getSigners();

    const PaymentToken = await ethers.getContractFactory("PaymentToken");
    const paymentToken = await PaymentToken.deploy(admin.address);

    await paymentToken.waitForDeployment();

    const minterRole = await paymentToken.MINTER_ROLE();

    return { paymentToken, admin, supplier, brand, outsider, minterRole };
  }

  //1. 초기 발행량 전부가 admin 에게 있어야 한다
  it("mints the initial supply to the admin", async function () {
    const { paymentToken, admin } = await deployPaymentTokenFixture();
    const initialSupply = await paymentToken.INITIAL_SUPPLY();

    expect(await paymentToken.totalSupply()).to.equal(initialSupply);
    expect(await paymentToken.balanceOf(admin.address)).to.equal(initialSupply);
    expect(initialSupply).to.equal(ethers.parseEther("1000000"));
  });

  //2. 토큰 기본 정보
  it("exposes token metadata", async function () {
    const { paymentToken } = await deployPaymentTokenFixture();

    expect(await paymentToken.name()).to.equal("Circular Fashion Token");
    expect(await paymentToken.symbol()).to.equal("CFT");
    expect(await paymentToken.decimals()).to.equal(18);
  });

  //3. admin 이 업체 지갑에 초기 물량을 분배한다
  it("distributes tokens from admin to company wallets", async function () {
    const { paymentToken, admin, supplier, brand } =
      await deployPaymentTokenFixture();
    const amount = ethers.parseEther("10000");
    const initialSupply = await paymentToken.INITIAL_SUPPLY();

    await paymentToken.transfer(supplier.address, amount);
    await paymentToken.transfer(brand.address, amount);

    expect(await paymentToken.balanceOf(supplier.address)).to.equal(amount);
    expect(await paymentToken.balanceOf(brand.address)).to.equal(amount);
    expect(await paymentToken.balanceOf(admin.address)).to.equal(
      initialSupply - amount * 2n
    );
  });

  //4. 업체 간 결제 전송
  it("supports transfer between companies", async function () {
    const { paymentToken, supplier, brand } = await deployPaymentTokenFixture();
    const funded = ethers.parseEther("10000");
    const payment = ethers.parseEther("3000");

    await paymentToken.transfer(brand.address, funded);
    await paymentToken.connect(brand).transfer(supplier.address, payment);

    expect(await paymentToken.balanceOf(brand.address)).to.equal(
      funded - payment
    );
    expect(await paymentToken.balanceOf(supplier.address)).to.equal(payment);
  });

  //5. 잔액보다 많이 보내면 실패
  it("rejects transfer over balance", async function () {
    const { paymentToken, brand, supplier } = await deployPaymentTokenFixture();

    await paymentToken.transfer(brand.address, ethers.parseEther("100"));

    await expect(
      paymentToken
        .connect(brand)
        .transfer(supplier.address, ethers.parseEther("101"))
    ).to.be.revertedWithCustomError(paymentToken, "ERC20InsufficientBalance");
  });

  //6. MINTER_ROLE 이 없으면 추가 발행 불가
  it("rejects minting from an account without minter role", async function () {
    const { paymentToken, outsider } = await deployPaymentTokenFixture();

    await expect(
      paymentToken
        .connect(outsider)
        .mint(outsider.address, ethers.parseEther("1"))
    ).to.be.revertedWithCustomError(
      paymentToken,
      "AccessControlUnauthorizedAccount"
    );
  });

  //7. admin 은 추가 발행 가능
  it("allows the admin to mint additional supply", async function () {
    const { paymentToken, admin, supplier } = await deployPaymentTokenFixture();
    const amount = ethers.parseEther("5000");
    const initialSupply = await paymentToken.INITIAL_SUPPLY();

    await paymentToken.connect(admin).mint(supplier.address, amount);

    expect(await paymentToken.balanceOf(supplier.address)).to.equal(amount);
    expect(await paymentToken.totalSupply()).to.equal(initialSupply + amount);
  });
});
