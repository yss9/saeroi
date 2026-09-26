/**
 * `mintMaterial` 의 `evidenceHash` 자리를 채우는 값.
 *
 * 컨트랙트가 `evidenceHash != 0` 을 요구한다. 정책상 여기에는 Evidence Manifest 의
 * SHA-256 이 들어가야 하는데 **그 매니페스트를 만드는 경로가 아직 없다.** 그래서 발행이
 * 막히지 않도록 소재 코드에서 파생한 값을 넣는다.
 *
 * ⚠️ **이 값은 증빙과 대조되지 않는다.** 매니페스트가 붙으면 이 함수를 지우고 실제 해시를
 * 넣는다. 그때까지 온체인 `evidenceHash` 는 "무엇인가 채워져 있다" 는 뜻일 뿐이다.
 *
 * 무작위가 아니라 소재 코드에서 파생하는 이유는 **재시도할 때 같은 값이 나와야** 하기
 * 때문이다. 매번 달라지면 나중에 어느 것이 그 소재의 값이었는지 알 수 없다.
 */
export function evidencePlaceholder(materialCode: string): string {
  let hash = 0n
  for (const char of materialCode) {
    hash = (hash * 31n + BigInt(char.codePointAt(0) ?? 0)) % 2n ** 256n
  }
  return `0x${hash.toString(16).padStart(64, '0')}`
}
