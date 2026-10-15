import { ethers, network } from "hardhat";
import fs from "node:fs";
import { deploymentFile } from "../lib/paths";

async function main() {
  const file = deploymentFile(network.name);
  const deployment = JSON.parse(fs.readFileSync(file, "utf8"));
  const [deployer] = await ethers.getSigners();
  const address = deployment.processingRegistry as string;
  const materialAddress = deployment.materialToken as string;

  if (!ethers.isAddress(address) || (await ethers.provider.getCode(address)) === "0x") {
    throw new Error(`ProcessingRegistry(${address}) 코드가 네트워크에 없다.`);
  }

  const registry = await ethers.getContractAt("ProcessingRegistry", address);
  const material = await ethers.getContractAt("MaterialToken", materialAddress);
  if ((await registry.materialToken()).toLowerCase() !== materialAddress.toLowerCase()) {
    throw new Error("ProcessingRegistry가 deployment의 MaterialToken을 참조하지 않는다.");
  }
  if (!(await registry.hasRole(await registry.DEFAULT_ADMIN_ROLE(), deployer.address))) {
    throw new Error("배포 계정에 ProcessingRegistry 관리자 권한이 없다.");
  }
  if (!(await material.hasRole(await material.MATERIAL_LOCK_MANAGER_ROLE(), address))) {
    throw new Error("새 ProcessingRegistry에 MATERIAL_LOCK_MANAGER_ROLE이 없다.");
  }
  if (!(await material.hasRole(await material.MATERIAL_WORKFLOW_ROLE(), address))) {
    throw new Error("새 ProcessingRegistry에 MATERIAL_WORKFLOW_ROLE이 없다.");
  }

  console.log("ProcessingRegistry deployment verified:", {
    network: network.name,
    processingRegistry: address,
    previousProcessingRegistry: deployment.processingRegistryPrevious,
    materialToken: materialAddress,
    deployer: deployer.address,
    nextJobId: (await registry.nextJobId()).toString()
  });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

