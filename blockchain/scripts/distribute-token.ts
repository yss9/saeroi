import { ethers, network } from "hardhat";
import fs from "node:fs";
import path from "node:path";

/**
 * admin 이 보유한 PaymentToken 을 업체 지갑들에 분배한다.
 *
 * .env 설정:
 *   PAYMENT_TOKEN_RECIPIENTS=0xAAA...,0xBBB...,0xCCC...
 *   PAYMENT_TOKEN_RECIPIENT_AMOUNT=10000
 *
 * 실행:
 *   npm run token:distribute:local
 *   npm run token:distribute:ssafy
 */

function loadPaymentTokenAddress() {
  if (process.env.PAYMENT_TOKEN_ADDRESS) {
    return process.env.PAYMENT_TOKEN_ADDRESS;
  }

  const deploymentPath = path.join(
    __dirname,
    "..",
    "deployments",
    `${network.name}.json`
  );

  if (!fs.existsSync(deploymentPath)) {
    throw new Error(`No deployment record for network ${network.name}`);
  }

  const deployment = JSON.parse(fs.readFileSync(deploymentPath, "utf8"));

  if (!deployment.paymentToken) {
    throw new Error(`paymentToken address missing in ${deploymentPath}`);
  }

  return deployment.paymentToken as string;
}

function loadRecipients() {
  const raw = process.env.PAYMENT_TOKEN_RECIPIENTS ?? "";
  const recipients = raw
    .split(",")
    .map((value) => value.trim())
    .filter((value) => value.length > 0);

  if (recipients.length === 0) {
    throw new Error("PAYMENT_TOKEN_RECIPIENTS is required");
  }

  for (const recipient of recipients) {
    if (!ethers.isAddress(recipient)) {
      throw new Error(`Invalid recipient address: ${recipient}`);
    }
  }

  return recipients;
}

async function main() {
  const [admin] = await ethers.getSigners();
  const tokenAddress = loadPaymentTokenAddress();
  const recipients = loadRecipients();
  const amount = ethers.parseEther(
    process.env.PAYMENT_TOKEN_RECIPIENT_AMOUNT ?? "10000"
  );

  const paymentToken = await ethers.getContractAt(
    "PaymentToken",
    tokenAddress,
    admin
  );

  console.log("network:", network.name);
  console.log("paymentToken:", tokenAddress);
  console.log("admin:", admin.address);
  console.log("amount each:", ethers.formatEther(amount));

  const adminBalance = await paymentToken.balanceOf(admin.address);
  const required = amount * BigInt(recipients.length);

  if (adminBalance < required) {
    throw new Error(
      `Admin balance ${ethers.formatEther(adminBalance)} is less than required ${ethers.formatEther(required)}`
    );
  }

  for (const recipient of recipients) {
    const tx = await paymentToken.transfer(recipient, amount);
    await tx.wait();

    const balance = await paymentToken.balanceOf(recipient);
    console.log(`sent -> ${recipient} (balance: ${ethers.formatEther(balance)})`);
  }

  const adminAfter = await paymentToken.balanceOf(admin.address);
  console.log("admin balance after:", ethers.formatEther(adminAfter));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
