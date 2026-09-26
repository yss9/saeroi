import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";
import { PROJECT_ROOT, deploymentFile } from "../lib/paths";

/**
 * 배포된 컨트랙트에 사용자 지갑 역할을 부여한다.
 *
 * deploy.ts 는 컨트랙트끼리의 역할(MATERIAL_LOCK_MANAGER / MATERIAL_WORKFLOW)만 준다.
 * 실제 사용자 지갑의 SUPPLIER / BRAND / PROCESSOR 는 아무도 주지 않으므로, 배포 직후
 * 이걸 돌리지 않으면 판매 등록이 "Marketplace: seller role required" 로 revert 한다.
 *
 * 역할 하나가 컨트랙트 하나에만 있는 게 아니다. ProcessingRegistry 와 ProductionRegistry 는
 * 각자 자기 AccessControl 을 갖고 있어서, BRAND 지갑은 세 컨트랙트 모두에 등록해야 한다.
 * Marketplace 는 예외로 materialToken.hasRole() 을 그대로 읽으므로 따로 줄 필요가 없다.
 *
 * 사용법:
 *   scripts/config/roles.ssafy.json 에 지갑과 역할을 적고
 *     npm run roles:ssafy
 *   한 건만 급히 줄 때는
 *     GRANT_ROLES="0xabc...:SUPPLIER,0xdef...:BRAND|PROCESSOR" npm run roles:ssafy
 */

type RoleName = "SUPPLIER" | "BRAND" | "PROCESSOR" | "ADMIN";
type ContractKey =
  | "materialToken"
  | "marketplace"
  | "processingRegistry"
  | "productionRegistry";

interface Target {
  contract: ContractKey;
  role: string;
}

/** 역할 이름 하나가 실제로 어느 컨트랙트의 어느 역할로 풀리는지. */
const ROLE_TARGETS: Record<RoleName, Target[]> = {
  // 소재 발행·원천 서명·판매 등록. Marketplace 가 materialToken 을 조회하므로 여기 하나면 된다.
  SUPPLIER: [{ contract: "materialToken", role: "SUPPLIER_ROLE" }],

  // 구매·거래 서명(materialToken) + 가공 의뢰(processingRegistry) + 생산 배치(productionRegistry).
  BRAND: [
    { contract: "materialToken", role: "BRAND_ROLE" },
    { contract: "processingRegistry", role: "BRAND_ROLE" },
    { contract: "productionRegistry", role: "BRAND_ROLE" }
  ],

  // 가공 수락·결과 제출.
  PROCESSOR: [
    { contract: "materialToken", role: "PROCESSOR_ROLE" },
    { contract: "processingRegistry", role: "PROCESSOR_ROLE" }
  ],

  // 운영 계정. 실수 방지를 위해 ALLOW_ADMIN_GRANT=true 없이는 거부한다.
  ADMIN: [
    { contract: "materialToken", role: "DEFAULT_ADMIN_ROLE" },
    { contract: "marketplace", role: "DEFAULT_ADMIN_ROLE" },
    { contract: "processingRegistry", role: "DEFAULT_ADMIN_ROLE" },
    { contract: "productionRegistry", role: "DEFAULT_ADMIN_ROLE" }
  ]
};

const CONTRACT_NAME: Record<ContractKey, string> = {
  materialToken: "MaterialToken",
  marketplace: "Marketplace",
  processingRegistry: "ProcessingRegistry",
  productionRegistry: "ProductionRegistry"
};

const ROLE_NAMES = Object.keys(ROLE_TARGETS) as RoleName[];

/** { "0xabc...": ["SUPPLIER"], ... } 형태로 정규화한다. */
function loadAssignments(): Map<string, RoleName[]> {
  const inline = process.env.GRANT_ROLES?.trim();
  const raw: Record<string, string[]> = inline
    ? parseInline(inline)
    : loadFile();

  const assignments = new Map<string, RoleName[]>();
  for (const [address, roles] of Object.entries(raw)) {
    if (!ethers.isAddress(address)) {
      throw new Error(`Invalid address: ${address}`);
    }
    if (!Array.isArray(roles) || roles.length === 0) {
      throw new Error(`No roles for ${address}`);
    }
    for (const role of roles) {
      if (!ROLE_NAMES.includes(role as RoleName)) {
        throw new Error(
          `Unknown role "${role}" for ${address}. Allowed: ${ROLE_NAMES.join(", ")}`
        );
      }
    }
    // 같은 지갑이 두 번 적혀도 역할을 합친다.
    const key = ethers.getAddress(address);
    const merged = new Set([...(assignments.get(key) ?? []), ...(roles as RoleName[])]);
    assignments.set(key, [...merged]);
  }

  if (assignments.size === 0) {
    throw new Error("No assignments found");
  }
  return assignments;
}

/** "0xabc:SUPPLIER,0xdef:BRAND|PROCESSOR" */
function parseInline(value: string): Record<string, string[]> {
  const result: Record<string, string[]> = {};
  for (const entry of value.split(",")) {
    const [address, roles] = entry.split(":").map((part) => part.trim());
    if (!address || !roles) {
      throw new Error(`Malformed GRANT_ROLES entry: "${entry}"`);
    }
    result[address] = roles.split("|").map((role) => role.trim().toUpperCase());
  }
  return result;
}

function loadFile(): Record<string, string[]> {
  const configured = process.env.ROLE_ASSIGNMENTS_FILE;
  const preferred = path.join(PROJECT_ROOT, "scripts", "config", `roles.${network.name}.json`);
  const legacy = path.join(PROJECT_ROOT, "scripts", `roles.${network.name}.json`);
  const file = configured
    ? path.resolve(configured)
    : fs.existsSync(preferred)
      ? preferred
      : legacy;
  if (!fs.existsSync(file)) {
    throw new Error(
      `${file} 이 없다. roles.ssafy.example.json 을 복사해 채우거나 GRANT_ROLES 로 넘긴다.`
    );
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function loadDeployment() {
  const file = deploymentFile(network.name);
  if (!fs.existsSync(file)) {
    throw new Error(`${file} 이 없다. 먼저 deploy 를 실행한다.`);
  }
  return JSON.parse(fs.readFileSync(file, "utf8")) as Record<ContractKey, string>;
}

async function main() {
  const assignments = loadAssignments();
  const deployment = loadDeployment();
  const [admin] = await ethers.getSigners();

  const allowAdminGrant = process.env.ALLOW_ADMIN_GRANT === "true";
  const wantsAdmin = [...assignments.values()].some((roles) => roles.includes("ADMIN"));
  if (wantsAdmin && !allowAdminGrant) {
    throw new Error(
      "ADMIN 부여는 ALLOW_ADMIN_GRANT=true 를 함께 넘겨야 한다. 되돌리기 어려운 권한이다."
    );
  }

  console.log("network :", network.name);
  console.log("admin   :", admin.address);
  console.log("wallets :", assignments.size);
  console.log("");

  // 필요한 컨트랙트만 붙이고, admin 권한을 먼저 확인한다.
  // 권한 없이 시작하면 절반쯤 부여된 상태로 실패한다.
  const needed = new Set<ContractKey>();
  for (const roles of assignments.values()) {
    for (const role of roles) {
      for (const target of ROLE_TARGETS[role]) needed.add(target.contract);
    }
  }

  const contracts = new Map<ContractKey, any>();
  for (const key of needed) {
    const address = deployment[key];
    if (!address) {
      throw new Error(`deployments/${network.name}.json 에 ${key} 주소가 없다.`);
    }
    const contract = await ethers.getContractAt(CONTRACT_NAME[key], address);
    const defaultAdminRole = await contract.DEFAULT_ADMIN_ROLE();
    if (!(await contract.hasRole(defaultAdminRole, admin.address))) {
      throw new Error(
        `${CONTRACT_NAME[key]}(${address}) 의 DEFAULT_ADMIN_ROLE 이 ${admin.address} 에 없다. ` +
          "DEPLOYER_PRIVATE_KEY 가 배포에 쓴 키와 같은지 확인한다."
      );
    }
    contracts.set(key, contract);
  }

  let granted = 0;
  let skipped = 0;

  for (const [wallet, roles] of assignments) {
    console.log(`${wallet}  ${roles.join(" ")}`);

    for (const roleName of roles) {
      for (const target of ROLE_TARGETS[roleName]) {
        const contract = contracts.get(target.contract);
        const roleHash = await contract[target.role]();
        const label = `${target.contract}.${target.role}`.padEnd(38);

        if (await contract.hasRole(roleHash, wallet)) {
          console.log(`  ${label} already`);
          skipped += 1;
          continue;
        }

        const tx = await contract.grantRole(roleHash, wallet);
        await tx.wait();
        console.log(`  ${label} granted  ${tx.hash}`);
        granted += 1;
      }
    }
    console.log("");
  }

  console.log(`done — granted ${granted}, skipped ${skipped}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
