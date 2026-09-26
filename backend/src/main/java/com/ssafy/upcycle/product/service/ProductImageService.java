package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.StoredFile;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductImageUploadResponse;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 제품 이미지 업로드와 조회.
 *
 * <h2>왜 등록보다 먼저 올리는가</h2>
 *
 * <p>{@link FileReference} 는 양수 id 를 요구하는데 썸네일은 제품이 생기기 전에 필요하고,
 * {@code product_model.thumbnail_url} 은 {@code NOT NULL} 이라 빈 채로 만들 수도 없다.
 * 그래서 <b>브랜드에 딸린 파일로 먼저 저장</b>하고 등록 요청이 그 키를 참조한다.
 * 증빙 업로드가 쓰는 순서와 같다 — 올리고, 결과를 참조한다.
 *
 * <h2>저장 자체는 여기 없다</h2>
 *
 * <p>확장자 화이트리스트 · 미디어 타입 일치 · 해시 계산 · 경로 파생은 모두
 * {@link FileStorageService} 가 한다. 도메인마다 그걸 다시 만들면 같은 사진이 한 화면에서는
 * 통과하고 다른 화면에서는 거부된다.
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    /**
     * 저장 경로의 첫 조각. {@code evidence_file.reference_type} ENUM 에는 없는 값이다 —
     * 제품 이미지는 증빙이 아니라서 그 테이블에 행을 남기지 않는다.
     */
    private static final String REFERENCE_TYPE = "PRODUCT_IMAGE";

    /** 제품 사진 한 장 10MB. 증빙과 같은 상한이다. */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    /**
     * 공용 정책보다 좁다. {@code FileContentPolicy} 는 PDF 도 받지만, 그건 증빙에
     * 계량증명서 같은 문서가 있기 때문이다. 제품 사진은 마켓에 {@code <img>} 로 걸린다.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final FileStorageService fileStorageService;
    private final ProductModelRepository productModelRepository;
    private final ProductSellerPolicy sellerPolicy;

    /**
     * 제품 이미지를 올린다. 동시에 목록용 썸네일(400px)을 생성·저장한다.
     *
     * @param brandId JWT {@code sub} 에서 온 사용자 id. 저장 경로에 들어가므로 요청 바디로
     *     받지 않는다 — 받으면 남의 폴더에 파일을 쓸 수 있다
     */
    public ProductImageUploadResponse upload(Long brandId, MultipartFile file) {
        sellerPolicy.requireSeller(brandId);
        requireUploadableImage(file);

        FileReference reference = new FileReference(REFERENCE_TYPE, brandId);
        StoredFile originalStored = store(reference, file);

        // 썸네일 생성 및 저장
        StoredFile thumbnailStored = generateAndStoreThumbnail(reference, file);

        // 프론트엔드가 받는 키는 썸네일 키 (목록용)
        return ProductImageUploadResponse.from(thumbnailStored);
    }

    /**
     * 제품의 대표 이미지 바이트.
     *
     * <p><b>키가 아니라 제품 id 로 받는다.</b> 클라이언트가 키를 URL 에 넣게 되면 {@code ..}
     * 나 남의 키를 찔러 볼 수 있고, 그 검사는 틀리기 쉽다. 행에서 꺼내면 그런 요청이
     * 만들어지지 않는다 — {@link FileStorageService} 가 경로를 인자로 받지 않는 것과 같은
     * 이유다.
     *
     * <p>스트림은 <b>받은 쪽이 닫는다</b>. 컨트롤러가 응답으로 흘린 뒤에 닫히기 때문이다.
     */
    @Transactional(readOnly = true)
    public FileContent readThumbnail(Long productModelId) {
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        try {
            return fileStorageService.read(model.getThumbnailUrl());
        } catch (BusinessException failure) {
            // 행은 있는데 파일이 없다. 저장소 계층의 FILE_NOT_FOUND 를 그대로 흘리면
            // 프론트가 "제품이 없다" 와 구분할 수 없다.
            if (failure.getErrorCode() == FileStorageErrorCode.FILE_NOT_FOUND) {
                throw new BusinessException(ProductErrorCode.PRODUCT_THUMBNAIL_NOT_FOUND);
            }
            throw failure;
        }
    }

    /**
     * 등록 요청이 준 이미지 키가 실제로 저장된 것인지 확인한다.
     *
     * <p>{@link ProductModelRegistrationService} 가 부른다. 이 검사가 없으면 프론트가 아무
     * 문자열이나 보내도 등록이 성공하고, 이미지가 깨진 제품이 마켓에 올라간 뒤에야 드러난다.
     */
    public void requireStoredImage(String storageKey) {
        if (!fileStorageService.exists(storageKey)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_THUMBNAIL_NOT_STORED);
        }
    }

    /**
     * 목록 카드용 썸네일(폭 400px)을 만들어 별도 키로 저장한다.
     *
     * <p>{@code declared content-type} 이 jpeg/png/webp 라고 해서 바이트가 실제로 그
     * 형식이라는 보장은 없다 ({@link #requireUploadableImage} 는 헤더만 본다). Thumbnailator
     * 는 실제로 디코딩을 시도하므로, 여기서 그 사실이 드러난다 — {@code IOException} 뿐
     * 아니라 {@code UnsupportedFormatException} 같은 unchecked 예외도 던진다. 둘 다 잡지
     * 않으면 이미지가 아닌 바이트를 jpeg 라 우기는 요청이 500 으로 터진다.
     */
    private StoredFile generateAndStoreThumbnail(FileReference reference, MultipartFile file) {
        byte[] thumbnailBytes;
        try (InputStream originalStream = file.getInputStream()) {
            ByteArrayOutputStream thumbnailOutput = new ByteArrayOutputStream();
            Thumbnails.of(originalStream)
                    .width(400)
                    .outputQuality(0.75)
                    .outputFormat("jpeg")
                    .toOutputStream(thumbnailOutput);
            thumbnailBytes = thumbnailOutput.toByteArray();
        } catch (IOException | RuntimeException unreadableImage) {
            throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_TYPE_NOT_ALLOWED);
        }

        String thumbnailFileName = "thumb_" + file.getOriginalFilename();
        return fileStorageService.store(
                reference,
                thumbnailFileName,
                "image/jpeg",
                new ByteArrayInputStream(thumbnailBytes),
                thumbnailBytes.length);
    }

    private StoredFile store(FileReference reference, MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return fileStorageService.store(
                    reference,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    content,
                    file.getSize());
        } catch (IOException unreadable) {
            // 업로드 스트림이 중간에 끊겼다. 사용자가 할 수 있는 일이 저장 실패와 같다.
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
    }

    /**
     * 요청 단위 규칙. 파일 하나에 적용되는 형식 검사는 저장소가 한다.
     *
     * <p>Spring 의 {@code max-file-size} 가 먼저 걸리는 경우도 있는데 그건
     * {@code MaxUploadSizeExceededException} 이고, 읽을 수 있는 오류로 바꾸는 것은
     * {@code GlobalExceptionHandler} 의 몫이다. 두 층에서 막는 것이 중복이 아니다 —
     * 앞 층은 서버를 지키고 이 층은 안내 문구를 만든다.
     */
    private void requireUploadableImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_MISSING);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(FileStorageErrorCode.FILE_TOO_LARGE);
        }
        String declared = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_CONTENT_TYPES.contains(declared)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_TYPE_NOT_ALLOWED);
        }
    }
}
