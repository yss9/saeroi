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

  const ProcessingRegistry = await ethers.getContractFactory("ProcessingRegistry");
  const processingRegistry = await ProcessingRegistry.deploy(
    await materialToken.getAddress(),
    deployer.address
  );
  await processingRegistry.waitForDeployment();

  const ProductionRegistry = await ethers.getContractFactory("ProductionRegistry");
  const productionRegistry = await ProductionRegistry.deploy(
    await materialToken.getAddress(),
    deployer.address
  );
  await productionRegistry.waitForDeployment();

  await materialToken.grantRole(
    await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
    await marketplace.getAddress()
  );
  for (const registry of [processingRegistry, productionRegistry]) {
    await materialToken.grantRole(
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
      await registry.getAddress()
    );
    await materialToken.grantRole(
      await materialToken.MATERIAL_WORKFLOW_ROLE(),
      await registry.getAddress()
    );
  }

  const deployment = {
    network: network.name,
    chainId: network.config.chainId,
    materialToken: await materialToken.getAddress(),
    marketplace: await marketplace.getAddress(),
    processingRegistry: await processingRegistry.getAddress(),
    productionRegistry: await productionRegistry.getAddress(),
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
