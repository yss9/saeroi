import type { ProductionItem } from '../types/production.types.ts'

/**
 * QR 이미지를 저장할 파일 이름.
 *
 * 개체 코드를 쓴다. 브랜드는 라벨 수십 장을 한 폴더에 내려받아 인쇄소에 넘기는데,
 * `download.png` 가 여러 개면 어느 것이 어느 개체인지 알 수 없고 **잘못된 라벨이 잘못된
 * 제품에 붙는다.**
 *
 * **`qrToken` 은 넣지 않는다.** 파일 이름은 폴더 목록·메신저·인쇄 의뢰서에 그대로 드러나고,
 * 그 값 하나가 비회원 공개 경로의 열쇠다.
 *
 * 미리보기(격자)와 크게 보기(모달) 두 곳에서 저장하므로 규칙을 여기 한 곳에 둔다 — 두 곳이
 * 다른 이름을 만들면 같은 개체의 라벨이 폴더에서 짝지어지지 않는다.
 */
export function qrFileName(item: ProductionItem): string {
  return `${item.itemCode}-qr.png`
}
