package com.ssafy.upcycle.trade.repository;

import com.ssafy.upcycle.trade.entity.MaterialListing;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 판매 글을 <b>행 잠금</b>으로 읽는 경로. 이 메서드 하나만 있다.
 *
 * <p>검색·목록은 {@link ListingSearchQueryRepository}(QueryDSL)로 옮겼다. 여기 남은 것은
 * 조건 조립이 아니라 <b>동시성 제어</b>라 성격이 다르다 — PK 단건 조회에 잠금 모드만
 * 얹는 일이고, Spring Data 의 {@code @Lock} 이 그걸 가장 짧게 표현한다.
 *
 * <h2>왜 잠금이 필요한가</h2>
 *
 * <p>이것이 없으면 잔여 12kg 짜리 판매 글을 두 사람이 동시에 10kg 씩 살 수 있다.
 * 둘 다 "12kg 남음" 을 읽고 각자 검사를 통과한 뒤 각자 차감하면 잔여가 음수가 되고,
 * {@code chk_material_listing_quantities} 위반으로 <b>나중 한 건만</b> 500 이 된다.
 * 사용자에게는 아무 설명이 안 되는 실패다.
 *
 * <p>{@code SELECT ... FOR UPDATE} 라 두 번째 요청은 첫 번째 트랜잭션이 끝날 때까지
 * 기다렸다가 <b>줄어든 잔여</b>를 읽는다. 그러면 초과분은 도메인 오류
 * ({@code LISTING_QUANTITY_EXCEEDS_AVAILABLE}) 로 정상 거절된다.
 * {@code ConcurrentPurchaseTests} 가 이 동작을 고정한다 — 잠금을 제거하면 그 테스트가
 * 성공 2건을 잡아낸다.
 *
 * <p>낙관적 락({@code @Version})을 쓰지 않은 이유는 충돌 시 재시도 루프가 필요한데,
 * 인기 있는 판매 글일수록 재시도가 몰려 오히려 느려지기 때문이다. 잠금 구간은 한 행에
 * 대한 짧은 트랜잭션이라 대기 비용이 작다.
 */
public interface ListingLockRepository extends Repository<MaterialListing, Long> {

    /**
     * 구매·취소 처리 직전에 판매 글을 행 잠금으로 읽는다.
     *
     * <p>구매(차감)와 취소(복구) 양쪽이 같은 행을 갱신하므로 <b>둘 다</b> 이 메서드를
     * 써야 한다. 한쪽만 잠그면 다른 쪽의 갱신이 사라진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MaterialListing l where l.id = :listingId")
    Optional<MaterialListing> findByIdForUpdate(@Param("listingId") Long listingId);
}
