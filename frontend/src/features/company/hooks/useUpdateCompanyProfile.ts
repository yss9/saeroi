import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { companyKeys } from '../api/companyKeys.ts'
import type { CompanyProfile } from '../api/getCompanyProfile.ts'
import {
  updateCompanyProfile,
  type CompanyProfileUpdateRequest,
} from '../api/updateCompanyProfile.ts'

/**
 * 내 업체 정보 수정.
 *
 * <b>응답을 캐시에 곧바로 넣는다.</b> 서버가 고쳐진 전체 프로필을 돌려주므로 다시
 * 조회할 이유가 없다. 무효화만 하면 저장 직후 한 박자 동안 <b>옛 값이 남아</b>,
 * 사용자가 방금 고친 것이 안 먹은 줄 안다.
 *
 * 그래도 무효화를 함께 거는 이유는 서버가 저장하며 다른 값을 손댈 수 있어서다
 * (예: 승인 상태). 화면은 즉시 맞고, 곧이어 서버가 정본으로 한 번 더 맞춘다.
 */
export function useUpdateCompanyProfile() {
  const queryClient = useQueryClient()

  return useMutation<CompanyProfile, AppError, CompanyProfileUpdateRequest>({
    mutationFn: updateCompanyProfile,
    onSuccess: (profile) => {
      queryClient.setQueryData(companyKeys.me(), profile)
      void queryClient.invalidateQueries({ queryKey: companyKeys.me() })
    },
  })
}
