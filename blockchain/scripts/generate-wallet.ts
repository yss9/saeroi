import { Wallet } from "ethers";

/**
 * 테스트/개발용 EOA 생성 스크립트.
 *
 * ethers 의 Wallet.createRandom() 은 OS 의 CSPRNG(crypto.getRandomValues)로
 * 32바이트 난수를 뽑아 secp256k1 개인키를 만든다. Math.random() 같은 것을
 * 직접 조합하면 안 된다.
 *
 * 사용법:
 *   npm run wallet:new
 *   WALLET_COUNT=3 npm run wallet:new
 *   WALLET_SHOW_MNEMONIC=true npm run wallet:new
 *
 * 주의: 여기서 만든 키는 로컬 / SSAFY Besu 테스트 전용이다.
 *      실제 서비스 사용자 지갑은 이 스크립트로 만들지 않는다.
 */

function parseCount(raw?: string) {
  const count = Number(raw ?? 1);

  if (!Number.isInteger(count) || count < 1 || count > 20) {
    throw new Error(`WALLET_COUNT must be an integer between 1 and 20: ${raw}`);
  }

  return count;
}

function main() {
  const count = parseCount(process.env.WALLET_COUNT);
  const showMnemonic = process.env.WALLET_SHOW_MNEMONIC === "true";

  console.log(`generating ${count} random wallet(s)...`);
  console.log("");

  for (let i = 0; i < count; i += 1) {
    const wallet = Wallet.createRandom();

    console.log(`--- wallet #${i + 1} ---`);
    console.log("address       :", wallet.address);
    console.log("privateKey    :", wallet.privateKey);
    console.log("env value     :", wallet.privateKey.slice(2));

    if (showMnemonic) {
      console.log("mnemonic      :", wallet.mnemonic?.phrase ?? "-");
    }

    console.log("");
  }

  console.log("DEPLOYER_PRIVATE_KEY 에는 'env value'(0x 제외)를 넣는다.");
  console.log("생성된 키는 절대 커밋하지 않는다. .env 는 .gitignore 대상이다.");
}

main();
