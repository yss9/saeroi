/**
 * 거래 화면의 시각 표기.
 *
 * 발송·수령·타임라인이 같은 형식을 써야 한다. 컴포넌트마다 따로 두면 한쪽만 형식을 바꿔도
 * 아무도 눈치채지 못하고, 같은 화면에서 "26. 9. 3. 오전 9:00" 과 "2026-09-03 09:00" 이
 * 나란히 놓인다.
 *
 * `components/common` 으로 올리지 않았다. 지금 쓰는 곳이 trade 도메인뿐이고, 다른 도메인은
 * 각자 자기 표기를 갖고 있다 (InventoryTable, MaterialBalanceTable). 두 번째 도메인이
 * 같은 것을 필요로 할 때 옮긴다 (ARCHITECTURE §7).
 */

/**
 * Backend 의 `LocalDateTime` 문자열을 사용자 시간대 표기로 바꾼다.
 *
 * 값이 없으면 `-` 다. **"없음" 과 "0" 을 구분해야 한다** — 발송 전 거래의 발송 시각은
 * 없는 것이고, 그 자리에 아무것도 없으면 화면이 깨진 것처럼 보인다.
 *
 * 파싱에 실패하면 원본을 그대로 보여준다. 서버가 예상 밖의 형식을 주더라도 "Invalid Date"
 * 를 띄우는 것보다 원본이 낫다 — 원인을 추적할 수 있다.
 */
export function formatTradeDateTime(value: string | null | undefined): string {
  if (value === null || value === undefined) return '-'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
}
