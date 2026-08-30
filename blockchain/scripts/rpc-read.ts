import { ethers, network } from "hardhat";

function formatOptionalAddress(address?: string) {
  if (!address) {
    return undefined;
  }

  if (!ethers.isAddress(address)) {
    throw new Error(`Invalid TARGET_ADDRESS: ${address}`);
  }

  return address;
}

async function main() {
  const provider = ethers.provider;
  const rpcNetwork = await provider.getNetwork();
  const blockNumber = await provider.getBlockNumber();
  const feeData = await provider.getFeeData();
  const signers = await ethers.getSigners();
  const targetAddress = formatOptionalAddress(
    process.env.TARGET_ADDRESS || signers[0]?.address
  );

  console.log("network:", network.name);
  console.log("rpc chainId:", rpcNetwork.chainId.toString());
  console.log("latest block:", blockNumber);

  if (feeData.gasPrice) {
    console.log("gasPrice(wei):", feeData.gasPrice.toString());
  }

  if (targetAddress) {
    const balance = await provider.getBalance(targetAddress);
    const code = await provider.getCode(targetAddress);

    console.log("target:", targetAddress);
    console.log("balance(wei):", balance.toString());
    console.log("balance(eth):", ethers.formatEther(balance));
    console.log("isContract:", code !== "0x");
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
