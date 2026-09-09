package com.ssafy.upcycle.product.repository;

import static com.ssafy.upcycle.product.entity.QProductItem.productItem;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 여러 제품의 재고를 <b>질의 한 번</b>으로 센다. QueryDSL 로 짜는 것이 팀 계약이다
 * ({@code MaterialBalanceQueryRepository} 주석).
 *
 * <p>{@code ProductItemRepository.countByProductModelIdAndStatus} 는 모델 하나를 셀 때
 * 쓴다. 목록에서 그것을 모델마다 부르면 그게 정확히 N+1 이다 — 제품이 30개면 목록 질의
 * 하나에 카운트 질의 30개가 붙는다. {@code ProductModelRepository} 주석이 이 작업을 목록
 * 티켓으로 미뤄 둔 이유가 그것이다.
 *
 * <p>{@code (product_model_id, status)} 인덱스가 이 집계를 받친다.
 */
@Repository
@RequiredArgsConstructor
public class ProductStockQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 모델별 재고 수량.
     *
     * <p><b>개체가 하나도 없는 모델은 결과에 없다.</b> {@code IN_STOCK} 행이 없으면 group
     * 자체가 만들어지지 않기 때문이며, 부르는 쪽이 {@code 0} 으로 접어야 한다. 등록 직후
     * 모델이 정확히 그 상태다.
     *
     * <p>빈 목록을 그대로 질의하면 {@code in ()} 이 되어 DB 마다 다르게 처리된다. 부르는
     * 쪽에서 걸러도 되지만, 한 곳에서 막아 두면 호출부가 늘어도 같은 사고가 나지 않는다.
     *
     * <p><b>QueryDSL 의 {@code transform(groupBy(...))} 을 쓰지 않았다.</b> 그쪽은 Hibernate
     * 의 {@code ScrollableResults} 를 부르는데 QueryDSL 5.1 이 기대하는 시그니처가 Hibernate
     * 6.6 에 없어 {@code NoSuchMethodError} 로 터진다. 컴파일은 통과하므로 실행해 봐야
     * 드러난다. 결과를 {@code fetch} 로 받아 Java 에서 접는 편이 안전하다 —
     * group 수만큼의 행이라 목록 크기를 넘지 않는다.
     */
    public Map<Long, Long> countInStockByProductModelIds(List<Long> productModelIds) {
        if (productModelIds == null || productModelIds.isEmpty()) {
            return Map.of();
        }
        List<Tuple> rows = queryFactory
                .select(productItem.productModelId, productItem.count())
                .from(productItem)
                .where(
                        productItem.productModelId.in(productModelIds),
                        productItem.status.eq(ProductItemStatus.IN_STOCK))
                .groupBy(productItem.productModelId)
                .fetch();

        Map<Long, Long> stocks = new LinkedHashMap<>();
        for (Tuple row : rows) {
            Long modelId = row.get(productItem.productModelId);
            Long count = row.get(productItem.count());
            stocks.put(modelId, count == null ? 0L : count);
        }
        return stocks;
    }
}
