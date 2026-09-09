package com.ssafy.upcycle.product.repository;

import static com.ssafy.upcycle.product.entity.QProductModel.productModel;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.upcycle.product.dto.ProductModelSearchCondition;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * 마켓 제품 목록. <b>QueryDSL 로 짜는 것이 팀 계약이다</b>
 * ({@code ListingSearchQueryRepository} 주석 참고).
 *
 * <h2>재고를 여기서 세지 않는다</h2>
 *
 * <p>재고는 {@code product_item} 을 세어 구하는데, 그 집계를 이 질의에 join 으로 붙이면
 * {@code group by} 가 페이지 계산과 얽힌다 — 개체가 없는 모델이 결과에서 빠지거나
 * ({@code inner join}), count 질의가 모델이 아니라 개체 수를 세게 된다.
 *
 * <p>그래서 <b>모델 페이지를 먼저 읽고, 그 페이지의 id 들로 재고를 한 번 더 센다</b>
 * ({@link ProductStockQueryRepository}). 질의는 최대 세 개다 — 목록 · count · 재고. 페이지
 * 크기와 무관한 고정 개수이므로 N+1 이 아니다.
 *
 * <p>재고로 <b>거르거나 정렬하지 않기 때문에</b> 이 분리가 가능하다. 언젠가 "재고 있는
 * 것만" 필터가 생기면 그때는 집계를 조건에 넣어야 하고, 그건 여기 구조를 바꾸는 일이다.
 */
@Repository
@RequiredArgsConstructor
public class ProductModelSearchQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 조건에 맞는 제품을 페이지로 읽는다. <b>최신순 고정이다.</b>
     *
     * <p>정렬 옵션을 열지 않은 것은 화면이 최신순 하나만 쓰기 때문이다. 가격순·인기순은
     * 요구가 생길 때 붙이는 편이 낫다 — 쓰이지 않는 정렬 키는 인덱스만 늘린다.
     *
     * <p>{@code id} 를 2차 정렬로 둔다. {@code created_at} 이 같은 초에 들어온 제품들의
     * 순서가 페이지마다 달라지면 <b>같은 제품이 1페이지와 2페이지에 동시에 보이거나 어느
     * 쪽에도 안 보인다.</b> 등록이 몰리는 순간에만 나타나서 재현하기 어렵다.
     *
     * <p>{@link PageableExecutionUtils} 가 <b>불필요한 count 질의를 생략한다</b> — 첫
     * 페이지이고 결과가 페이지 크기보다 작으면 총 개수를 이미 아는 셈이라 세지 않는다.
     */
    public Page<ProductModel> search(ProductModelSearchCondition condition, Pageable pageable) {
        Predicate where = where(condition);

        List<ProductModel> content = queryFactory
                .selectFrom(productModel)
                .where(where)
                .orderBy(productModel.createdAt.desc(), productModel.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> count(where));
    }

    /**
     * 목록과 count 가 함께 쓰는 조건.
     *
     * <p><b>{@code null} 인 조건은 붙지 않는다.</b> {@link BooleanBuilder} 는 {@code null}
     * 항을 무시하므로, 아래 메서드가 값이 없을 때 {@code null} 을 돌려주는 것이 곧 "이
     * 필터는 걸지 않는다" 는 뜻이다.
     *
     * <p>목록과 count 가 이 메서드 하나를 공유하는 것이 요점이다. 조건을 두 벌로 적으면
     * 필터를 추가할 때 한쪽만 고치게 되고, 그러면 <b>"총 3건" 이라고 표시하면서 2건만
     * 보여주는</b> 상태가 된다.
     */
    private Predicate where(ProductModelSearchCondition condition) {
        return new BooleanBuilder()
                .and(productModel.status.eq(condition.statusOrDefault()))
                .and(categoryEq(condition.category()))
                .and(brandIdEq(condition.brandId()));
    }

    private long count(Predicate where) {
        Long total = queryFactory
                .select(productModel.count())
                .from(productModel)
                .where(where)
                .fetchOne();
        return total == null ? 0L : total;
    }

    private BooleanExpression categoryEq(ProductCategory category) {
        return category == null ? null : productModel.category.eq(category);
    }

    private BooleanExpression brandIdEq(Long brandId) {
        return brandId == null ? null : productModel.brandId.eq(brandId);
    }
}
