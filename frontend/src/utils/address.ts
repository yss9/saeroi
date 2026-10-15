// 지갑 주소 표시 유틸.
//
// walletClient 와 WalletChip 양쪽에 같은 구현이 있었다. 한쪽만 고치면 화면마다
// 축약 형태가 달라지므로 여기 하나로 합친다.

/** 표시용 주소 축약. `0x1234…cdef` */
export function shortenAddress(address: string): string {
  return address.length <= 12 ? address : `${address.slice(0, 6)}…${address.slice(-4)}`
}
