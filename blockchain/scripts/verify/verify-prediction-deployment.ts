import { ethers, network } from "hardhat";
import fs from "node:fs";
import { deploymentFile } from "../lib/paths";

interface Deployment {
  predictionPoint: string;
  predictionMarket: string;
  productionRegistry: string;
  predictionPointInitialSupply?: string;
}

async function main() {
  const file = deploymentFile(network.name);
  if (!fs.existsSync(file)) throw new Error(`${file} 이 없다.`);
  const deployment = JSON.parse(fs.readFileSync(file, "utf8")) as Deployment;
  const [deployer] = await ethers.getSigners();

  for (const key of ["predictionPoint", "predictionMarket", "productionRegistry"] as const) {
    const address = deployment[key];
    if (!ethers.isAddress(address) || (await ethers.provider.getCode(address)) === "0x") {
      throw new Error(`${key}(${address}) 배포 코드를 확인할 수 없다.`);
    }
  }

  const point = await ethers.getContractAt("PredictionPoint", deployment.predictionPoint);
  const market = await ethers.getContractAt("PredictionMarket", deployment.predictionMarket);
  const expectedSupply = deployment.predictionPointInitialSupply
    ? BigInt(deployment.predictionPointInitialSupply)
    : undefined;

  if ((await point.name()) !== "Prediction Point" || (await point.symbol()) !== "PPT") {
    throw new Error("PredictionPoint 메타데이터가 일치하지 않는다.");
  }
  if (expectedSupply !== undefined && (await point.totalSupply()) !== expectedSupply) {
    throw new Error("PredictionPoint 총 발행량이 배포 파일과 일치하지 않는다.");
  }
  if ((await market.predictionToken()).toLowerCase() !== deployment.predictionPoint.toLowerCase()) {
    throw new Error("PredictionMarket의 predictionToken 주소가 일치하지 않는다.");
  }
  if ((await market.productionRegistry()).toLowerCase() !== deployment.productionRegistry.toLowerCase()) {
    throw new Error("PredictionMarket의 productionRegistry 주소가 일치하지 않는다.");
  }
  if (!(await point.hasRole(await point.DEFAULT_ADMIN_ROLE(), deployer.address))) {
    throw new Error("배포자에게 PredictionPoint 관리자 권한이 없다.");
  }
  if (!(await market.hasRole(await market.DEFAULT_ADMIN_ROLE(), deployer.address))) {
    throw new Error("배포자에게 PredictionMarket 관리자 권한이 없다.");
  }

  console.log("Prediction deployment verified:", {
    network: network.name,
    predictionPoint: deployment.predictionPoint,
    predictionMarket: deployment.predictionMarket,
    productionRegistry: deployment.productionRegistry,
    totalSupply: (await point.totalSupply()).toString(),
    deployer: deployer.address
  });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
