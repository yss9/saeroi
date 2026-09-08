import { createQueryKeys } from '../../../utils/queryKeys.ts'

const keys = createQueryKeys('evidence')

/**
 * 증빙 캐시 키.
 *
 * <b>대상 종류를 키에 반드시 넣는다.</b> `reference_id` 는 타입마다 따로 매겨지므로
 * id 만으로 키를 만들면 7번 소재의 증빙과 7번 가공작업의 증빙이 같은 캐시를 쓴다.
 */
export const evidenceKeys = {
  ...keys,
  list: (referenceType: string, referenceId: number) =>
    [...keys.lists(), referenceType, referenceId] as const,
  /**
   * 파일 바이트 캐시.
   *
   * <b>목록 키 아래에 두지 않는다.</b> 업로드 성공 후 목록을 무효화하는데 바이트가
   * 그 아래 있으면 이미 받아 둔 사진까지 함께 버려지고 화면이 다시 내려받는다.
   * 파일 바이트는 바뀌지 않으므로(저장 이름이 UUID 이고 덮어쓰기가 없다) 애초에
   * 무효화 대상이 아니다.
   */
  content: (evidenceFileId: number) => [...keys.all, 'content', evidenceFileId] as const,
}
