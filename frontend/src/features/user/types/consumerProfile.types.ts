/**
 * 소비자 본인 개인정보. Backend 원본: `user/dto/UserSummaryResponse.java`
 * (`GET/PUT /v1/consumers/me`).
 *
 * **`phone`은 계정 연락처다.** 배송지의 수령인 연락처(`ConsumerAddress.receiverPhone`)와는
 * 다른 값이고 서로 동기화되지 않는다 — 가입 때만 같은 값으로 시작한다.
 */
export interface ConsumerProfile {
  id: number
  email: string
  name: string
  phone: string
  role: string
  status: string
  createdAt: string
}

/**
 * 개인정보 수정 요청.
 *
 * ⚠️ **이름·연락처를 함께 보낸다.** 서버가 두 값을 한 요청으로만 받는다 — 하나만 고치는
 * 화면이어도 나머지 칸엔 지금 값을 그대로 담아 보내야 한다.
 */
export interface ConsumerProfileUpdateRequest {
  name: string
  phone: string
}
