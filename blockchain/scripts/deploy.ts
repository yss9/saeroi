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

  const Marketplace = await ethers.getContractFactory("Marketplace");
  const marketplace = await Marketplace.deploy(
    await materialToken.getAddress(),
    deployer.address
  );

  await marketplace.waitForDeployment();

  await materialToken.grantRole(
    await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
    await marketplace.getAddress()
  );

  const deployment = {
    network: network.name,
    chainId: network.config.chainId,
    materialToken: await materialToken.getAddress(),
    marketplace: await marketplace.getAddress(),
    deployer: deployer.address,
    deployedAt: new Date().toISOString()
  };

  const deploymentsDir = path.join(__dirname, "..", "deployments");
  fs.mkdirSync(deploymentsDir, { recursive: true });
  fs.writeFileSync(
    path.join(deploymentsDir, `${network.name}.json`),
    `${JSON.stringify(deployment, null, 2)}\n`
  );

  console.log("Blockchain contracts deployed:", deployment);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
