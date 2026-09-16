package com.ssafy.upcycle.product.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.product.entity.ProductImage;
import com.ssafy.upcycle.product.entity.ProductImageType;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductImageRepository;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임시 이미지를 진짜 사진으로 바꾸는 경로.
 *
 * <p>고정하는 계약이 셋이다.
 *
 * <ul>
 *   <li><b>사진이 준비되면 저절로 바뀐다</b> — "이미지가 없을 때만 올린다" 규칙만 두면
 *       임시 이미지가 영영 교체되지 않는다
 *   <li><b>바뀌는 것은 이미지뿐이다</b> — 상품 id·재고·주문 연결이 그대로 남아야 한다.
 *       지우고 다시 만들면 이미 걸린 주문이 없는 상품을 가리킨다
 *   <li><b>한 번 바꾼 뒤에는 다시 올리지 않는다</b> — 기동할 때마다 올리면 저장소에
 *       아무도 가리키지 않는 파일이 쌓인다
 * </ul>
 *
 * <p>기동 시점에는 사진 디렉터리가 비어 있어 시연 상품 48개가 임시 이미지로 시작한다.
 * 테스트가 그 디렉터리에 사진을 넣고 시더를 다시 돌려 교체를 확인한다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:demo-image-replacement-tests;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.demo-catalog.enabled=true",
    "app.demo-catalog.stock-per-product=2"
})
@Transactional
class DemoProductImageReplacementTests {

    /** 시연 상품 하나를 골라 사진을 넣는다. 데모 키가 곧 폴더 이름이다. */
    private static final String DEMO_KEY = "reweave-tote";
    private static final String PRODUCT_NAME = "리위브 스퀘어 토트";

    private static final Path STORAGE_ROOT = createTempDirectory("demo-image-storage-");
    private static final Path IMAGE_DIR = createTempDirectory("demo-image-source-");

    @DynamicPropertySource
    static void demoImageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
        registry.add("app.demo-catalog.image-dir", IMAGE_DIR::toString);
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new IllegalStateException("임시 디렉터리를 만들 수 없다", e);
        }
    }

    @Autowired
    private DemoProductCatalogSeeder seeder;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    /**
     * 사진 디렉터리는 DB 와 달리 트랜잭션으로 되돌아가지 않는다. 앞 테스트가 놓아 둔
     * 파일이 남으면 다음 테스트가 "사진이 없는 상태" 를 만들 수 없다.
     */
    @BeforeEach
    void clearImageDirectory() throws IOException {
        Path productDir = IMAGE_DIR.resolve(DEMO_KEY);
        if (!Files.isDirectory(productDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(productDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void replacesPlaceholdersWithRealPhotosWhileKeepingTheProduct() throws Exception {
        ProductModel product = demoProduct();
        Long productId = product.getId();
        String placeholderThumbnail = product.getThumbnailUrl();
        long stockBefore = inStock(productId);

        assertThat(productImageRepository.findAllByProductModelIdOrderBySortOrderAsc(productId))
                .as("사진이 없는 동안에는 갤러리가 비어 있다")
                .isEmpty();

        writeRealPhotos("01-main", "02-angle", "03-detail", "04-context");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        ProductModel replaced = productModelRepository.findById(productId).orElseThrow();
        assertThat(replaced.getId()).isEqualTo(productId);
        assertThat(replaced.getThumbnailUrl()).isNotEqualTo(placeholderThumbnail);
        assertThat(inStock(productId)).isEqualTo(stockBefore);

        List<ProductImage> gallery =
                productImageRepository.findAllByProductModelIdOrderBySortOrderAsc(productId);
        assertThat(gallery).hasSize(4);
        assertThat(gallery.get(0).getImageType()).isEqualTo(ProductImageType.MAIN);
        assertThat(gallery.get(3).getImageType()).isEqualTo(ProductImageType.CONTEXT);
        assertThat(gallery.get(0).getStorageKey()).isEqualTo(replaced.getThumbnailUrl());
    }

    /** 교체가 끝난 뒤의 재기동은 아무것도 하지 않는다. */
    @Test
    void doesNotUploadAgainOnceTheRealPhotoIsInPlace() throws Exception {
        writeRealPhotos("01-main");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        ProductModel replaced = demoProduct();
        String realThumbnail = replaced.getThumbnailUrl();

        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(demoProduct().getThumbnailUrl()).isEqualTo(realThumbnail);
    }

    /** 대표 사진 없이 각도 컷만 있으면 그 묶음은 쓰지 않는다 — 목록 카드에 걸 사진이 없다. */
    @Test
    void ignoresAPhotoSetWithoutTheMainShot() throws Exception {
        String placeholderThumbnail = demoProduct().getThumbnailUrl();

        writeRealPhotos("02-angle");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(demoProduct().getThumbnailUrl()).isEqualTo(placeholderThumbnail);
    }

    private ProductModel demoProduct() {
        return productModelRepository.findAll().stream()
                .filter(model -> PRODUCT_NAME.equals(model.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시연 상품을 찾을 수 없다"));
    }

    private long inStock(Long productModelId) {
        return productItemRepository.countByProductModelIdAndStatus(
                productModelId, ProductItemStatus.IN_STOCK);
    }

    /**
     * 사진 파일을 놓는다. 내용은 아무 바이트여도 된다 — 저장 정책은 확장자와 선언된
     * 미디어 타입만 대조하고, 여기서 보려는 것은 "그 파일이 상품에 걸리는가" 다.
     */
    private void writeRealPhotos(String... slots) throws IOException {
        Path productDir = IMAGE_DIR.resolve(DEMO_KEY);
        Files.createDirectories(productDir);
        for (String slot : slots) {
            Files.write(
                    productDir.resolve(slot + ".png"),
                    ("real-photo-" + slot).getBytes(StandardCharsets.UTF_8));
        }
    }
}
