// 소비자 배송지 주소록 API.
//
// 다섯 호출이 같은 리소스의 생애를 다루고 응답 타입이 하나라 한 파일에 모은다
// (`productionBatchApi.ts` 가 같은 판단을 한다).
//
// ⚠️ **경로가 `/users/me` 가 아니라 `/consumers/me` 다.** 사업자의 `/companies/me` 와
// 짝을 이루는 이름이고, 사용자를 경로 변수로 받지 않는다 — 서버가 JWT 에서 얻는다.
// `{userId}` 가 있었다면 남의 주소록을 들여다볼 길이 생긴다.

import { apiClient } from '../../../services/apiClient.ts'
import type { ConsumerAddress, ConsumerAddressRequest } from '../types/consumerAddress.types.ts'

const PATH = '/v1/consumers/me/addresses'

/**
 * 내 배송지 목록 — `GET /v1/consumers/me/addresses`
 *
 * **빈 배열이 정상 상태다.** 주소를 받기 전에 가입한 계정은 배송지가 없고, 서버도 그때
 * 에러가 아니라 `[]` 를 준다. 화면은 그것을 "결과 없음" 으로 다뤄야 한다 (ARCHITECTURE §8).
 */
export async function getConsumerAddresses(): Promise<ConsumerAddress[]> {
  const { data } = await apiClient.get<ConsumerAddress[]>(PATH)
  return data
}

/**
 * 배송지 등록 — `POST /v1/consumers/me/addresses`
 *
 * 주소록이 비어 있을 때 넣는 첫 배송지는 **서버가 기본으로 만든다.** 화면이 따로
 * 지정하지 않는다 — 선택지가 하나뿐이라 물을 필요가 없다.
 *
 * 20건을 넘기면 `CONSUMER_ADDRESS_LIMIT_EXCEEDED`(422)로 거절된다.
 */
export async function addConsumerAddress(
  request: ConsumerAddressRequest,
): Promise<ConsumerAddress> {
  const { data } = await apiClient.post<ConsumerAddress>(PATH, normalize(request))
  return data
}

/**
 * 배송지 수정 — `PUT /v1/consumers/me/addresses/{addressId}`
 *
 * 남의 배송지는 `CONSUMER_ADDRESS_NOT_FOUND`(404)다 — 403 이 아니다. 있는지 없는지를
 * 알려주지 않는 쪽이 안전하다.
 */
export async function updateConsumerAddress(
  addressId: number,
  request: ConsumerAddressRequest,
): Promise<ConsumerAddress> {
  const { data } = await apiClient.put<ConsumerAddress>(
    `${PATH}/${addressId}`,
    normalize(request),
  )
  return data
}

/** 배송지 삭제 — `DELETE /v1/consumers/me/addresses/{addressId}` */
export async function deleteConsumerAddress(addressId: number): Promise<void> {
  await apiClient.delete(`${PATH}/${addressId}`)
}

/**
 * 기본 배송지 지정 — `PATCH /v1/consumers/me/addresses/{addressId}/default`
 *
 * 수정과 경로가 갈린 이유가 여기 있다. 주소를 고치다가 기본 배송지가 함께 옮겨 가면
 * 사용자는 그 사실을 모른 채 다른 곳으로 물건을 받는다.
 */
export async function setDefaultConsumerAddress(addressId: number): Promise<ConsumerAddress> {
  const { data } = await apiClient.patch<ConsumerAddress>(`${PATH}/${addressId}/default`)
  return data
}

/**
 * 보내기 전에 다듬는다.
 *
 * 앞뒤 공백은 사용자가 붙이려고 붙인 것이 아니다. 특히 주소는 **택배 송장에 인쇄되는
 * 값**이라 공백 하나가 남아도 좋을 것이 없다.
 */
function normalize(request: ConsumerAddressRequest): ConsumerAddressRequest {
  return {
    alias: request.alias.trim(),
    receiverName: request.receiverName.trim(),
    receiverPhone: request.receiverPhone.trim(),
    zipCode: request.zipCode.trim(),
    address: request.address.trim(),
    addressDetail: request.addressDetail.trim(),
  }
}
