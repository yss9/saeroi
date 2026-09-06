import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

const CONTRACTS = ["MaterialToken", "Marketplace", "ProcessingRegistry", "ProductionRegistry"];

async function main() {
  const txHash = process.env.TX_HASH;
  if (!txHash || !/^0x[0-9a-fA-F]{64}$/.test(txHash)) throw new Error("TX_HASH is required");
  const receipt = await ethers.provider.getTransactionReceipt(txHash);
  if (!receipt) throw new Error(`Receipt not found: ${txHash}`);
  const block = await ethers.provider.getBlock(receipt.blockNumber);
  if (!block) throw new Error(`Block not found: ${receipt.blockNumber}`);

  const interfaces = CONTRACTS.map((name) => {
    const abi = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "abi", `${name}.json`), "utf8"));
    return { name, iface: new ethers.Interface(abi) };
  });
  const events = receipt.logs.flatMap((log) => {
    for (const contract of interfaces) {
      try {
        const parsed = contract.iface.parseLog(log);
        if (parsed) return [{ contract: contract.name, event: parsed.name, args: parsed.args.toObject() }];
      } catch { /* 다른 ABI의 로그 */ }
    }
    return [];
  });
  console.log(JSON.stringify({ network: network.name, transactionHash: txHash, status: receipt.status, blockNumber: receipt.blockNumber, occurredAt: new Date(block.timestamp * 1000).toISOString(), gasUsed: receipt.gasUsed.toString(), events }, (_, value) => typeof value === "bigint" ? value.toString() : value, 2));
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
