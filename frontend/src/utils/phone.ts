// 전화번호 입력 표시 유틸.
//
// 회원가입(auth)에만 있던 것을 올렸다. 배송지 주소록도 수령인 연락처를 같은 모양으로
// 받아야 하는데, 두 벌이 되면 한쪽만 고쳐져 화면마다 하이픈 위치가 달라진다
// (ARCHITECTURE §7: 두 번째 도메인이 필요로 할 때 올린다).

const digitsOnly = (value: string) => value.replace(/\D/g, '')

/**
 * 입력 중인 번호에 하이픈을 넣는다.
 *
 * 서울 국번(02)만 자릿수가 다르다 — 지역번호가 두 자리라 나머지를 3-4 가 아니라 4-4 로
 * 끊어야 한다. 이 분기가 없으면 `02-1234-5678` 이 `021-234-5678` 로 보인다.
 */
export function formatPhoneNumber(value: string) {
  const rawDigits = digitsOnly(value)
  const isSeoulNumber = rawDigits.startsWith('02')
  const digits = rawDigits.slice(0, isSeoulNumber ? 10 : 11)

  if (isSeoulNumber) {
    if (digits.length <= 2) return digits
    if (digits.length <= 5) return `${digits.slice(0, 2)}-${digits.slice(2)}`
    if (digits.length <= 9) return `${digits.slice(0, 2)}-${digits.slice(2, 5)}-${digits.slice(5)}`
    return `${digits.slice(0, 2)}-${digits.slice(2, 6)}-${digits.slice(6)}`
  }

  if (digits.length <= 3) return digits
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  if (digits.length <= 10) return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
}

/** 휴대전화 번호 형식. Backend 의 `ConsumerSignupRequest` 정규식과 같은 규칙이다. */
export const MOBILE_PHONE_PATTERN = /^01[016789]-?\d{3,4}-?\d{4}$/
