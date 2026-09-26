import { apiClient } from '../../../services/apiClient.ts'
import type { CompanyProfile } from './getCompanyProfile.ts'

/**
 * `PUT /v1/companies/me` 요청. Backend `CompanyProfileUpdateRequest` 와 1:1 로 대응한다.
 *
 * <b>고칠 수 없는 값은 아예 담기지 않는다.</b> 업체명·사업자등록번호·이메일·업체 유형은
 * 신원과 심사에 쓰인 값이라 서버가 받지 않는다 — 화면에서 못 고치게 막는 것이 아니라
 * 계약 자체에 없다.
 *
 * <b>`logoUrl` 은 담지 않는다.</b> 서버는 받지만 브랜드 로고는 파일 업로드
 * (`POST /v1/companies/me/logo`)로 넣는다 — 소비자 화면에 그대로 걸리는 그림이라
 * 우리가 받은 파일이어야 한다. 자유 입력 칸을 두면 아무 주소나 들어간다.
 */
export interface CompanyProfileUpdateRequest {
  representativeName: string
  companyPhone: string
  zipCode: string
  address: string
  /** 선택. 서버는 빈 문자열을 받는다 */
  addressDetail: string
  /** 선택. 서버는 빈 문자열을 받는다 */
  introduction: string
}

/**
 * 내 업체 정보를 고친다.
 *
 * 보유자를 파라미터로 받지 않는다 — 서버가 JWT `sub` 로 판단한다. 아이디를 받으면
 * 남의 업체 정보를 고칠 수 있다.
 *
 * 응답은 <b>고쳐진 전체 프로필</b>이다. 그대로 캐시에 넣으면 다시 조회하지 않아도 된다.
 */
export async function updateCompanyProfile(
  request: CompanyProfileUpdateRequest,
): Promise<CompanyProfile> {
  const { data } = await apiClient.put<CompanyProfile>('/v1/companies/me', request)
  return data
}
