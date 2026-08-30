import fs from "node:fs";
import path from "node:path";

async function main() {
  const artifactPath = path.join(
    __dirname,
    "..",
    "artifacts",
    "contracts",
    "MaterialToken.sol",
    "MaterialToken.json"
  );
  const outputPath = path.join(__dirname, "..", "abi", "MaterialToken.json");

  const artifact = JSON.parse(fs.readFileSync(artifactPath, "utf8"));
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(artifact.abi, null, 2)}\n`);

  console.log(`MaterialToken ABI exported to ${outputPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
