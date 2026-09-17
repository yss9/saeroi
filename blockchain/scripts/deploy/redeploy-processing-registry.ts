import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";
import { deploymentFile, PROJECT_ROOT } from "../lib/paths";

interface Deployment {
  network: string;
  chainId?: number;
  materialToken: string;
  processingRegistry: string;
  [key: string]: unknown;
}

function loadDeployment(file: string): Deployment {
  if (!fs.existsSync(file)) {
    throw new Error(`${file} 이 없다. 먼저 전체 배포를 실행한다.`);
  }
  const value = JSON.parse(fs.readFileSync(file, "utf8")) as Deployment;
  if (!ethers.isAddress(value.materialToken) || !ethers.isAddress(value.processingRegistry)) {
    throw new Error("배포 파일의 materialToken 또는 processingRegistry 주소가 올바르지 않다.");
  }
  return value;
}

async function main() {
  if (process.env.ALLOW_PROCESSING_REDEPLOY !== "true") {
    throw new Error("재배포를 명시하려면 ALLOW_PROCESSING_REDEPLOY=true 를 설정한다.");
  }

  const file = deploymentFile(network.name);
  const deployment = loadDeployment(file);
  const chain = await ethers.provider.getNetwork();
  const [deployer] = await ethers.getSigners();

  if (deployment.chainId !== undefined && BigInt(deployment.chainId) !== chain.chainId) {
    throw new Error(
      `배포 파일 chainId(${deployment.chainId})와 연결 네트워크 chainId(${chain.chainId})가 다르다.`
    );
  }
  if ((await ethers.provider.getCode(deployment.materialToken)) === "0x") {
    throw new Error(`MaterialToken(${deployment.materialToken}) 코드가 네트워크에 없다.`);
  }

  const materialToken = await ethers.getContractAt("MaterialToken", deployment.materialToken);
  const adminRole = await materialToken.DEFAULT_ADMIN_ROLE();
  if (!(await materialToken.hasRole(adminRole, deployer.address))) {
    throw new Error(`${deployer.address}에 MaterialToken 관리자 권한이 없다.`);
  }

  const previousAddress = ethers.getAddress(deployment.processingRegistry);
  const ProcessingRegistry = await ethers.getContractFactory("ProcessingRegistry");
  const processingRegistry = await ProcessingRegistry.deploy(
    deployment.materialToken,
    deployer.address
  );
  const deployReceipt = await processingRegistry.deploymentTransaction()?.wait();
  const nextAddress = await processingRegistry.getAddress();

  const lockTx = await materialToken.grantRole(
    await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
    nextAddress
  );
  await lockTx.wait();
  const workflowTx = await materialToken.grantRole(
    await materialToken.MATERIAL_WORKFLOW_ROLE(),
    nextAddress
  );
  await workflowTx.wait();

  const now = new Date().toISOString();
  const historyDir = path.join(PROJECT_ROOT, "deployments", "history");
  fs.mkdirSync(historyDir, { recursive: true });
  const historyName = `${network.name}-processingRegistry-${now.replace(/[:.]/g, "-")}.json`;
  fs.writeFileSync(
    path.join(historyDir, historyName),
    `${JSON.stringify(deployment, null, 2)}\n`
  );

  const updated = {
    ...deployment,
    processingRegistry: nextAddress,
    processingRegistryPrevious: previousAddress,
    processingRegistryDeployer: deployer.address,
    processingRegistryDeployTx: deployReceipt?.hash,
    processingRegistryDeployedAt: now
  };
  fs.writeFileSync(file, `${JSON.stringify(updated, null, 2)}\n`);

  console.log("ProcessingRegistry redeployed:", {
    network: network.name,
    previousAddress,
    nextAddress,
    deploymentTransaction: deployReceipt?.hash,
    lockRoleTransaction: lockTx.hash,
    workflowRoleTransaction: workflowTx.hash,
    deploymentFile: file,
    historyFile: path.join(historyDir, historyName)
  });
  console.log("사용자 BRAND/PROCESSOR 역할은 npm run roles:ssafy 로 새 주소에 다시 부여해야 한다.");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

