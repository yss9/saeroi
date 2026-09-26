import { useListings } from '../../marketplace/hooks/useListings.ts'

export interface SupplierListingSummary {
  onSale: number
  soldOut: number
  canceled: number
  /** 판매 중인데 이력이 남지 않은 글. 손봐야 할 것이 있는지가 이 화면의 첫 질문이다 */
  unrecorded: number
  /** 판매 중인 글의 남은 수량 합계 (gram) */
  availableGram: number
  isPending: boolean
}

/**
 * 판매 관리 화면 머리에 올리는 집계.
 *
 * <b>상태별로 따로 묻는다.</b> 서버가 기본으로 `ON_SALE` 만 돌려주므로(getListings 주석)
 * 한 번의 조회로는 품절·내림 건수를 알 수 없다. 대신 건수만 필요한 쪽은 `size: 1` 로
 * 받아 `totalElements` 만 읽는다 — 목록 내용은 버린다.
 *
 * <b>판매 중만 통째로 받는 이유</b>는 `unrecorded` 때문이다. "이력이 남지 않은 글이
 * 몇 건인가" 는 서버가 세어 주지 않으므로(그런 필터가 없다) 행을 받아 직접 세야 한다.
 * 한 페이지만 세면 2페이지의 미기록 건을 놓쳐 <b>숫자가 조용히 틀린다</b> — 그래서
 * 넉넉한 size 로 한 번에 받는다.
 *
 * 목록 렌더용 조회(`ListingList`)와 Query Key 가 다르므로 캐시를 공유하지 않는다.
 * 요청이 셋 늘지만 전부 작고, staleTime 5초 안에서는 재사용된다.
 */
export function useSupplierListingSummary(sellerId: number | undefined): SupplierListingSummary {
  // 200 은 "이 프로젝트의 한 공급업체가 가질 법한 판매글 수" 보다 충분히 크다. 넘어가면
  // 집계가 조금 작아지는데, 그때는 페이지네이션이 아니라 서버 집계가 필요한 규모다.
  const onSale = useListings({ sellerId, status: 'ON_SALE', size: 200 })
  const soldOut = useListings({ sellerId, status: 'SOLD_OUT', size: 1 })
  const canceled = useListings({ sellerId, status: 'CANCELED', size: 1 })

  const rows = onSale.data?.content ?? []

  return {
    onSale: onSale.data?.totalElements ?? 0,
    soldOut: soldOut.data?.totalElements ?? 0,
    canceled: canceled.data?.totalElements ?? 0,
    // chainListingId 가 null = 판매 등록 때 서명이 끝나지 않아 체인에 기록이 없는 글.
    unrecorded: rows.filter((row) => row.chainListingId === null).length,
    availableGram: rows.reduce((sum, row) => sum + row.availableQuantity, 0),
    isPending: onSale.isPending || soldOut.isPending || canceled.isPending,
  }
}
