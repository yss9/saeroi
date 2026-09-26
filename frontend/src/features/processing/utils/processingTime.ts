/**
 * 서버가 주는 시각을 그대로 읽는다.
 *
 * **`Z` 를 붙이지 않는다.** 서버는 `LocalDateTime`(시간대 없는 값)을 그대로 내려주고 그
 * 값은 이미 한국 시각이다. 예전 코드는 시간대가 없으면 UTC 로 보고 `Z` 를 붙였는데,
 * 그러면 브라우저가 한국 시각으로 다시 옮기면서 **아홉 시간이 더해졌다** — 결제 기한이
 * 실제보다 늦게, 견적 제출 시각이 미래처럼 보였다.
 *
 * 시간대가 적혀 온 값(`...Z`, `+09:00`)은 그대로 둔다 — 그때는 브라우저가 옳게 옮긴다.
 * 다른 도메인(보유 소재·거래)도 같은 방식으로 읽는다.
 */
export function parseServerTime(value: string): Date {
  return new Date(value)
}

/** 날짜와 시각. 기한처럼 분 단위가 중요한 자리에 쓴다. */
export function formatDateTime(value: string): string {
  return parseServerTime(value).toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** 날짜만. 목록의 만료일처럼 하루 단위로 읽는 자리에 쓴다. */
export function formatDate(value: string): string {
  return parseServerTime(value).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
