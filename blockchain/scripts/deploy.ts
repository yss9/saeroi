import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

async function main() {
  const [deployer] = await ethers.getSigners();
  const MaterialToken = await ethers.getContractFactory("MaterialToken");
  const materialToken = await MaterialToken.deploy(
    "ipfs://material/{id}.json",
    deployer.address
  );

  await materialToken.waitForDeployment();

  const deployment = {
    network: network.name,
    chainId: network.config.chainId,
    materialToken: await materialToken.getAddress(),
    deployer: deployer.address,
    deployedAt: new Date().toISOString()
  };

  const deploymentsDir = path.join(__dirname, "..", "deployments");
  fs.mkdirSync(deploymentsDir, { recursive: true });
  fs.writeFileSync(
    path.join(deploymentsDir, `${network.name}.json`),
    `${JSON.stringify(deployment, null, 2)}\n`
  );

  console.log("MaterialToken deployed:", deployment);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
