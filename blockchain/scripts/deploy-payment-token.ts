import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

async function main() {
  const [deployer] = await ethers.getSigners();

  const PaymentToken = await ethers.getContractFactory("PaymentToken");
  const paymentToken = await PaymentToken.deploy(deployer.address);

  await paymentToken.waitForDeployment();

  const address = await paymentToken.getAddress();
  const totalSupply = await paymentToken.totalSupply();

  //같은 네트워크의 다른 배포 기록을 지우지 않고 합친다.
  const deploymentsDir = path.join(__dirname, "..", "deployments");
  const deploymentPath = path.join(deploymentsDir, `${network.name}.json`);

  fs.mkdirSync(deploymentsDir, { recursive: true });

  const existing = fs.existsSync(deploymentPath)
    ? JSON.parse(fs.readFileSync(deploymentPath, "utf8"))
    : {};

  const deployment = {
    ...existing,
    network: network.name,
    chainId: network.config.chainId,
    deployer: deployer.address,
    paymentToken: address,
    paymentTokenDeployedAt: new Date().toISOString()
  };

  fs.writeFileSync(deploymentPath, `${JSON.stringify(deployment, null, 2)}\n`);

  console.log("PaymentToken deployed:", address);
  console.log("symbol:", await paymentToken.symbol());
  console.log("totalSupply:", ethers.formatEther(totalSupply));
  console.log("holder:", deployer.address);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
