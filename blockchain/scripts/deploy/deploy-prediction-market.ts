import { ethers, network } from "hardhat";
import fs from "node:fs";
import { deploymentFile as resolveDeploymentFile } from "../lib/paths";

interface Deployment {
  network: string;
  chainId?: number;
  productionRegistry: string;
  predictionPoint?: string;
  predictionMarket?: string;
  [key: string]: unknown;
}

function deploymentFile() {
  return resolveDeploymentFile(network.name);
}

function loadDeployment(file: string): Deployment {
  if (!fs.existsSync(file)) {
    throw new Error(`${file} 이 없다. 기존 컨트랙트를 먼저 배포해야 한다.`);
  }

  const deployment = JSON.parse(fs.readFileSync(file, "utf8")) as Deployment;
  if (!ethers.isAddress(deployment.productionRegistry)) {
    throw new Error(`deployments/${network.name}.json 의 productionRegistry 주소가 올바르지 않다.`);
  }
  return deployment;
}

async function main() {
  const file = deploymentFile();
  const deployment = loadDeployment(file);
  const [deployer] = await ethers.getSigners();
  const provider = ethers.provider;
  const chain = await provider.getNetwork();

  if (deployment.chainId !== undefined && BigInt(deployment.chainId) !== chain.chainId) {
    throw new Error(
      `배포 파일 chainId(${deployment.chainId})와 연결된 네트워크 chainId(${chain.chainId})가 다르다.`
    );
  }
  if ((deployment.predictionPoint || deployment.predictionMarket) && process.env.ALLOW_PREDICTION_REDEPLOY !== "true") {
    throw new Error(
      `deployments/${network.name}.json 에 예측시장 주소가 이미 있다. 재배포하려면 ALLOW_PREDICTION_REDEPLOY=true가 필요하다.`
    );
  }
  if ((await provider.getCode(deployment.productionRegistry)) === "0x") {
    throw new Error(`ProductionRegistry(${deployment.productionRegistry}) 코드가 네트워크에 없다.`);
  }

  const initialSupplyText = process.env.PREDICTION_POINT_INITIAL_SUPPLY ?? "1000000";
  const initialSupply = ethers.parseUnits(initialSupplyText, 18);

  console.log("network              :", network.name);
  console.log("chainId              :", chain.chainId.toString());
  console.log("deployer             :", deployer.address);
  console.log("productionRegistry   :", deployment.productionRegistry);
  console.log("predictionPointSupply:", initialSupplyText, "PPT");

  const PredictionPoint = await ethers.getContractFactory("PredictionPoint");
  const predictionPoint = await PredictionPoint.deploy(deployer.address, initialSupply);
  await predictionPoint.waitForDeployment();

  const PredictionMarket = await ethers.getContractFactory("PredictionMarket");
  const predictionMarket = await PredictionMarket.deploy(
    await predictionPoint.getAddress(),
    deployment.productionRegistry,
    deployer.address
  );
  await predictionMarket.waitForDeployment();

  const predictionPointAddress = await predictionPoint.getAddress();
  const predictionMarketAddress = await predictionMarket.getAddress();
  const updated = {
    ...deployment,
    predictionPoint: predictionPointAddress,
    predictionMarket: predictionMarketAddress,
    predictionPointInitialSupply: initialSupply.toString(),
    predictionDeployer: deployer.address,
    predictionDeployedAt: new Date().toISOString()
  };

  fs.writeFileSync(file, `${JSON.stringify(updated, null, 2)}\n`);
  console.log("Prediction contracts deployed:", {
    predictionPoint: predictionPointAddress,
    predictionMarket: predictionMarketAddress,
    deploymentFile: file
  });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
