package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.dto.ProductModelImagesReplaceRequest;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductImageType;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import com.ssafy.upcycle.product.service.ProductModelImageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 데모 상품의 대표 이미지와 갤러리를 정한다.
 *
 * <h2>왜 따로 있는가</h2>
 *
 * <p>{@link DemoProductCatalogSeeder} 는 상품·재고·생산 배치를 만든다. 이미지는 그것과
 * 수명이 다르다 — 상품은 한 번 만들어지면 그대로지만 사진은 임시물로 시작해 나중에 진짜
 * 사진으로 바뀐다. 그 교체가 상품을 지우고 다시 만드는 일이 되면 <b>id·SKU·재고·주문
 * 연결이 전부 끊기므로</b>, 이미지만 갈아 끼우는 경로를 따로 둔다.
 *
 * <h2>무엇을 올릴지</h2>
 *
 * <p>출처에 우선순위가 있다.
 *
 * <ol>
 *   <li>{@link DemoProductImageDirectory} 의 진짜 사진 — 있으면 언제나 이것이 이긴다
 *   <li>없으면 {@link DemoPlaceholderImageFactory} 의 임시 이미지 한 장
 * </ol>
 *
 * <p>임시 이미지일 때는 <b>갤러리를 비워 둔다.</b> 같은 그림 네 장을 상세 화면에 늘어놓을
 * 이유가 없고, 공개 상세는 갤러리가 비면 대표 이미지 한 장으로 물러서도록 이미 되어 있다.
 *
 * <h2>언제 바꿀지</h2>
 *
 * <table>
 *   <caption>교체 조건</caption>
 *   <tr><th>상황</th><th>동작</th></tr>
 *   <tr><td>상품이 방금 만들어졌다</td><td>우선순위대로 올린다 ({@link #upload})</td></tr>
 *   <tr><td>썸네일 키가 저장소에 없다 (파일 유실)</td><td>우선순위대로 다시 올린다</td></tr>
 *   <tr><td>진짜 사진이 있고 지금 걸린 것이 임시 이미지다</td><td>진짜 사진으로 교체</td></tr>
 *   <tr><td>진짜 사진이 있고 이미 그것이 걸려 있다</td><td>아무것도 안 한다</td></tr>
 *   <tr><td>{@code image-refresh=true}</td><td>우선순위대로 무조건 다시 올린다</td></tr>
 *   <tr><td><b>그 밖의 평상시 재기동</b></td><td><b>아무것도 안 한다</b></td></tr>
 * </table>
 *
 * <p>마지막 줄이 이 클래스의 목적이다. 기동할 때마다 다시 올리면 저장소에 쓰이지 않는
 * 파일이 쌓이고 {@code thumbnail_url} 이 매번 달라진다.
 *
 * <h2>임시 이미지인지 어떻게 아는가</h2>
 *
 * <p>지금 걸린 파일의 바이트 해시가 {@link DemoPlaceholderImageFactory#hashes()} 안에 있으면
 * 임시물이다. 임시 이미지는 분류마다 언제나 같은 바이트로 만들어지므로 해시가 고정된다.
 * 새 컬럼도 새 테이블도 필요하지 않다.
 *
 * <p>이 대조는 <b>진짜 사진이 실제로 준비된 상품에서만</b> 일어난다. 사진 경로가 지정되지
 * 않은 평상시에는 저장소를 읽지도 않는다.
 *
 * <p>임시 이미지 규격을 바꿨을 때의 탈출구가 {@code image-refresh} 다 — 진짜 사진이 없어도
 * 현재 규격의 임시 이미지로 밀어 준다 ({@link DemoPlaceholderImageFactory} 주석 참고).
 */
@Component
public class DemoProductImageBinder {

    private static final Logger log = LoggerFactory.getLogger(DemoProductImageBinder.class);

    /** 저장 경로의 첫 조각. 브랜드가 올리는 상품 이미지와 같은 자리를 쓴다. */
    private static final String REFERENCE_TYPE = "PRODUCT_IMAGE";

    private final FileStorageService fileStorageService;
    private final ProductModelImageService productModelImageService;
    private final DemoProductImageDirectory imageDirectory;
    private final DemoPlaceholderImageFactory placeholderFactory;
    private final boolean imageRefresh;

    /** 임시 이미지 12종의 해시. 기동할 때 한 번 만든다. */
    private final Set<String> placeholderHashes;

    public DemoProductImageBinder(
            FileStorageService fileStorageService,
            ProductModelImageService productModelImageService,
            DemoProductImageDirectory imageDirectory,
            DemoPlaceholderImageFactory placeholderFactory,
            @Value("${app.demo-catalog.image-refresh:false}") boolean imageRefresh) {
        this.fileStorageService = fileStorageService;
        this.productModelImageService = productModelImageService;
        this.imageDirectory = imageDirectory;
        this.placeholderFactory = placeholderFactory;
        this.imageRefresh = imageRefresh;
        this.placeholderHashes = placeholderFactory.hashes();
    }

    /**
     * 이미지를 저장소에 올리고 키만 돌려준다. <b>상품 행을 건드리지 않는다.</b>
     *
     * <p>새로 만드는 상품이 이것을 먼저 부른다 — {@code product_model.thumbnail_url} 이
     * {@code NOT NULL} 이라 등록 전에 키가 있어야 한다. 저장 경로는 브랜드에 딸리므로
     * ({@link FileReference}) 상품 id 없이도 올릴 수 있다. 브랜드가 썸네일을 먼저 올리고
     * 등록하는 순서와 같다.
     *
     * <p>갤러리는 상품 행이 생긴 뒤 {@link #applyGallery} 가 건다. 두 번에 나눈 덕분에
     * <b>같은 파일을 두 번 올리지 않는다</b>.
     */
    public DemoProductImageUpload upload(
            Long brandId,
            String demoKey,
            ProductCategory category,
            ProductSubcategory subcategory) {
        // 진짜 사진이 있으면 그것이 이긴다. 없을 때만 임시 이미지 한 장을 만든다.
        List<DemoProductImage> real = imageDirectory.find(demoKey);
        List<DemoProductImage> images = real.isEmpty()
                ? List.of(placeholderFactory.create(category, subcategory))
                : real;

        String thumbnailKey = null;
        List<ProductModelImagesReplaceRequest.Image> gallery = new ArrayList<>();
        for (DemoProductImage image : images) {
            String storageKey = store(brandId, image);
            if (image.type() == ProductImageType.MAIN) {
                thumbnailKey = storageKey;
            }
            // 임시 이미지는 갤러리에 넣지 않는다 — 같은 그림 한 장이 상세 화면에 또
            // 걸릴 뿐이다. 갤러리가 비어 있다는 것 자체가 "아직 사진이 한 장" 이라는 뜻이다.
            if (!real.isEmpty()) {
                gallery.add(new ProductModelImagesReplaceRequest.Image(storageKey, image.type()));
            }
        }
        return new DemoProductImageUpload(thumbnailKey, gallery);
    }

    /**
     * 올려 둔 이미지를 상품의 갤러리로 건다.
     *
     * <p>브랜드가 쓰는 교체 경로({@link ProductModelImageService#replace})를 그대로 쓴다 —
     * 데모 데이터만 다른 구조로 이미지를 다루면 화면이 두 경우를 구분해야 한다.
     *
     * <p>임시 이미지 상태에서는 빈 목록이라 갤러리가 비워진다. 그 상태가 곧 "아직 사진이
     * 한 장뿐" 이라는 뜻이다.
     */
    public void applyGallery(Long brandId, Long productModelId, DemoProductImageUpload upload) {
        productModelImageService.replace(
                brandId,
                productModelId,
                new ProductModelImagesReplaceRequest(upload.gallery()));
    }

    /**
     * 이미 있는 상품의 이미지를 <b>필요할 때만</b> 갈아 끼운다. 위 표가 판단의 전부다.
     *
     * <p>바뀌는 것은 {@code thumbnail_url} 한 필드와 {@code product_image} 행들뿐이다.
     * 상품 행 자체는 제자리에서 수정되므로 id·SKU·재고·생산 배치·주문 연결이 그대로 남는다.
     */
    public void refresh(ProductModel model, String demoKey, Long brandId) {
        boolean thumbnailMissing = !fileStorageService.exists(model.getThumbnailUrl());
        boolean placeholderToReplace =
                !thumbnailMissing && shouldReplacePlaceholder(model, demoKey);

        if (!imageRefresh && !thumbnailMissing && !placeholderToReplace) {
            return;
        }

        DemoProductImageUpload upload =
                upload(brandId, demoKey, model.getCategory(), model.getSubcategory());
        model.update(null, null, null, upload.thumbnailKey());
        applyGallery(brandId, model.getId(), upload);
        log.info(
                "Demo product images updated: key={} galleryImages={} reason={}",
                demoKey,
                upload.gallery().size(),
                reasonOf(thumbnailMissing, placeholderToReplace));
    }

    private String reasonOf(boolean thumbnailMissing, boolean placeholderToReplace) {
        if (thumbnailMissing) {
            return "thumbnail-missing";
        }
        if (placeholderToReplace) {
            return "placeholder-replaced";
        }
        return "image-refresh";
    }

    /**
     * 진짜 사진이 준비됐는데 아직 임시 이미지가 걸려 있는가.
     *
     * <p>사진 경로가 지정되지 않은 평상시에는 첫 줄에서 끝난다 — 저장소를 읽지 않는다.
     */
    private boolean shouldReplacePlaceholder(ProductModel model, String demoKey) {
        if (!imageDirectory.isConfigured() || imageDirectory.find(demoKey).isEmpty()) {
            return false;
        }
        return isPlaceholder(model.getThumbnailUrl());
    }

    private boolean isPlaceholder(String storageKey) {
        try {
            FileContent stored = fileStorageService.read(storageKey);
            try (InputStream content = stored.stream()) {
                return placeholderHashes.contains(
                        DemoPlaceholderImageFactory.sha256(content.readAllBytes()));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        } catch (BusinessException missing) {
            // 파일이 사라졌다면 임시물인지 따질 것이 없다 — 어차피 다시 올려야 한다.
            return true;
        }
    }

    private String store(Long brandId, DemoProductImage image) {
        return fileStorageService.store(
                        new FileReference(REFERENCE_TYPE, brandId),
                        image.filename(),
                        image.contentType(),
                        new ByteArrayInputStream(image.content()),
                        image.size())
                .storageKey();
    }
}
