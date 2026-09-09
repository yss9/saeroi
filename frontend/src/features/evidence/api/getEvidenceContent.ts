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
 * <h2>`baseURL` 을 비우는 이유</h2>
 *
 * <p>`contentPath` 는 서버가 만들어 준 `/api` 를 **포함한** 경로다. `apiClient` 의
 * `baseURL` 이 `/api` 라 그냥 넘기면 `/api/api/evidence/...` 가 된다. 여기서 `/api` 를
 * 잘라내는 대신 `baseURL` 을 비운다 — 잘라내려면 baseURL 문자열을 이 파일이 다시 알아야
 * 하고, `config/env.ts` 한 곳에서만 읽는다는 규칙이 깨진다.
 *
 * <p>경로를 id 로 조립하지 않는 이유는 `EvidenceFile.contentPath` 주석에 있다.
 */
export async function getEvidenceContent(contentPath: string): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(contentPath, {
    baseURL: '',
    // 기본값(JSON 파싱)으로 두면 axios 가 JPEG·PDF 바이트를 문자열로 망가뜨린다.
    responseType: 'blob',
  })
  return data
}
