import fs from "node:fs";
import path from "node:path";

//abi/ 로 내보낼 컨트랙트 목록. 새 컨트랙트를 만들면 여기에 추가한다.
const CONTRACTS = ["MaterialToken", "PaymentToken", "Marketplace"];

async function main() {
  const abiDir = path.join(__dirname, "..", "abi");
  fs.mkdirSync(abiDir, { recursive: true });

  for (const name of CONTRACTS) {
    const artifactPath = path.join(
      __dirname,
      "..",
      "artifacts",
      "contracts",
      `${name}.sol`,
      `${name}.json`
    );

    if (!fs.existsSync(artifactPath)) {
      throw new Error(`Artifact not found: ${artifactPath} (run npm run compile first)`);
    }

    const outputPath = path.join(abiDir, `${name}.json`);
    const artifact = JSON.parse(fs.readFileSync(artifactPath, "utf8"));

    fs.writeFileSync(outputPath, `${JSON.stringify(artifact.abi, null, 2)}\n`);

    console.log(`${name} ABI exported to ${outputPath}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
