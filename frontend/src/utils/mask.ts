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

/** 상세주소 마스킹 — 길이는 유지하되 전부 `*`로 치환해 존재 여부만 알 수 있게 한다. */
export function maskAddressDetail(addressDetail: string): string {
  return '*'.repeat(addressDetail.length)
}

/**
 * 도로명주소 마스킹. 시/도·시/군/구 두 어절까지는 남기고 나머지는 글자 수만 유지한 채
 * `*`로 치환한다 — 대략적인 지역만 알 수 있고 상세 도로명·번지는 가려진다.
 */
export function maskAddressLine(addressLine: string): string {
  const words = addressLine.split(' ')
  if (words.length <= 2) return maskAddressDetail(addressLine)

  const visible = words.slice(0, 2).join(' ')
  const restLength = addressLine.length - visible.length - 1
  return `${visible} ${'*'.repeat(Math.max(restLength, 0))}`
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
