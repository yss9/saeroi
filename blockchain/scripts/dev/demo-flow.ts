import { createHash } from "node:crypto";
import type {
  ContractTransactionReceipt,
  ContractTransactionResponse,
  Interface
} from "ethers";
import { ethers } from "hardhat";

function sha256(value: string) {
  return `0x${createHash("sha256").update(value).digest("hex")}`;
}

let operationSequence = 0;
const op = () => ethers.keccak256(ethers.toUtf8Bytes(`demo-operation-${++operationSequence}`));

async function waitForReceipt(
  transaction: Promise<ContractTransactionResponse>
) {
  const response = await transaction;
  const receipt = await response.wait();
  if (!receipt) {
    throw new Error("Transaction receipt was not returned");
  }
  return receipt;
}

function findEventArgs(
  receipt: ContractTransactionReceipt,
  contractInterface: Interface,
  eventName: string
) {
  for (const log of receipt.logs) {
    try {
      const parsed = contractInterface.parseLog(log);
      if (parsed?.name === eventName) {
        return parsed.args;
      }
    } catch {
      // 다른 컨트랙트가 발생시킨 Log는 현재 Interface로 해석할 수 없으므로 건너뛴다.
    }
  }
  throw new Error(`${eventName} event was not found`);
}

async function main() {
  // Hardhat 내장 네트워크가 매 실행마다 자금이 들어 있는 테스트 지갑을 제공한다.
  const [admin, supplier, brand, processor] = await ethers.getSigners();

  console.log("\n=== 1. Hardhat 가상 지갑 ===");
  console.log("Admin:", admin.address);
  console.log("Supplier:", supplier.address);
  console.log("Brand:", brand.address);
  console.log("Processor:", processor.address);

  const MaterialToken = await ethers.getContractFactory("MaterialToken");
  const materialToken = await MaterialToken.deploy(
    "ipfs://material/{id}.json",
    admin.address
  );
  await materialToken.waitForDeployment();

  const Marketplace = await ethers.getContractFactory("Marketplace");
  const marketplace = await Marketplace.deploy(
    await materialToken.getAddress(),
    admin.address
  );
  await marketplace.waitForDeployment();

  await waitForReceipt(
    materialToken.grantRole(
      await materialToken.SUPPLIER_ROLE(),
      supplier.address
    )
  );
  await waitForReceipt(
    materialToken.grantRole(await materialToken.BRAND_ROLE(), brand.address)
  );
  await waitForReceipt(
    materialToken.grantRole(
      await materialToken.PROCESSOR_ROLE(),
      processor.address
    )
  );
  await waitForReceipt(
    materialToken.grantRole(
      await materialToken.MATERIAL_LOCK_MANAGER_ROLE(),
      await marketplace.getAddress()
    )
  );

  console.log("\n=== 2. 컨트랙트 배포 및 역할 부여 ===");
  console.log("MaterialToken:", await materialToken.getAddress());
  console.log("Marketplace:", await marketplace.getAddress());

  const sourceEvidenceHash = sha256("supplier-source-manifest.json");
  const mintReceipt = await waitForReceipt(
    materialToken
      .connect(supplier)
      .mintMaterial(
        supplier.address,
        20_000,
        1,
        "ipfs://material/waste-denim.json",
        sourceEvidenceHash
      , op())
  );
  const mintEvent = findEventArgs(
    mintReceipt,
    materialToken.interface,
    "MaterialMinted"
  );
  const materialId = mintEvent.tokenId as bigint;

  await waitForReceipt(
    materialToken
      .connect(supplier)
      .verifySource(materialId, sourceEvidenceHash, op())
  );

  console.log("\n=== 3. Supplier 소재 발행 및 원천 서명 ===");
  console.log("Material ID:", materialId.toString());
  console.log(
    "Supplier balance:",
    (await materialToken.balanceOf(supplier.address, materialId)).toString(),
    "g"
  );
  console.log("Verification: SOURCE_VERIFIED (SourceVerified event에서 파생)");

  const listingReceipt = await waitForReceipt(
    marketplace.connect(supplier).createListing(materialId, 20_000, op())
  );
  const listingEvent = findEventArgs(
    listingReceipt,
    marketplace.interface,
    "ListingCreated"
  );
  const listingId = listingEvent.listingId as bigint;
  const lockId = listingEvent.materialLockId as bigint;

  console.log("\n=== 4. 판매 등록과 Material Lock ===");
  console.log("Listing ID:", listingId.toString());
  console.log("Lock ID:", lockId.toString());
  console.log("Lock purpose: MARKETPLACE(0)");
  console.log("Lock status: ACTIVE(1)");
  console.log(
    "Supplier available:",
    (await materialToken.availableBalance(supplier.address, materialId)).toString(),
    "g"
  );

  const orderReceipt = await waitForReceipt(
    marketplace.connect(brand).createOrder(listingId, 20_000, op())
  );
  const orderEvent = findEventArgs(
    orderReceipt,
    marketplace.interface,
    "OrderCreated"
  );
  const orderId = orderEvent.orderId as bigint;

  console.log("\n=== 5. Brand 주문 ===");
  console.log("Order ID:", orderId.toString());
  console.log("Order status: CREATED(1)");
  console.log("Payment: 백엔드/PG와 DB에서만 처리하며 온체인에는 기록하지 않음");

  // 실제 서비스에서는 백엔드가 오프체인 결제 완료를 확인한 뒤 발송 절차를 진행한다.
  await waitForReceipt(marketplace.connect(supplier).markShipped(orderId, op()));

  console.log("\n=== 6. 오프체인 결제 확인 후 Supplier 발송 ===");
  console.log("Order status: SHIPPED(2)");
  console.log("이 시점까지 소재 소유자는 Supplier이고 Lock도 유지됩니다.");

  await waitForReceipt(marketplace.connect(brand).confirmReceipt(orderId, op()));

  console.log("\n=== 7. Brand 수령 확인 ===");
  console.log("Order status: COMPLETED(4)");
  console.log(
    "Supplier balance:",
    (await materialToken.balanceOf(supplier.address, materialId)).toString(),
    "g"
  );
  console.log(
    "Brand balance:",
    (await materialToken.balanceOf(brand.address, materialId)).toString(),
    "g"
  );
  const consumedLock = await materialToken.materialLockInfo(lockId);
  console.log("Lock remaining:", consumedLock.remainingAmount.toString(), "g");
  console.log("Lock status: CONSUMED(", consumedLock.status.toString(), ")");

  await waitForReceipt(
    materialToken
      .connect(brand)
      .verifyTransaction(materialId, orderId, sha256("trade-manifest-1.json"), op())
  );
  console.log("\n=== 8. Brand 거래 서명 ===");
  console.log("Brand signer:", brand.address);
  console.log("Processing 검증은 ProcessingRegistry의 결과 제출 + Brand 승인 흐름에서 파생됩니다.");
  console.log("\n데모 완료");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
