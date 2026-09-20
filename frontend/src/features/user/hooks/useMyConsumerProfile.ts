import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMyConsumerProfile } from '../api/consumerProfileApi.ts'
import { consumerProfileKeys } from '../api/consumerProfileKeys.ts'
import type { ConsumerProfile } from '../types/consumerProfile.types.ts'

/**
 * 내 개인정보(이름·연락처). 로그인 응답으로 받은 값을 화면이 들고 있다가 쓰면 다른
 * 탭에서 고쳤을 때 어긋난다 — 수정 폼이 열릴 때마다 정본을 다시 읽는다.
 */
export function useMyConsumerProfile() {
  return useQuery<ConsumerProfile, AppError>({
    queryKey: consumerProfileKeys.me(),
    queryFn: getMyConsumerProfile,
  })
}
