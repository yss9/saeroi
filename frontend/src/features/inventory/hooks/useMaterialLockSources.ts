import { useListings } from '../../marketplace/hooks/useListings.ts'

export interface LockSource {
  listingId: number
  /** 그 판매글이 잡고 있는 수량 (gram) */
  quantity: number
}

/**
 * 잠긴 수량이 <b>어디에</b> 잡혀 있는지.
 *
 * <b>잠금 조회 API 가 없다.</b> `GET /materials/{id}/locks` 는 Jira 티켓조차 없어서
 * (`inventory.types.ts` TODO), 보유 소재 화면은 "18kg 잠김" 까지만 말하고 어디에
 * 잠겼는지는 말하지 못했다. 사용자는 그 18kg 을 되찾으려면 어느 판매글을 취소해야
 * 하는지 알 수 없었다.
 *
 * <b>내 판매글로 이어 붙여 그 절반을 메운다.</b> 판매 중인 글을 `sellerId` 로 받아
 * `materialId` 로 묶으면 `MARKETPLACE` 목적 잠금은 출처를 말할 수 있다.
 *
 * <b>나머지 절반은 못 한다.</b> `PROCESSING`(가공 요청)과 `PRODUCTION`(생산 배치)
 * 잠금은 소재로 되짚는 조회가 없다. 그쪽은 빈 칸으로 둔다 — 지어내면 틀린 말이
 * 화면에 남고, 그 말을 믿고 엉뚱한 것을 취소하게 된다.
 *
 * 그래서 공급업체 화면은 대체로 채워지고 브랜드·가공업체 화면은 대체로 빈다.
 */
export function useMaterialLockSources(ownerId: number | undefined): {
  byMaterial: Map<number, LockSource[]>
  isPending: boolean
} {
  // 판매 중인 글만 본다. 품절·취소된 글은 수량을 잡고 있지 않다.
  //
  // 200 은 이 프로젝트의 한 판매자가 가질 법한 글 수보다 충분히 크다 —
  // `useSupplierListingSummary` 와 같은 판단이고 같은 쿼리 키를 써서 요청도 겹치지 않는다.
  const listings = useListings({ sellerId: ownerId, status: 'ON_SALE', size: 200 })

  // <b>내가 누구인지 모르면 아무것도 잇지 않는다.</b> `useListings` 에는 `enabled` 가
  // 없어서 `sellerId` 가 undefined 면 조건 없이 전체 판매글을 받아 온다. 그대로 이어
  // 붙이면 <b>남의 판매글이 내 소재의 잠금 출처로 적힌다.</b> 로그인 복원 전 한순간에
  // 그런 화면이 뜰 수 있다.
  const rowsForMe = ownerId === undefined ? [] : (listings.data?.content ?? [])

  const byMaterial = new Map<number, LockSource[]>()
  for (const listing of rowsForMe) {
    const rows = byMaterial.get(listing.materialId) ?? []
    // 판매글이 잡고 있는 것은 <b>남은 수량</b>이다. 이미 팔린 몫은 구매자에게 넘어가
    // 이 소재의 잠금에서 빠진다.
    rows.push({ listingId: listing.listingId, quantity: listing.availableQuantity })
    byMaterial.set(listing.materialId, rows)
  }

  return {
    byMaterial,
    // 로그인 정보가 아직 없으면 조회가 꺼져 있어(enabled) 영원히 pending 이다.
    // 그 상태를 "불러오는 중" 으로 보여주면 빈 칸이 영영 뜨지 않는다.
    isPending: ownerId !== undefined && listings.isPending,
  }
}
