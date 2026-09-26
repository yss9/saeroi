package com.ssafy.upcycle.user.repository;

import static com.ssafy.upcycle.product.entity.QProductModel.productModel;
import static com.ssafy.upcycle.user.entity.QCompanyProfile.companyProfile;
import static com.ssafy.upcycle.user.entity.QUser.user;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.user.dto.PublicBrandSearchCondition;
import com.ssafy.upcycle.user.dto.PublicBrandSort;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * 소비자 BRANDS 목록. <b>브랜드 테이블이 아니라 상품에서 시작한다.</b>
 *
 * <h2>왜 상품을 묶는가</h2>
 *
 * <p>브랜드는 별도 엔티티가 아니다 — {@code users} 행에 {@code company_profile} 이 딸려 있고,
 * {@code product_model.brand_id} 가 그 {@code users.id} 를 FK 값으로 가리킬 뿐이다.
 *
 * <p>목록에 실을 기준이 "판매 중인 공개 상품이 하나 이상" 이라 결국 상품을 세야 한다. 브랜드를
 * 먼저 읽고 각자의 상품 수를 뒤에 세는 방식은 <b>상품이 없는 브랜드를 걸러내지 못한다</b> —
 * 페이지를 읽은 뒤 버리면 8개를 요청해 5개가 그려지고 총 개수가 실제와 달라진다. 상품을 묶어
 * 세면 그 브랜드는 애초에 결과에 없다.
 *
 * <h2>세 곳이 같은 조건을 쓴다</h2>
 *
 * <p>목록 노출 기준 · 카드의 상품 수 · 브랜드별 상품 목록이 모두
 * {@code ON_SALE + ProductCategory.consumerVisible()} 이다. 상품 수만 {@code ETC} 를 포함해
 * 세면 <b>"상품 12개" 라고 적어 놓고 11개만 그리는 카드</b>가 된다. 그래서 감출 분류의 목록을
 * {@link ProductCategory#consumerVisible()} 한 곳에서 만들고 세 곳이 그것을 쓴다.
 *
 * <h2>이름은 화면에 보이는 것으로</h2>
 *
 * <p>회사명이 있으면 회사명, 없으면 사용자 이름이다 — 상품 목록의 브랜드명 조립, 상품의
 * 브랜드명 검색과 같은 규칙. 여기서만 다르게 보면 사용자가 목록에서 본 이름으로 검색해도
 * 걸리지 않는다.
 *
 * <p>두 조인 모두 브랜드당 최대 한 행이다({@code users.id}·{@code company_profile.user_id} 가
 * PK). 그룹의 크기를 바꾸지 않으므로 상품 수가 정확하다.
 */
@Repository
@RequiredArgsConstructor
public class PublicBrandQueryRepository {

    /** like 패턴의 이스케이프 문자. 상품 검색과 같은 규칙을 쓴다. */
    private static final char LIKE_ESCAPE = '!';

    private final JPAQueryFactory queryFactory;

    /**
     * 공개할 브랜드를 페이지로 읽는다.
     *
     * <p>{@link PageableExecutionUtils} 가 불필요한 count 질의를 생략한다 — 첫 페이지이고
     * 결과가 페이지 크기보다 작으면 총 개수를 이미 아는 셈이다.
     */
    public Page<PublicBrandRow> search(PublicBrandSearchCondition condition, Pageable pageable) {
        Predicate where = where(condition.keywordOrNull());
        // Tuple 에서 값을 꺼낼 때 쓴 것과 같은 식이어야 한다. 부를 때마다 새로 만들면
        // 꺼내는 쪽이 다른 식으로 찾게 된다.
        StringExpression brandName = brandName();
        NumberExpression<Long> productCount = productModel.count();

        List<Tuple> rows = brandQuery(queryFactory.select(
                        productModel.brandId, brandName, productCount))
                .where(where)
                .groupBy(productModel.brandId, companyProfile.companyName, user.name)
                .orderBy(orderBy(condition.sortOrDefault(), productCount))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<PublicBrandRow> content = new ArrayList<>();
        for (Tuple row : rows) {
            Long count = row.get(productCount);
            content.add(new PublicBrandRow(
                    row.get(productModel.brandId),
                    row.get(brandName),
                    count == null ? 0L : count));
        }
        return PageableExecutionUtils.getPage(content, pageable, () -> count(where));
    }

    /**
     * 브랜드 하나의 공개 상품 수. 상세 화면과 "목록에 실릴 자격" 판정이 함께 쓴다.
     *
     * <p>목록과 <b>같은 조건</b>이라 목록에 없는 브랜드는 여기서도 {@code 0} 이 나온다.
     */
    public long countConsumerVisibleProducts(Long brandId) {
        Long count = queryFactory
                .select(productModel.count())
                .from(productModel)
                .where(publicProduct().and(productModel.brandId.eq(brandId)))
                .fetchOne();
        return count == null ? 0L : count;
    }

    /**
     * 조건에 맞는 브랜드 수.
     *
     * <p><b>{@code countDistinct} 여야 한다.</b> 그냥 세면 상품 수가 나와서, 브랜드 3곳이
     * 상품 40개를 가졌을 때 "40개 브랜드" 가 된다.
     */
    private long count(Predicate where) {
        Long total = brandQuery(queryFactory.select(productModel.brandId.countDistinct()))
                .where(where)
                .fetchOne();
        return total == null ? 0L : total;
    }

    /** 목록과 count 가 공유하는 from·join. 한쪽만 고치면 이름 조건이 가리킬 별칭이 없어진다. */
    private <T> JPAQuery<T> brandQuery(JPAQuery<T> query) {
        return query
                .from(productModel)
                .leftJoin(user).on(user.id.eq(productModel.brandId))
                .leftJoin(companyProfile).on(companyProfile.userId.eq(productModel.brandId));
    }

    private Predicate where(String keyword) {
        return new BooleanBuilder().and(publicProduct()).and(nameContains(keyword));
    }

    /** 소비자에게 공개되는 상품의 조건. 상품 목록이 쓰는 것과 같다. */
    private BooleanExpression publicProduct() {
        return productModel.status.eq(ProductModelStatus.ON_SALE)
                .and(productModel.category.in(ProductCategory.consumerVisible()));
    }

    private BooleanExpression nameContains(String keyword) {
        if (keyword == null) {
            return null;
        }
        return brandName().likeIgnoreCase(likePattern(keyword), LIKE_ESCAPE);
    }

    /** 화면에 보이는 브랜드명. 회사명이 있으면 회사명, 없으면 사용자 이름. */
    private StringExpression brandName() {
        return companyProfile.companyName.coalesce(user.name);
    }

    /**
     * 검색어를 like 패턴으로 감싼다. 사용자가 친 {@code %}·{@code _} 는 글자로 읽는다 —
     * 그대로 흘리면 {@code %} 한 글자를 친 사람이 전체 목록을 받는다.
     */
    private static String likePattern(String keyword) {
        String escape = String.valueOf(LIKE_ESCAPE);
        String escaped = keyword
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
        return "%" + escaped + "%";
    }

    private OrderSpecifier<?>[] orderBy(PublicBrandSort sort, NumberExpression<Long> productCount) {
        return switch (sort) {
            case PRODUCT_COUNT -> new OrderSpecifier<?>[] {
                productCount.desc(), productModel.brandId.asc()
            };
            case NAME -> new OrderSpecifier<?>[] {
                brandName().asc(), productModel.brandId.asc()
            };
        };
    }

    /**
     * 질의가 돌려주는 한 줄. 소개글과 로고는 여기 없다 — 그것들은 브랜드당 한 행짜리 값이라
     * 그룹 질의에 끌고 들어가지 않고, 페이지에 실린 브랜드만 따로 읽는다.
     */
    public record PublicBrandRow(Long brandId, String brandName, long productCount) {}
}
