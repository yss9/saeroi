import { createQueryKeys } from '../../../utils/queryKeys.ts'

/** production 도메인 Query Key. 이 파일 밖에서 키 배열을 직접 만들지 않는다. */
export const productionKeys = createQueryKeys('production')

/**
 * 배치가 만들어낸 개체와 그 라벨 이미지.
 *
 * **목록·이미지·"아직 받는 중인가" 셋이 같은 접두사를 봐야 한다.** 세 곳이 배열을 따로
 * 지으면 한 곳만 바뀌었을 때 무효화가 조용히 빗나가고, 라벨이 몇 장 남았는지 세는 쪽은
 * 아무것도 세지 못한다.
 */
export const productionItemKeys = {
  /** 이 배치의 개체 관련 요청 전부 (목록 + 라벨 이미지) */
  all: (batchId: number) => [...productionKeys.detail(batchId), 'items'] as const,
  /** 개체 하나의 QR 이미지 */
  qr: (batchId: number, productItemId: number) =>
    [...productionKeys.detail(batchId), 'items', productItemId, 'qr'] as const,
}
