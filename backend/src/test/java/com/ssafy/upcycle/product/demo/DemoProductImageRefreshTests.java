package com.ssafy.upcycle.product.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code image-refresh=true} 일 때의 강제 재업로드.
 *
 * <p>이 설정이 없으면 <b>임시 이미지 규격을 바꿨을 때 빠져나올 길이 없다.</b> 교체 판단은
 * "지금 걸린 파일의 해시가 임시 이미지 목록에 있는가" 인데, 규격을 바꾸면 이미 올라간
 * 임시 이미지의 해시가 그 목록에서 빠져 <b>진짜 사진으로 오판되고 영영 교체되지 않는다</b>
 * ({@link DemoPlaceholderImageFactory} 주석).
 *
 * <p>그래서 이 설정은 진짜 사진이 없을 때도 동작해야 한다 — 우선순위(진짜 사진 &gt; 임시
 * 이미지)를 타되 무조건 다시 올린다. 여기서는 사진 디렉터리를 지정하지 않아 임시 이미지
 * 쪽만 확인한다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:demo-image-refresh-tests;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.demo-catalog.enabled=true",
    "app.demo-catalog.stock-per-product=1",
    "app.demo-catalog.image-refresh=true"
})
@Transactional
class DemoProductImageRefreshTests {

    private static final String PRODUCT_NAME = "리위브 스퀘어 토트";

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("demo-image-refresh-");
        } catch (IOException e) {
            throw new IllegalStateException("임시 저장 루트를 만들 수 없다", e);
        }
    }

    @Autowired
    private DemoProductCatalogSeeder seeder;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 진짜 사진이 없어도 현재 규격의 임시 이미지로 다시 올린다. 이것이 규격 변경의
     * 탈출구다 — 사진이 있을 때만 동작하면 임시 이미지끼리는 영영 갈아 끼울 수 없다.
     */
    @Test
    void reuploadsPlaceholdersEvenWithoutRealPhotos() {
        ProductModel product = demoProduct();
        String before = product.getThumbnailUrl();

        seeder.run(new DefaultApplicationArguments(new String[0]));

        ProductModel refreshed = demoProduct();
        assertThat(refreshed.getId()).isEqualTo(product.getId());
        assertThat(refreshed.getThumbnailUrl()).isNotEqualTo(before);
        assertThat(fileStorageService.exists(refreshed.getThumbnailUrl())).isTrue();
    }

    private ProductModel demoProduct() {
        return productModelRepository.findAll().stream()
                .filter(model -> PRODUCT_NAME.equals(model.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시연 상품을 찾을 수 없다"));
    }
}
