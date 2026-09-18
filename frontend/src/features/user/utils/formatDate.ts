/**
 * 날짜 문자열을 화면용으로 안전하게 바꾼다.
 *
 * `Intl.DateTimeFormat.format(new Date(value))` 는 value 가 null·빈 문자열·깨진 문자열이면
 * Invalid Date 로 RangeError 를 던져 마이페이지 화면 전체가 깨진다. 서버 응답의 날짜
 * 필드는 비어 있을 수 있으므로(결제·배송 전 단계 등) 그런 값은 `'-'` 로 보여준다.
 */
export function formatDate(
  value: string | number | Date | null | undefined,
  formatter: Intl.DateTimeFormat,
): string {
  if (value === null || value === undefined || value === '') return '-'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return formatter.format(date)
}
