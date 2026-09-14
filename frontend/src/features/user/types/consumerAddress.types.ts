/**
 * 소비자 배송지.
 *
 * Backend 원본: `user/dto/ConsumerAddressResponse.java`.
 *
 * **가입할 때 입력한 주소가 첫 배송지로 들어와 있다** (`ConsumerAddress.ofSignup`, 이름은
 * "기본 배송지"). 그래서 이 목록이 비어 있는 계정은 주소를 받기 전에 가입한 사람뿐이고,
 * 화면은 그 상태도 정상으로 다뤄야 한다.
 */
export interface ConsumerAddress {
  addressId: number
  /** 사용자가 붙이는 이름("집", "회사"). 주소를 고를 때 보이는 값이다 */
  alias: string
  receiverName: string
  receiverPhone: string
  zipCode: string
  address: string
  /** 동·호수. 서버가 빈 값을 저장하지 않아 null 로 온다 */
  addressDetail: string | null
  isDefault: boolean
}

/**
 * 배송지 등록·수정 요청. 두 경로가 같은 본문을 쓴다.
 *
 * ⚠️ **`isDefault` 가 없다.** 기본 배송지 지정은 `PATCH /{addressId}/default` 하나로만
 * 한다 — 수정하면서 기본이 조용히 옮겨 가면 사용자가 눈치채지 못한 채 다른 곳으로 물건을
 * 받는다. 주소록이 비어 있을 때 등록하는 첫 배송지는 서버가 알아서 기본으로 만든다.
 */
export interface ConsumerAddressRequest {
  alias: string
  receiverName: string
  receiverPhone: string
  zipCode: string
  address: string
  addressDetail: string
}

/**
 * 한 계정이 가질 수 있는 배송지 수.
 *
 * 서버가 `CONSUMER_ADDRESS_LIMIT_EXCEEDED`(422)로 거절하는 지점과 같은 값이다. 화면이
 * 먼저 알려주지 않으면 사용자는 주소를 다 입력하고 저장을 눌러야 한계를 알게 된다.
 */
export const CONSUMER_ADDRESS_LIMIT = 20
