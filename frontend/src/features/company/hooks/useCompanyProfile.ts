import { useQuery } from '@tanstack/react-query'
import { companyKeys } from '../api/companyKeys.ts'
import { getCompanyProfile, type CompanyProfile } from '../api/getCompanyProfile.ts'

/**
 * 내 업체 정보. `enabled` 는 사업자 계정일 때만 호출하도록 페이지가 넘긴다 —
 * 개인(CONSUMER) 계정으로 호출하면 서버가 `COMPANY_PROFILE_FORBIDDEN` 을 낸다.
 */
export function useCompanyProfile(enabled = true) {
  const query = useQuery({
    queryKey: companyKeys.me(),
    queryFn: getCompanyProfile,
    enabled,
  })

  // 같은 레이아웃을 렌더하는 다른 화면의 테스트가 apiClient 를 광범위하게 mock 하면
  // (예: 배열을 돌려주는 공용 mock) 이 화면이 그 응답을 그대로 믿고 깨질 수 있다.
  // 업체 정보로 보이는 모양(companyName 이 string)일 때만 실데이터로 인정한다.
  const profile = isCompanyProfile(query.data) ? query.data : null

  return { ...query, profile }
}

function isCompanyProfile(value: unknown): value is CompanyProfile {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    typeof (value as { companyName?: unknown }).companyName === 'string'
  )
}
