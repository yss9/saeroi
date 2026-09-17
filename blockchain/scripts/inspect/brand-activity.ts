import { ethers, network } from "hardhat";
import fs from "node:fs";
import { abiFile, deploymentFile } from "../lib/paths";

/**
 * 한 주소(기본값: 브랜드)가 관여한 온체인 활동을 네 컨트랙트 로그에서 전부 긁어 시간순으로 출력한다.
 *
 *   set BRAND_ADDRESS=0x...        조회 대상 (없으면 .env 의 배포자 계정)
 *   set FROM_BLOCK=11000000        시작 블록 (없으면 latest - 500000)
 *   set TO_BLOCK=latest            끝 블록
 *   npx hardhat run scripts/inspect/brand-activity.ts --network ssafy
 *
 * Besu 는 getLogs 범위에 제한이 있어 CHUNK 단위로 끊어 요청한다.
 */
const DEPLOY_KEYS = {
  MaterialToken: "materialToken",
  Marketplace: "marketplace",
  ProcessingRegistry: "processingRegistry",
  ProductionRegistry: "productionRegistry",
} as const;

const CHUNK = 5_000;
const DEFAULT_LOOKBACK = 500_000;

async function main() {
  const provider = ethers.provider;
  const deployments = JSON.parse(
    fs.readFileSync(deploymentFile(network.name), "utf8")
  );

  let target = process.env.BRAND_ADDRESS;
  if (!target) {
    const signers = await ethers.getSigners();
    target = signers[0]?.address;
  }
  if (!target || !ethers.isAddress(target)) throw new Error("BRAND_ADDRESS 가 필요하다");
  const targetLc = target.toLowerCase();

  const latest = await provider.getBlockNumber();
  const toBlock = process.env.TO_BLOCK && process.env.TO_BLOCK !== "latest"
    ? Number(process.env.TO_BLOCK)
    : latest;
  const fromBlock = process.env.FROM_BLOCK
    ? Number(process.env.FROM_BLOCK)
    : Math.max(0, toBlock - DEFAULT_LOOKBACK);

  const targets = Object.entries(DEPLOY_KEYS).map(([name, key]) => {
    const address = deployments[key];
    if (!address) throw new Error(`deployments/${network.name}.json 에 ${key} 가 없다`);
    const abi = JSON.parse(fs.readFileSync(abiFile(name), "utf8"));
    return { name, address: address as string, iface: new ethers.Interface(abi) };
  });

  console.error(`scanning ${fromBlock} → ${toBlock} for ${target} on ${network.name} ...`);

  const rows: any[] = [];
  for (const contract of targets) {
    for (let start = fromBlock; start <= toBlock; start += CHUNK) {
      const end = Math.min(start + CHUNK - 1, toBlock);
      let logs;
      try {
        logs = await provider.getLogs({ address: contract.address, fromBlock: start, toBlock: end });
      } catch (error) {
        console.error(`  [skip] ${contract.name} ${start}-${end}: ${(error as Error).message}`);
        continue;
      }
      for (const log of logs) {
        let parsed;
        try {
          parsed = contract.iface.parseLog(log);
        } catch {
          continue;
        }
        if (!parsed) continue;
        const args = parsed.args.toObject();
        const touchesTarget = Object.values(args).some(
          (value) => typeof value === "string" && value.toLowerCase() === targetLc
        );
        if (!touchesTarget) continue;
        rows.push({
          block: log.blockNumber,
          txHash: log.transactionHash,
          contract: contract.name,
          event: parsed.name,
          args,
        });
      }
    }
  }

  rows.sort((a, b) => a.block - b.block || a.txHash.localeCompare(b.txHash));

  const txSeen = new Set(rows.map((r) => r.txHash));
  console.log(
    JSON.stringify(
      {
        network: network.name,
        address: target,
        fromBlock,
        toBlock,
        transactionCount: txSeen.size,
        eventCount: rows.length,
        events: rows,
      },
      (_, value) => (typeof value === "bigint" ? value.toString() : value),
      2
    )
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
