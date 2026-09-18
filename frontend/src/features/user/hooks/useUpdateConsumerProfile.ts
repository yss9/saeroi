import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { AppError } from '../../../utils/errors.ts'
import { updateMyConsumerProfile } from '../api/consumerProfileApi.ts'
import { consumerProfileKeys } from '../api/consumerProfileKeys.ts'
import type { ConsumerProfile, ConsumerProfileUpdateRequest } from '../types/consumerProfile.types.ts'

/**
 * 개인정보 수정.
 *
 * **응답을 캐시에 곧바로 넣는다** (`useUpdateCompanyProfile`과 같은 이유) — 무효화만
 * 하면 저장 직후 한 박자 동안 옛 값이 남아 "방금 고친 게 안 먹었다"로 보인다.
 *
 * **`authStore`의 이름도 같이 바꾼다.** 헤더·사이드바 인사말이 로그인 시점 스냅샷을
 * 그대로 들고 있어서, 여기서 patchUser 하지 않으면 다른 화면을 오갈 때까지 옛 이름이
 * 남는다.
 */
export function useUpdateConsumerProfile() {
  const queryClient = useQueryClient()
  const patchUser = useAuthStore((state) => state.patchUser)

  return useMutation<ConsumerProfile, AppError, ConsumerProfileUpdateRequest>({
    mutationFn: updateMyConsumerProfile,
    onSuccess: (profile) => {
      queryClient.setQueryData(consumerProfileKeys.me(), profile)
      void queryClient.invalidateQueries({ queryKey: consumerProfileKeys.me() })
      patchUser({ name: profile.name })
    },
  })
}
