import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import {
  addConsumerAddress,
  deleteConsumerAddress,
  setDefaultConsumerAddress,
  updateConsumerAddress,
} from '../api/consumerAddressApi.ts'
import { consumerAddressKeys } from '../api/consumerAddressKeys.ts'
import type { ConsumerAddress, ConsumerAddressRequest } from '../types/consumerAddress.types.ts'

/**
 * 배송지 등록·수정·삭제·기본 지정.
 *
 * **네 동작이 한 훅에 있다.** 전부 같은 목록 하나를 바꾸고, 화면도 한 곳에서 모두 쓴다.
 * 훅을 넷으로 나누면 화면이 네 번 import 하고 네 개의 `isPending` 을 따로 들고 있어야 한다.
 *
 * **응답을 캐시에 직접 넣지 않고 목록을 다시 읽는다.** 기본 배송지 지정은 **두 행을 바꾼다**
 * — 새 기본이 켜지고 옛 기본이 꺼진다. 서버는 바뀐 한 건만 돌려주므로, 그것만 캐시에
 * 반영하면 화면에 기본 배송지가 둘로 보인다.
 */
export function useConsumerAddressActions() {
  const queryClient = useQueryClient()

  // 목록 하나만 비우면 된다. 배송지는 다른 도메인의 캐시를 낡게 만들지 않는다 —
  // 주문 화면이 생기면 그때 배송지 선택 캐시를 여기 더한다.
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: consumerAddressKeys.lists() })
  }

  const add = useMutation<ConsumerAddress, AppError, ConsumerAddressRequest>({
    mutationFn: addConsumerAddress,
    onSuccess: invalidate,
  })

  const update = useMutation<
    ConsumerAddress,
    AppError,
    { addressId: number; request: ConsumerAddressRequest }
  >({
    mutationFn: ({ addressId, request }) => updateConsumerAddress(addressId, request),
    onSuccess: invalidate,
  })

  const remove = useMutation<void, AppError, number>({
    mutationFn: deleteConsumerAddress,
    onSuccess: invalidate,
  })

  const setDefault = useMutation<ConsumerAddress, AppError, number>({
    mutationFn: setDefaultConsumerAddress,
    onSuccess: invalidate,
  })

  return { add, update, remove, setDefault }
}
