const digitsOnly = (value: string) => value.replace(/\D/g, '')

export function formatBusinessNumber(value: string) {
  const digits = digitsOnly(value).slice(0, 10)

  if (digits.length <= 3) return digits
  if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`
}

// formatPhoneNumber 는 src/utils/phone.ts 로 옮겼다 — 배송지 주소록도 같은 모양으로
// 연락처를 받는다. 사업자등록번호는 가입 화면에만 있어 여기 남는다.
