import { expect } from "chai";
import { ethers } from "hardhat";

describe("OperationTracked 멱등성", function () {
  it("성공한 operationId를 다시 보내면 중복 상태 변경을 거부한다", async function () {
    const [admin, supplier] = await ethers.getSigners();
    const Token = await ethers.getContractFactory("MaterialToken");
    const token = await Token.deploy("ipfs://materials/{id}", admin.address);
    await token.grantRole(await token.SUPPLIER_ROLE(), supplier.address);
    const operationId = ethers.keccak256(ethers.toUtf8Bytes("mint-material-42"));
    const evidenceHash = ethers.keccak256(ethers.toUtf8Bytes("evidence"));

    await expect(token.connect(supplier).mintMaterial(
      supplier.address, 1_000, 1, "saeroi://material/42", evidenceHash, operationId,
    )).to.emit(token, "OperationRecorded").withArgs(
      operationId,
      token.interface.getFunction("mintMaterial")!.selector,
      supplier.address,
    );

    await expect(token.connect(supplier).mintMaterial(
      supplier.address, 1_000, 1, "saeroi://material/42", evidenceHash, operationId,
    )).to.be.revertedWith("Operation: already processed");
    expect(await token.nextMaterialId()).to.equal(2);
  });
});
