import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";
import { PROJECT_ROOT } from "../lib/paths";

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

  const initialPredictionPointSupply = ethers.parseUnits(
    process.env.PREDICTION_POINT_INITIAL_SUPPLY ?? "1000000",
    18
  );
  const PredictionPoint = await ethers.getContractFactory("PredictionPoint");
  const predictionPoint = await PredictionPoint.deploy(
    deployer.address,
    initialPredictionPointSupply
  );
  await predictionPoint.waitForDeployment();

  const PredictionMarket = await ethers.getContractFactory("PredictionMarket");
  const predictionMarket = await PredictionMarket.deploy(
    await predictionPoint.getAddress(),
    await productionRegistry.getAddress(),
    deployer.address
  );
  await predictionMarket.waitForDeployment();

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
    predictionPoint: await predictionPoint.getAddress(),
    predictionMarket: await predictionMarket.getAddress(),
    predictionPointInitialSupply: initialPredictionPointSupply.toString(),
    deployer: deployer.address,
    deployedAt: new Date().toISOString()
  };

  const deploymentsDir = path.join(PROJECT_ROOT, "deployments");
  fs.mkdirSync(deploymentsDir, { recursive: true });
  const deploymentFile = path.join(deploymentsDir, `${network.name}.json`);
  // 재배포는 새 주소로 정본을 바꾸지만, 옛 주소와 배포 시각은 감사·복구에 필요하다.
  // 덮어쓰기 전에 그대로 보존해 두면 과거 트랜잭션을 어느 컨트랙트에서 조회할지 잃지 않는다.
  if (fs.existsSync(deploymentFile)) {
    const historyDir = path.join(deploymentsDir, "history");
    fs.mkdirSync(historyDir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    fs.copyFileSync(deploymentFile, path.join(historyDir, `${network.name}-${stamp}.json`));
  }
  fs.writeFileSync(
    deploymentFile,
    `${JSON.stringify(deployment, null, 2)}\n`
  );

  console.log("Blockchain contracts deployed:", deployment);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
