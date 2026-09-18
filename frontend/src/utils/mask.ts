// 개인정보 표시용 마스킹 유틸.
//
// 주문 상세(받는사람·상세주소)에만 있던 것을 올렸다. 마이페이지 홈(내 프로필)도 이름·
// 이메일·연락처·배송지를 같은 강도로 가려야 하는데, 두 벌이 되면 한쪽만 고쳐져 화면마다
// 가려지는 정도가 달라진다 (ARCHITECTURE §7: 두 번째 도메인이 필요로 할 때 올린다).

/** 이름 마스킹. 첫/끝 글자만 남기고 가운데는 `*`. */
export function maskName(name: string): string {
  if (name.length <= 1) return name
  if (name.length === 2) return `${name[0]}*`
  return `${name[0]}${'*'.repeat(name.length - 2)}${name[name.length - 1]}`
}

/**
 * 주소 마스킹. 숫자만 `*`로 치환하고 나머지 글자·띄어쓰기는 그대로 둔다 — 동·호수·번지
 * 같은 식별 번호만 가려지고, 도로명·동/구·"동"·"호" 같은 단위 글자는 그대로 남는다.
 *
 * 예) `테헤란로 212` → `테헤란로 ***`, `102동 307호` → `***동 ***호`.
 */
function maskDigits(text: string): string {
  return text.replace(/\d/g, '*')
}

/** 상세주소(동/호수 등) 마스킹. */
export function maskAddressDetail(addressDetail: string): string {
  return maskDigits(addressDetail)
}

/** 도로명주소(시/도·구·도로명·번지) 마스킹. */
export function maskAddressLine(addressLine: string): string {
  return maskDigits(addressLine)
}

/** 이메일 마스킹. `@` 앞부분만 앞 2글자를 남기고 나머지를 가린다. 도메인은 그대로 둔다. */
export function maskEmail(email: string): string {
  const atIndex = email.indexOf('@')
  if (atIndex === -1) return email

  const local = email.slice(0, atIndex)
  const domain = email.slice(atIndex)
  const visibleLength = Math.min(2, local.length)
  const visible = local.slice(0, visibleLength)
  return `${visible}${'*'.repeat(local.length - visibleLength)}${domain}`
}

/** 전화번호 마스킹. `010-1234-5678` → `010-****-5678`. 하이픈 3구간이 아니면 그대로 둔다. */
export function maskPhone(phone: string): string {
  const parts = phone.split('-')
  if (parts.length !== 3) return phone
  return `${parts[0]}-${'*'.repeat(parts[1].length)}-${parts[2]}`
}
