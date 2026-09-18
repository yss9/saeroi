// 소비자 본인 개인정보 API.
//
// 경로가 `/users/me`가 아니라 `/consumers/me`다 — 사업자의 `/companies/me`와 짝을 이루는
// 이름이고, 사용자를 경로 변수로 받지 않는다 (서버가 JWT에서 얻는다).

import { apiClient } from '../../../services/apiClient.ts'
import type {
  ConsumerProfile,
  ConsumerProfileUpdateRequest,
} from '../types/consumerProfile.types.ts'

const PATH = '/v1/consumers/me'

/** 내 개인정보 — `GET /v1/consumers/me` */
export async function getMyConsumerProfile(): Promise<ConsumerProfile> {
  const { data } = await apiClient.get<ConsumerProfile>(PATH)
  return data
}

/**
 * 개인정보 수정 — `PUT /v1/consumers/me`
 *
 * 이메일은 받지 않는다 — 로그인 식별자라 새 주소의 소유 확인이 필요하고, 이름·연락처와
 * 같은 요청에 섞을 수 없다.
 */
export async function updateMyConsumerProfile(
  request: ConsumerProfileUpdateRequest,
): Promise<ConsumerProfile> {
  const { data } = await apiClient.put<ConsumerProfile>(PATH, request)
  return data
}
