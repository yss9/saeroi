package com.ssafy.upcycle.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductQrTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * FK 무결성은 여기서 검증되지 않는다. 참조를 연관관계가 아닌 값으로 매핑했으므로
 * Hibernate 가 테스트 스키마에 FK 제약을 만들지 않는다. 실제 MySQL 에는 FK 가 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ProductRepositoryTests {

    private static final long BRAND_ID = 10L;
    private static final long BATCH_ID = 1L;

    private final ProductQrTokenGenerator qrTokenGenerator = new ProductQrTokenGenerator();

    @Autowired
    private ProductModelRepository modelRepository;

    @Autowired
    private ProductItemRepository itemRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void modelIsPersistedWithCreatedAt() {
        ProductModel saved = modelRepository.save(model());
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ProductModelStatus.DRAFT);
    }

    @Test
    void brandListsOwnModelsByStatus() {
        modelRepository.save(model()).putOnSale();
        modelRepository.save(model());
        entityManager.flush();
        entityManager.clear();

        assertThat(modelRepository.findAllByBrandIdAndStatus(BRAND_ID, ProductModelStatus.ON_SALE))
                .hasSize(1);
        assertThat(modelRepository.findAllByBrandId(BRAND_ID)).hasSize(2);
    }

    /** QR 이력 조회의 진입점. 토큰으로만 찾을 수 있어야 순번 조작이 막힌다. */
    @Test
    void itemIsFoundByQrToken() {
        Long modelId = modelRepository.save(model()).getId();
        String token = qrTokenGenerator.generate();
        itemRepository.save(item(modelId, 1, "RE:BAG #001", token));
        entityManager.flush();
        entityManager.clear();

        assertThat(itemRepository.findByQrToken(token))
                .get()
                .extracting(ProductItem::getItemCode)
                .isEqualTo("RE:BAG #001");
        assertThat(itemRepository.findByQrToken(qrTokenGenerator.generate())).isEmpty();
    }

    /** 재고는 컬럼이 아니라 개체를 세어 구한다. 이 조회가 그 유일한 답이다. */
    @Test
    void stockIsCountedFromItems() {
        Long modelId = modelRepository.save(model()).getId();
        itemRepository.save(item(modelId, 1, "RE:BAG #001", qrTokenGenerator.generate()));
        itemRepository.save(item(modelId, 2, "RE:BAG #002", qrTokenGenerator.generate()))
                .reserve();
        itemRepository.save(item(modelId, 3, "RE:BAG #003", qrTokenGenerator.generate()));
        entityManager.flush();
        entityManager.clear();

        assertThat(itemRepository.countByProductModelIdAndStatus(
                modelId, ProductItemStatus.IN_STOCK)).isEqualTo(2);
        assertThat(itemRepository.countByProductModelIdAndStatus(
                modelId, ProductItemStatus.RESERVED)).isEqualTo(1);
    }

    @Test
    void itemsAreListedByProductionBatch() {
        Long modelId = modelRepository.save(model()).getId();
        itemRepository.save(item(modelId, 1, "RE:BAG #001", qrTokenGenerator.generate()));
        itemRepository.save(item(modelId, 2, "RE:BAG #002", qrTokenGenerator.generate()));
        entityManager.flush();
        entityManager.clear();

        assertThat(itemRepository.findAllByProductionBatchId(BATCH_ID)).hasSize(2);
    }

    @Test
    void statusChangesArePersisted() {
        Long modelId = modelRepository.save(model()).getId();
        ProductItem saved =
                itemRepository.save(item(modelId, 1, "RE:BAG #001", qrTokenGenerator.generate()));
        saved.reserve();
        saved.markSold();
        entityManager.flush();
        entityManager.clear();

        assertThat(itemRepository.findById(saved.getId()))
                .get()
                .extracting(ProductItem::getStatus)
                .isEqualTo(ProductItemStatus.SOLD);
    }

    private ProductModel model() {
        return ProductModel.register(
                "RE:BAG", ProductCategory.BAG, BRAND_ID, 89_000L, "폐데님 가방", "https://img");
    }

    private ProductItem item(Long modelId, int serial, String itemCode, String qrToken) {
        return ProductItem.produce(BATCH_ID, modelId, serial, itemCode, qrToken);
    }
}
