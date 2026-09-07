package com.ssafy.upcycle.trade.repository;

import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Marketplace 목록·검색·필터 전용 조회.
 *
 * <p><b>{@link MaterialListingRepository} 에 넣지 않고 따로 둔 것은 의도적이다.</b> 그쪽은
 * 스키마 인덱스에 정확히 대응하는 단순 조회만 모아둔 파일이고, 여기는 조건이 계속 붙는
 * 검색 쿼리다. 성격이 달라 같이 두면 파일이 두 종류의 이유로 자란다. 한 엔티티에 여러
 * Repository 인터페이스를 두는 것은 Spring Data 가 지원하는 구성이다.
 *
 * <p><b>{@code Material} 을 함께 가져오는 것이 핵심이다.</b> 필터 조건(소재 종류·출처·등급·
 * 이름)이 전부 {@code material} 쪽에 있고, 목록 카드도 그 값들을 보여준다. 판매 글만 읽고
 * 카드마다 소재를 다시 조회하면 그게 정확히 N+1 이다. 두 테이블 사이에 JPA 연관이 없어
 * ({@code materialId} 가 그냥 {@code Long} 이다) 명시적 entity join 으로 묶는다.
 *
 * <p><b>Verification 필터는 없다.</b> 티켓 -298 이 요구하지만 판정 결과를 담는 컬럼이 아직
 * DB 에 없다 — {@code MaterialDetailResponse} 가 항상 {@code REGISTERED} 를 내려보내는 것과
 * 같은 이유다. 없는 값으로 거르면 "검증된 소재만" 이 실제로는 전부 또는 전무가 된다.
 * Verification 에픽이 상태를 저장하기 시작하면 조건 한 줄로 붙는다.
 */
public interface ListingSearchRepository extends Repository<MaterialListing, Long> {

    /**
     * 조건에 맞는 판매 글을 소재 정보와 함께 페이지로 읽는다.
     *
     * <p>모든 필터는 {@code null} 이면 무시된다 — 조건마다 메서드를 나누면 조합 폭발이 난다.
     * MySQL 은 이 형태를 상수 조건으로 접어내므로 실행 계획이 나빠지지 않는다.
     *
     * <p>{@code keyword} 는 소재 이름 부분 일치다. 선행 와일드카드라 인덱스를 타지 못하는
     * 것을 알고 있다. 판매 글 수가 인덱스를 논할 규모가 아니고, 검색 품질이 필요해지면
     * 전문 검색으로 바꿔야 하지 컬럼 인덱스로 해결되지 않는다.
     *
     * @return {@code [MaterialListing, Material]} 쌍. 순서가 계약이다
     */
    @Query(value = """
            select l, m
            from MaterialListing l
            join Material m on m.id = l.materialId
            where l.status = :status
              and (:sellerId is null or l.sellerId = :sellerId)
              and (:materialType is null or m.materialType = :materialType)
              and (:rootOrigin is null or m.rootOrigin = :rootOrigin)
              and (:grade is null or m.grade = :grade)
              and (:keyword is null or m.name like concat('%', :keyword, '%'))
              and (:minPricePerKg is null or l.pricePerKg >= :minPricePerKg)
              and (:maxPricePerKg is null or l.pricePerKg <= :maxPricePerKg)
              and (:minAvailableQuantity is null or l.availableQuantity >= :minAvailableQuantity)
            """,
            countQuery = """
            select count(l)
            from MaterialListing l
            join Material m on m.id = l.materialId
            where l.status = :status
              and (:sellerId is null or l.sellerId = :sellerId)
              and (:materialType is null or m.materialType = :materialType)
              and (:rootOrigin is null or m.rootOrigin = :rootOrigin)
              and (:grade is null or m.grade = :grade)
              and (:keyword is null or m.name like concat('%', :keyword, '%'))
              and (:minPricePerKg is null or l.pricePerKg >= :minPricePerKg)
              and (:maxPricePerKg is null or l.pricePerKg <= :maxPricePerKg)
              and (:minAvailableQuantity is null or l.availableQuantity >= :minAvailableQuantity)
            """)
    Page<Object[]> search(
            @Param("status") ListingStatus status,
            @Param("sellerId") Long sellerId,
            @Param("materialType") MaterialType materialType,
            @Param("rootOrigin") MaterialRootOrigin rootOrigin,
            @Param("grade") String grade,
            @Param("keyword") String keyword,
            @Param("minPricePerKg") Long minPricePerKg,
            @Param("maxPricePerKg") Long maxPricePerKg,
            @Param("minAvailableQuantity") Long minAvailableQuantity,
            Pageable pageable);

    /**
     * 구매 처리 직전에 판매 글을 <b>행 잠금</b>으로 읽는다.
     *
     * <p>이것이 없으면 잔여 12kg 짜리 판매 글을 두 사람이 동시에 10kg 씩 살 수 있다.
     * 둘 다 "12kg 남음" 을 읽고 각자 검사를 통과한 뒤 각자 차감하면 잔여가 음수가 되고,
     * {@code chk_material_listing_quantities} 위반으로 <b>나중 한 건만</b> 500 이 된다.
     * 사용자에게는 아무 설명이 안 되는 실패다.
     *
     * <p>{@code SELECT ... FOR UPDATE} 라 두 번째 요청은 첫 번째 트랜잭션이 끝날 때까지
     * 기다렸다가 <b>줄어든 잔여</b>를 읽는다. 그러면 초과분은 도메인 오류
     * ({@code LISTING_QUANTITY_EXCEEDS_AVAILABLE}) 로 정상 거절된다.
     *
     * <p>낙관적 락(@Version)을 쓰지 않은 이유는 충돌 시 재시도 루프가 필요한데, 인기 있는
     * 판매 글일수록 재시도가 몰려 오히려 느려지기 때문이다. 잠금 구간은 한 행에 대한 짧은
     * 트랜잭션이라 대기 비용이 작다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MaterialListing l where l.id = :listingId")
    Optional<MaterialListing> findByIdForUpdate(@Param("listingId") Long listingId);

    /**
     * 판매 글 단건을 소재와 함께. 상세 화면이 소재를 다시 조회하지 않게 한다.
     *
     * <p>{@code List} 로 받는다. 단건 반환형({@code Object[]})으로 선언하면 두 selection 을
     * 한 행으로 볼지 두 행으로 볼지가 모호해진다 — 결과 없음도 예외가 아니라 빈 목록으로
     * 받는 편이 호출부에서 다루기 쉽다.
     */
    @Query("""
            select l, m
            from MaterialListing l
            join Material m on m.id = l.materialId
            where l.id = :listingId
            """)
    List<Object[]> findWithMaterial(@Param("listingId") Long listingId);
}
