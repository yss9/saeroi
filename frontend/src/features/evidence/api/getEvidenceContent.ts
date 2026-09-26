import { apiClient } from '../../../services/apiClient.ts'

/**
 * 증빙 파일 바이트. `GET /api/evidence/{id}/content` (BE S15P21E104-162).
 *
 * <h2>왜 `<img src={contentPath}>` 가 아닌가</h2>
 *
 * <p>이 엔드포인트는 **`Authorization: Bearer` 헤더를 요구한다** (`EvidenceController` 는
 * `permitAll` 목록에 없어 `anyRequest().authenticated()` 로 떨어진다). 토큰은 쿠키가 아니라
 * 스토어에 있고 `apiClient` 요청 인터셉터가 헤더로 붙인다.
 *
 * <p>브라우저가 스스로 보내는 `<img src>` · `<iframe src>` 요청에는 그 인터셉터가 끼어들
 * 자리가 없다. 그래서 **오리진이 같아도 401 이 오고 이미지가 깨진다** — 파일을 볼 자격이
 * 있는 사용자에게도 그렇다. 경로가 절대 URL 이 아니라 우리 오리진 경로인 것과는 무관한
 * 문제다.
 *
 * <p>그래서 바이트를 `apiClient` 로 받아 `blob:` URL 로 바꿔 그린다
 * (`hooks/useObjectUrl.ts`). 인가 실패는 깨진 이미지가 아니라 **읽을 수 있는 오류**로
 * 나온다 — `apiClient` 가 봉투를 벗겨 `AppError` 로 정규화하기 때문이다.
 *
 * <h2>`/api` 접두를 잘라내는 이유</h2>
 *
 * <p>`contentPath` 는 서버가 만들어 준 `/api` 를 **포함한** 경로다. `apiClient` 의
 * `baseURL`(`env.apiBaseUrl`)이 이미 `/api` 라 그냥 넘기면 `/api/api/evidence/...` 가
 * 된다. 그렇다고 `baseURL` 을 비우면(과거 구현) 환경별 `baseURL`(예: `/test` 스택의
 * `/test/api`)을 완전히 우회해 버려, 테스트 환경에서 엉뚱한 백엔드로 요청이 나가는
 * 문제가 있었다 (`getConsumerOrderThumbnail.ts` 와 동일한 패턴으로 접두어만 잘라낸다).
 *
 * <p>경로를 id 로 조립하지 않는 이유는 `EvidenceFile.contentPath` 주석에 있다.
 */
export async function getEvidenceContent(contentPath: string): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(toApiClientPath(contentPath), {
    // 기본값(JSON 파싱)으로 두면 axios 가 JPEG·PDF 바이트를 문자열로 망가뜨린다.
    responseType: 'blob',
  })
  return data
}

/** `/api`가 이미 붙은 서버 경로를 apiClient의 base URL 기준으로 정규화한다. */
function toApiClientPath(contentPath: string): string {
  return contentPath.startsWith('/api/') ? contentPath.slice('/api'.length) : contentPath
}
