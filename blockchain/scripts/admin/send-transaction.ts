import { ethers, network } from "hardhat";

function requireAddress(name: string, value?: string) {
  if (!value) {
    throw new Error(`${name} is required`);
  }

  if (!ethers.isAddress(value)) {
    throw new Error(`Invalid ${name}: ${value}`);
  }

  return value;
}

async function main() {
  const [sender] = await ethers.getSigners();
  const receiver = requireAddress(
    "TRANSFER_TO_ADDRESS",
    process.env.TRANSFER_TO_ADDRESS
  );
  const amount = ethers.parseEther(process.env.TRANSFER_AMOUNT_ETH ?? "0.001");
  const provider = ethers.provider;
  const rpcNetwork = await provider.getNetwork();

  const senderBalanceBefore = await provider.getBalance(sender.address);
  const receiverBalanceBefore = await provider.getBalance(receiver);

  console.log("network:", network.name);
  console.log("rpc chainId:", rpcNetwork.chainId.toString());
  console.log("from:", sender.address);
  console.log("to:", receiver);
  console.log("amount(eth):", ethers.formatEther(amount));
  console.log("from balance before:", ethers.formatEther(senderBalanceBefore));
  console.log("to balance before:", ethers.formatEther(receiverBalanceBefore));

  const tx = await sender.sendTransaction({
    to: receiver,
    value: amount
  });

  console.log("tx.hash:", tx.hash);
  console.log("waiting for receipt...");

  const receipt = await tx.wait();
  if (!receipt) {
    throw new Error("Transaction receipt was not returned");
  }
  if (receipt.status !== 1) {
    throw new Error(`Transaction failed: ${tx.hash}`);
  }

  const senderBalanceAfter = await provider.getBalance(sender.address);
  const receiverBalanceAfter = await provider.getBalance(receiver);
  if (receiverBalanceAfter - receiverBalanceBefore !== amount) {
    throw new Error("Receiver balance did not increase by the transfer amount");
  }

  console.log("receipt.status:", receipt.status);
  console.log("receipt.blockNumber:", receipt.blockNumber);
  console.log("receipt.gasUsed:", receipt.gasUsed.toString());
  console.log("from balance after:", ethers.formatEther(senderBalanceAfter));
  console.log("to balance after:", ethers.formatEther(receiverBalanceAfter));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
