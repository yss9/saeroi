package com.ssafy.upcycle.trade.repository;

import static com.ssafy.upcycle.material.entity.QMaterial.material;
import static com.ssafy.upcycle.trade.entity.QMaterialListing.materialListing;
import static com.ssafy.upcycle.user.entity.QUser.user;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.ListingSearchCondition;
import com.ssafy.upcycle.trade.dto.ListingSearchCondition.ListingSort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * Marketplace 목록·검색·필터. <b>QueryDSL 로 짜는 것이 팀 계약이다</b>
 * ({@code MaterialBalanceQueryRepository} 주석 참고).
 *
 * <h2>JPQL {@code @Query} 에서 옮겨온 이유</h2>
 *
 * <p>이전 구현은 {@code value} 와 {@code countQuery} 에 <b>같은 where 절을 두 번</b>
 * 적어야 했다. 조건이 여덟 개였으니 여덟 개씩 두 벌이다. 필터를 하나 추가할 때 한쪽만
 * 고치면 <b>목록과 총 개수가 어긋난다</b> — "총 3건" 이라고 표시하면서 2건만 보여주고,
 * 2페이지는 비어 있는 상태가 된다. 그 불일치는 목록 테스트와 count 테스트를 따로 쓰지
 * 않으면 걸리지도 않는다. 여기서는 {@link #where} 하나를 목록과 count 가 공유한다.
 *
 * <p>{@code (:param is null or ...)} 도 사라졌다. 값이 없으면 조건 자체를 붙이지 않으므로
 * MySQL 이 접어낼 상수 조건이 애초에 생기지 않는다.
 *
 * <p>그리고 컬럼 이름을 컴파일러가 검사한다. JPQL 문자열에서는 {@code pricePerKg} 를
 * 잘못 적어도 런타임에야 터졌다.
 *
 * <h2>{@code Material} 과 판매자를 함께 가져온다</h2>
 *
 * <p>필터 조건(소재 종류·출처·등급·이름)이 전부 {@code material} 쪽에 있고, 목록 카드는
 * 그 값들에 더해 판매자 이름을 보여준다. 판매 글만 읽고 카드마다 소재와 사용자를 다시
 * 조회하면 그게 정확히 N+1 이다. 두 테이블 사이에 JPA 연관이 없어
 * ({@code materialId} 가 그냥 {@code Long} 이다) 명시적 join 으로 묶는다.
 *
 * <h2>Verification 필터는 아직 없다</h2>
 *
 * <p>티켓 -298 이 요구하지만 판정 결과를 담는 컬럼이 없다. 검증 단계는
 * {@code MaterialVerificationStateResolver} 가 조회 시점에 계산하는데, 그중
 * {@code TRANSACTION_VERIFIED} 만 {@code material_trade.buyer_signed_at} 이라는 실제
 * 컬럼에서 나온다. 그 한 단계는 여기에 조건으로 붙일 수 있고, 나머지는 근거가 될 컬럼이
 * 아직 없다. 절반만 되는 필터를 내보내면 "검증된 소재만" 이 실제로는 전부 또는 전무가
 * 되므로, 나머지 단계의 근거가 생길 때 한 번에 붙인다.
 */
@Repository
@RequiredArgsConstructor
public class ListingSearchQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 조건에 맞는 판매 글을 소재·판매자와 함께 페이지로 읽는다.
     *
     * <p>목록과 count 가 {@link #where} 를 공유하는 것이 이 메서드의 요점이다.
     *
     * <p>{@link PageableExecutionUtils} 가 <b>불필요한 count 쿼리를 생략</b>한다 —
     * 첫 페이지이고 결과가 페이지 크기보다 작으면 총 개수를 이미 아는 셈이라 세지 않는다.
     */
    public Page<ListingResponse> search(ListingSearchCondition condition, Pageable pageable) {
        Predicate where = where(condition);

        List<ListingResponse> content = queryFactory
                .select(materialListing, material, user)
                .from(materialListing)
                .join(material)
                .on(material.id.eq(materialListing.materialId))
                .join(user)
                .on(user.id.eq(materialListing.sellerId))
                .where(where)
                .orderBy(orderOf(condition.sort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(row -> ListingResponse.of(
                        row.get(materialListing), row.get(material), row.get(user)))
                .toList();

        return PageableExecutionUtils.getPage(content, pageable, () -> count(where));
    }

    /** 판매 글 단건을 소재·판매자와 함께. 상세 화면이 세 번 호출하지 않게 한다. */
    public ListingResponse findDetail(Long listingId) {
        return queryFactory
                .select(materialListing, material, user)
                .from(materialListing)
                .join(material)
                .on(material.id.eq(materialListing.materialId))
                .join(user)
                .on(user.id.eq(materialListing.sellerId))
                .where(materialListing.id.eq(listingId))
                .fetch()
                .stream()
                .map(row -> ListingResponse.of(
                        row.get(materialListing), row.get(material), row.get(user)))
                .findFirst()
                .orElse(null);
    }

    /**
     * 목록과 count 가 함께 쓰는 조건.
     *
     * <p><b>{@code null} 인 조건은 붙지 않는다.</b> {@link BooleanBuilder} 는 {@code null}
     * 을 넣으면 그 항을 무시한다. 그래서 아래 각 메서드가 값이 없을 때 {@code null} 을
     * 돌려주는 것이 곧 "이 필터는 걸지 않는다" 는 뜻이다.
     *
     * <p>전부 {@code AND} 다. 조합해서 걸면 교집합이 된다 —
     * {@code MarketplaceApiTests} 의 조합 테스트가 이것을 고정한다.
     */
    private Predicate where(ListingSearchCondition condition) {
        return new BooleanBuilder()
                .and(materialListing.status.eq(condition.statusOrDefault()))
                .and(sellerIdEq(condition.sellerId()))
                .and(materialIdEq(condition.materialId()))
                .and(materialTypeEq(condition.materialType()))
                .and(rootOriginEq(condition.rootOrigin()))
                .and(generationTypeEq(condition.generationType()))
                .and(gradeEq(condition.gradeOrNull()))
                .and(nameContains(condition.keywordOrNull()))
                .and(pricePerKgGoe(condition.minPricePerKg()))
                .and(pricePerKgLoe(condition.maxPricePerKg()))
                .and(availableQuantityGoe(condition.minAvailableQuantity()));
    }

    private long count(Predicate where) {
        Long total = queryFactory
                .select(materialListing.count())
                .from(materialListing)
                .join(material)
                .on(material.id.eq(materialListing.materialId))
                .where(where)
                .fetchOne();
        return total == null ? 0L : total;
    }

    private static BooleanExpression sellerIdEq(Long sellerId) {
        return sellerId == null ? null : materialListing.sellerId.eq(sellerId);
    }

    private static BooleanExpression materialIdEq(Long materialId) {
        return materialId == null ? null : materialListing.materialId.eq(materialId);
    }

    private static BooleanExpression materialTypeEq(MaterialType materialType) {
        return materialType == null ? null : material.materialType.eq(materialType);
    }

    private static BooleanExpression rootOriginEq(MaterialRootOrigin rootOrigin) {
        return rootOrigin == null ? null : material.rootOrigin.eq(rootOrigin);
    }

    /**
     * 생성 유형. {@code rootOrigin} 과 <b>독립된 축</b>이라 별도 조건이다 —
     * "폐기물 + 가공 산출물" 과 "폐기물 + 최초 등록" 은 다른 물건이고, 구매자는 둘을
     * 갈라서 봐야 한다 (화면 설계서 B03의 2축 필터).
     */
    private static BooleanExpression generationTypeEq(MaterialGenerationType generationType) {
        return generationType == null ? null : material.generationType.eq(generationType);
    }

    private static BooleanExpression gradeEq(String grade) {
        return grade == null ? null : material.grade.eq(grade);
    }

    /**
     * 소재 이름 부분 일치.
     *
     * <p>선행 와일드카드라 인덱스를 타지 못하는 것을 알고 있다. 판매 글 수가 인덱스를
     * 논할 규모가 아니고, 검색 품질이 필요해지면 전문 검색으로 바꿔야 하지 컬럼 인덱스로
     * 해결되지 않는다.
     */
    private static BooleanExpression nameContains(String keyword) {
        return keyword == null ? null : material.name.contains(keyword);
    }

    private static BooleanExpression pricePerKgGoe(Long minPricePerKg) {
        return minPricePerKg == null ? null : materialListing.pricePerKg.goe(minPricePerKg);
    }

    private static BooleanExpression pricePerKgLoe(Long maxPricePerKg) {
        return maxPricePerKg == null ? null : materialListing.pricePerKg.loe(maxPricePerKg);
    }

    private static BooleanExpression availableQuantityGoe(Long minAvailableQuantity) {
        return minAvailableQuantity == null
                ? null
                : materialListing.availableQuantity.goe(minAvailableQuantity);
    }

    /**
     * 정렬.
     *
     * <p><b>두 번째 키가 있는 것이 중요하다.</b> 단가나 등록 시각이 같은 행이 여러 개면
     * DB 는 순서를 보장하지 않는다. 그러면 2페이지에 1페이지에서 본 글이 다시 나오거나
     * 어떤 글은 아예 안 보인다. {@code id} 는 유일하므로 순서를 확정한다.
     *
     * <p>이전 JPQL 구현은 {@code JpaSort.unsafe("l.createdAt")} 로 별칭을 문자열에 적어야
     * 했다. 여기서는 표현식이라 별칭 문제가 없다.
     */
    private static OrderSpecifier<?>[] orderOf(ListingSort sort) {
        OrderSpecifier<?> tieBreaker = materialListing.id.desc();
        ListingSort effective = sort == null ? ListingSort.LATEST : sort;
        return switch (effective) {
            case PRICE_ASC -> new OrderSpecifier<?>[] {
                materialListing.pricePerKg.asc(), tieBreaker};
            case PRICE_DESC -> new OrderSpecifier<?>[] {
                materialListing.pricePerKg.desc(), tieBreaker};
            case QUANTITY_DESC -> new OrderSpecifier<?>[] {
                materialListing.availableQuantity.desc(), tieBreaker};
            case LATEST -> new OrderSpecifier<?>[] {
                materialListing.createdAt.desc(), tieBreaker};
        };
    }
}
