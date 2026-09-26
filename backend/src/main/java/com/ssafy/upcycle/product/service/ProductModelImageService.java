package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductImageResponse;
import com.ssafy.upcycle.product.dto.ProductModelImagesReplaceRequest;
import com.ssafy.upcycle.product.entity.ProductImage;
import com.ssafy.upcycle.product.entity.ProductImageType;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductImageRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 상세 갤러리.
 *
 * <p><b>파일을 여기서 받지 않는다.</b> 업로드는 {@link ProductImageService} 가 이미 맡고
 * 있고 ({@code POST /api/v1/product-images}), 갤러리는 그것이 돌려준 저장 키를 참조할
 * 뿐이다 — 등록 요청이 {@code thumbnailUrl} 을 다루는 방식과 같다. 업로드와 참조를 한
 * 요청으로 묶으면 사진 네 장짜리 한 번의 저장이 하나라도 실패할 때 무엇이 남았는지
 * 알 수 없게 된다.
 *
 * <p><b>교체만 있고 부분 수정이 없다.</b> 이유는
 * {@link ProductModelImagesReplaceRequest} 주석에 있다.
 */
@Service
@RequiredArgsConstructor
public class ProductModelImageService {

    /**
     * 상품 하나가 가질 수 있는 사진 수.
     *
     * <p>기본 구성은 대표·각도·상세·사용 장면 네 장이다. 상한을 그 수에 딱 맞추지 않은
     * 것은 각도나 상세 컷이 둘 이상 필요한 제품이 있기 때문이고, 그렇다고 열어 두면 공개
     * 상세 응답이 요청 하나로 얼마든지 커진다.
     */
    public static final int MAX_IMAGES_PER_MODEL = 10;

    private final ProductImageRepository productImageRepository;
    private final ProductModelRepository productModelRepository;
    private final FileStorageService fileStorageService;

    /**
     * 갤러리를 통째로 바꾼다. 본인 제품만 가능하다.
     *
     * <p>저장 키가 실제로 올라온 것인지 확인한다. 이 검사가 없으면 아무 문자열이나 통과하고,
     * 사진이 깨진 상세 화면으로 드러날 때는 이미 상품이 공개된 뒤다 — 등록이
     * {@code thumbnailUrl} 을 확인하는 것과 같은 이유다.
     *
     * <p><b>단종된 제품은 건드릴 수 없다.</b> 더 팔지 않기로 한 제품의 사진을 바꾸면, 그
     * 제품을 산 사람이 나중에 보는 이력이 실제로 산 것과 달라진다. 제품 정보 수정과 같은
     * 판단이다.
     *
     * <p>기존 행을 지우고 다시 넣는다. 살려 둘 행을 골라 순서만 고치는 방식은 유니크 키
     * {@code (product_model_id, sort_order)} 와 부딪친다 — 두 사진의 자리를 맞바꾸는 흔한
     * 요청이 중간 상태에서 같은 자리를 잠깐 공유하게 되고, 그 순간 요청이 실패한다.
     */
    @Transactional
    public List<ProductImageResponse> replace(
            Long brandId, Long productModelId, ProductModelImagesReplaceRequest request) {
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        requireOwner(model, brandId);
        if (model.getStatus() == ProductModelStatus.DISCONTINUED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_MODEL_DISCONTINUED);
        }
        requireValidGallery(request.images());

        // 새 행을 넣기 전에 옛 행을 실제로 지운다. 같은 트랜잭션 안에서 delete 가 insert
        // 뒤로 밀리면 유니크 키에 걸린다 — JPA 는 쓰기 순서를 스스로 정한다.
        productImageRepository.deleteAllByProductModelId(productModelId);
        productImageRepository.flush();

        List<ProductImage> saved = new ArrayList<>();
        List<ProductModelImagesReplaceRequest.Image> images = request.images();
        for (int order = 0; order < images.size(); order++) {
            ProductModelImagesReplaceRequest.Image image = images.get(order);
            saved.add(productImageRepository.save(ProductImage.of(
                    productModelId, image.storageKey(), image.type(), order)));
        }
        return saved.stream().map(ProductImageResponse::managementOf).toList();
    }

    /** 브랜드 관리 화면이 보는 갤러리. 판매 상태와 무관하게 자기 제품이면 보인다. */
    @Transactional(readOnly = true)
    public List<ProductImageResponse> findManagementGallery(Long brandId, Long productModelId) {
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        requireOwner(model, brandId);
        return productImageRepository
                .findAllByProductModelIdOrderBySortOrderAsc(productModelId)
                .stream()
                .map(ProductImageResponse::managementOf)
                .toList();
    }

    /**
     * 여러 제품의 갤러리를 한 번에 읽어 제품별로 묶는다. 목록 응답이 쓴다.
     *
     * <p>제품마다 부르면 그게 N+1 이다. 재고를 한 번에 세는 것과 같은 방식으로 묶는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ProductImage>> findGalleriesOf(List<Long> productModelIds) {
        if (productModelIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ProductImage>> galleries = new LinkedHashMap<>();
        List<ProductImage> images = productImageRepository
                .findAllByProductModelIdInOrderByProductModelIdAscSortOrderAsc(productModelIds);
        for (ProductImage image : images) {
            galleries.computeIfAbsent(image.getProductModelId(), id -> new ArrayList<>())
                    .add(image);
        }
        return galleries;
    }

    /**
     * 갤러리 사진 한 장의 바이트.
     *
     * <p><b>제품 id 와 함께 받는다.</b> 이미지 id 만으로 찾으면 다른 제품의 사진을 남의
     * 제품 주소로 열 수 있고, 그 주소가 그대로 공유된다. 두 값이 맞지 않으면 404 다 —
     * 403 은 그 이미지가 존재한다는 사실을 알려주는 답이다.
     *
     * <p>스트림은 받은 쪽이 닫는다. 컨트롤러가 응답으로 흘린 뒤에 닫히기 때문이다.
     */
    @Transactional(readOnly = true)
    public FileContent readImage(Long productModelId, Long productImageId) {
        ProductImage image = productImageRepository.findById(productImageId)
                .filter(found -> found.belongsTo(productModelId))
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_IMAGE_NOT_FOUND));
        try {
            return fileStorageService.read(image.getStorageKey());
        } catch (BusinessException failure) {
            // 행은 있는데 파일이 없다. 저장소의 FILE_NOT_FOUND 를 그대로 흘리면 프론트가
            // "그런 사진이 없다" 와 구분할 수 없다 — 썸네일이 같은 구분을 한다.
            if (failure.getErrorCode() == FileStorageErrorCode.FILE_NOT_FOUND) {
                throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_FILE_NOT_FOUND);
            }
            throw failure;
        }
    }

    /**
     * 갤러리 전체가 지켜야 하는 규칙. 사진 한 장씩으로는 볼 수 없는 것들이다.
     *
     * <p>대표 사진을 <b>강제하지는 않는다.</b> 상세 화면은 갤러리가 비어 있을 때 이미
     * {@code thumbnailUrl} 로 물러설 줄 알아야 하므로, 대표가 없는 갤러리도 같은 규칙으로
     * 다루면 된다 — 첫 자리가 대표 자리다. 다만 <b>둘은 안 된다</b>: 어느 쪽이 대표인지
     * 서버가 답할 수 없는 상태가 되고, 화면마다 다른 사진을 고르게 된다.
     */
    private void requireValidGallery(List<ProductModelImagesReplaceRequest.Image> images) {
        if (images.size() > MAX_IMAGES_PER_MODEL) {
            throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_TOO_MANY);
        }

        Set<String> seenKeys = new HashSet<>();
        int mainCount = 0;
        for (ProductModelImagesReplaceRequest.Image image : images) {
            if (!seenKeys.add(image.storageKey())) {
                throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_DUPLICATED);
            }
            if (image.type() == ProductImageType.MAIN) {
                mainCount++;
            }
        }
        if (mainCount > 1) {
            throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_MAIN_NOT_UNIQUE);
        }

        // 저장소 확인은 마지막이다. 목록 자체가 성립하지 않는 요청(장수 초과·중복·대표
        // 둘)에 "업로드되지 않은 이미지" 라고 답하면 브랜드가 엉뚱한 곳을 고치게 된다.
        for (String storageKey : seenKeys) {
            if (!fileStorageService.exists(storageKey)) {
                throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_NOT_STORED);
            }
        }
    }

    /**
     * 남의 제품 갤러리를 건드릴 수 없다. 역할만 보면 브랜드 아무나 다른 브랜드의 상세
     * 화면 사진을 바꿀 수 있게 된다 — 제품 수정이 하는 검사와 같다.
     */
    private void requireOwner(ProductModel model, Long brandId) {
        if (!model.getBrandId().equals(brandId)) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_MODEL_OWNER);
        }
    }
}
