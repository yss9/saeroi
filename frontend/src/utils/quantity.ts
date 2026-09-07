// 소재 수량 단위 변환.
//
// 정책 03절: 공식 단위는 kg 이지만 저장·전송은 **gram 정수** 로 한다.
//   화면      20.5 kg
//   DB·체인   20500 (gram)
//
// ⚠️ 왜 이 파일이 따로 있는가
// 컴포넌트마다 `value / 1000` 을 쓰면 부동소수점 오차가 섞인다.
// JavaScript 에서 `0.1 + 0.2 !== 0.3` 이고, 20.3kg 을 gram 으로 바꾸는
// `20.3 * 1000` 은 **20299.999999999996** 이 된다. 이 값이 그대로 체인에 나가면
// 잔량이 1g 씩 어긋나고, 그건 되돌릴 수 없다.
//
// 그래서 kg → gram 변환은 반드시 이 함수를 쓴다.

/** 화면 입력(kg) → 저장·전송용 gram 정수. 소수 넷째 자리 이하는 버린다. */
export function kgToGram(kg: number): number {
  if (!Number.isFinite(kg) || kg < 0) return 0
  // 곱한 뒤 반올림한다. 20.3 * 1000 = 20299.999... → 20300
  return Math.round(kg * 1000)
}

/** gram → 화면 표시용 kg 숫자. */
export function gramToKg(gram: number | bigint): number {
  return Number(gram) / 1000
}

/**
 * gram → 화면 표시 문자열.
 *
 * 소수점 자리를 고정하지 않는다. 20000g 을 "20.000 kg" 으로 보여줄 이유가 없고,
 * 20500g 은 "20.5 kg" 이어야 한다.
 */
export function formatKg(gram: number | bigint, options: { withUnit?: boolean } = {}): string {
  const { withUnit = true } = options
  const value = gramToKg(gram)
  const text = value.toLocaleString('ko-KR', { maximumFractionDigits: 3 })
  return withUnit ? `${text} kg` : text
}

/**
 * 체인이 돌려주는 bigint 잔량을 화면용 문자열로.
 *
 * ERC-1155 amount 는 uint256 이라 ethers 가 bigint 로 준다. Number 로 바꾸면
 * 2^53 을 넘을 때 정밀도가 깨지지만, 1g 단위 소재 수량이 그 범위에 닿는 일은 없다
 * (9,007조 kg). 그래도 변환은 이 함수 한 곳에서만 한다.
 */
export function formatOnChainQuantity(amount: bigint): string {
  return formatKg(amount)
}

/** 금액(원) 표시. 정책 08절에서 포인트가 폐지되고 원 단위로 통일됐다. */
export function formatWon(amount: number | bigint): string {
  return `${Number(amount).toLocaleString('ko-KR')}원`
}

/**
 * kg당 단가와 수량(gram)으로 총액을 구한다. **미리보기 전용이다.**
 *
 * 기준은 서버의 `TradePricing` 이다. 컨트랙트가 아니다 — `Marketplace.sol` 은 가격을
 * 아예 저장하지 않는다 (`createListing` 에 가격 인자가 없고 `Order` 에 금액 필드가 없다).
 * 결제가 전적으로 오프체인이라 온체인에는 수량과 소유권만 남는다.
 *
 * 그래서 여기와 맞춰야 하는 상대는 **서버 하나**다. 화면이 보여준 금액과 결제 승인 금액이
 * 다르면 서버가 `PAYMENT_AMOUNT_MISMATCH` 로 거절한다.
 *
 * 버림을 쓴다. 바꿔야 한다면 `TradePricing.totalPrice` 와 같은 커밋에서 바꾼다.
 */
export function calculateTotalPrice(quantityGram: number, pricePerKg: number): number {
  return Math.floor((quantityGram * pricePerKg) / 1000)
}
