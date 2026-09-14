import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getConsumerAddresses } from '../api/consumerAddressApi.ts'
import { consumerAddressKeys } from '../api/consumerAddressKeys.ts'
import type { ConsumerAddress } from '../types/consumerAddress.types.ts'

/**
 * 내 배송지 목록.
 *
 * 페이징이 없다 — 서버가 20건으로 제한해 배열을 그대로 준다.
 *
 * **빈 배열과 실패를 구분해야 한다.** 주소를 받기 전에 가입한 계정은 배송지가 0건이고
 * 그것은 정상이다. 실패와 같은 화면을 보여주면 사용자는 고장으로 읽는다.
 */
export function useConsumerAddresses() {
  return useQuery<ConsumerAddress[], AppError>({
    queryKey: consumerAddressKeys.lists(),
    queryFn: getConsumerAddresses,
  })
}
