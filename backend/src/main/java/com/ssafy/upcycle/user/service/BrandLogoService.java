package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.StoredFile;
import com.ssafy.upcycle.user.CompanyErrorCode;
import com.ssafy.upcycle.user.dto.BrandLogoUploadResponse;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 브랜드 로고 업로드와 조회.
 *
 * <h2>왜 자유 입력으로 두지 않는가</h2>
 *
 * <p>{@code company_profile.logo_url} 은 지금까지 브랜드 쪽에서는 업체 정보 수정 폼의
 * <b>검증 없는 문자열</b>이었다({@code CompanyProfileUpdateRequest.logoUrl}). 그 값을 소비자
 * 화면의 {@code <img src>} 에 그대로 물리면 깨진 주소나 외부 링크가 우리 페이지에서 로드된다.
 *
 * <p>그래서 파일로 받아 {@code FileStorageService} 에 저장하고 <b>저장 키</b>를 넣는다 —
 * 상품 이미지·가공업체 대표 이미지와 같은 방식이다. 저장 방식이 로컬이든 S3 든 도메인은 같은
 * 값을 들고, 프론트는 우리가 완성해 준 주소만 쓴다.
 *
 * <h2>같은 컬럼에 두 종류 값이 있다</h2>
 *
 * <p>{@code logo_url} 은 가공업체의 대표 이미지도 함께 쓴다
 * ({@code ProcessorProfileImageService}, 접두어 {@code PROCESSOR_PROFILE_IMAGE/}). 여기서
 * 저장하는 키는 {@code BRAND_LOGO/} 로 시작하므로, 공개 응답은 <b>그 접두어일 때만</b> 로고
 * 주소를 내려준다({@link #isBrandLogoKey}). 예전에 손으로 입력된 자유 문자열은 이 검사에
 * 걸려 자연히 {@code null} 이 되고, 마이그레이션이나 데이터 정리가 필요 없다.
 *
 * <p>업체 정보 수정 폼의 {@code logoUrl} 필드는 <b>그대로 둔다</b> — 지우면 기존 사업자 화면이
 * 깨진다. 그쪽으로 들어온 값은 공개 화면에 나가지 않을 뿐이다.
 */
@Service
@RequiredArgsConstructor
public class BrandLogoService {

    /** 저장 경로의 첫 조각이자 "우리가 받은 로고" 임을 알아보는 표시. */
    public static final String REFERENCE_TYPE = "BRAND_LOGO";

    /** 로고 한 장 10MB. 상품 이미지·증빙과 같은 상한이다. */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    /**
     * 공용 정책보다 좁다. {@code FileContentPolicy} 는 PDF 도 받지만 로고는 화면에
     * {@code <img>} 로 걸린다.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final FileStorageService fileStorageService;

    /** 그 값이 우리가 저장한 브랜드 로고인가. 공개 응답과 공개 조회가 함께 쓴다. */
    public static boolean isBrandLogoKey(String logoUrl) {
        return logoUrl != null && logoUrl.startsWith(REFERENCE_TYPE + "/");
    }

    /**
     * 로고를 올린다. <b>{@code BRAND} 만 가능하다.</b>
     *
     * <p>역할을 토큰이 아니라 DB 에서 다시 읽는다 — 토큰은 발급 시점의 스냅샷이라 정지되거나
     * 역할이 바뀐 계정이 만료 전까지 계속 올릴 수 있다. 상품 등록이 하는 검사와 같다.
     *
     * <p>저장에는 성공했는데 행을 못 고치면 <b>저장한 파일을 지운다</b> — 아무도 가리키지
     * 않는 파일이 남는다. 가공업체 대표 이미지가 하는 방식과 같다.
     */
    @Transactional
    public BrandLogoUploadResponse upload(Long brandId, MultipartFile file) {
        requireBrand(brandId);
        CompanyProfile profile = requireProfile(brandId);
        requireUploadable(file);

        StoredFile stored = store(brandId, file);
        try {
            profile.updateLogoUrl(stored.storageKey());
            companyProfileRepository.saveAndFlush(profile);
            return new BrandLogoUploadResponse(publicLogoUrl(brandId));
        } catch (RuntimeException failure) {
            fileStorageService.delete(stored.storageKey());
            throw failure;
        }
    }

    /**
     * 소비자에게 보여 줄 로고 바이트.
     *
     * <p><b>판매 상품이 있는지는 보지 않는다.</b> 공개 상세는 목록에 없는 브랜드를 404 로
     * 막지만, 로고는 그 브랜드가 스스로 올린 그림일 뿐 상품 정보를 드러내지 않는다. 오히려
     * 여기까지 막으면 아직 상품을 올리지 않은 브랜드가 자기 화면에서 방금 올린 로고를 볼 수
     * 없게 된다.
     *
     * <p>저장 키가 아닌 값({@code PROCESSOR_PROFILE_IMAGE/} 나 옛 자유 입력)은 404 다.
     */
    @Transactional(readOnly = true)
    public FileContent readPublicLogo(Long brandId) {
        User user = userRepository.findById(brandId)
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.BRAND_LOGO_NOT_FOUND));
        if (user.getRole() != UserRole.BRAND) {
            throw new BusinessException(CompanyErrorCode.BRAND_LOGO_NOT_FOUND);
        }
        String storageKey = companyProfileRepository.findById(brandId)
                .map(CompanyProfile::getLogoUrl)
                .filter(BrandLogoService::isBrandLogoKey)
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.BRAND_LOGO_NOT_FOUND));
        try {
            return fileStorageService.read(storageKey);
        } catch (BusinessException failure) {
            // 행은 있는데 파일이 사라졌다. 저장소의 FILE_NOT_FOUND 를 그대로 흘리면 프론트가
            // "로고를 올린 적 없음" 과 구분할 수 없다.
            if (failure.getErrorCode() == FileStorageErrorCode.FILE_NOT_FOUND) {
                throw new BusinessException(CompanyErrorCode.BRAND_LOGO_NOT_FOUND);
            }
            throw failure;
        }
    }

    /** 공개 로고 주소. 저장 키가 아닐 때는 {@code null} — 화면이 빈 로고를 그린다. */
    public static String publicLogoUrlOrNull(Long brandId, String logoUrl) {
        return isBrandLogoKey(logoUrl) ? publicLogoUrl(brandId) : null;
    }

    private static String publicLogoUrl(Long brandId) {
        return "/api/v1/public/brands/" + brandId + "/logo";
    }

    private void requireBrand(Long brandId) {
        User user = userRepository.findById(brandId).orElseThrow(
                () -> new BusinessException(CompanyErrorCode.COMPANY_PROFILE_FORBIDDEN));
        if (!user.isActive() || user.getRole() != UserRole.BRAND) {
            throw new BusinessException(CompanyErrorCode.COMPANY_PROFILE_FORBIDDEN);
        }
    }

    private CompanyProfile requireProfile(Long brandId) {
        return companyProfileRepository.findById(brandId)
                .orElseThrow(() ->
                        new BusinessException(CompanyErrorCode.COMPANY_PROFILE_NOT_FOUND));
    }

    /**
     * 요청 단위 규칙. 확장자와 미디어 타입의 일치는 저장소가 본다 —
     * 도메인마다 다시 만들면 같은 그림이 한 화면에서는 통과하고 다른 화면에서는 거부된다.
     */
    private void requireUploadable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(FileStorageErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(FileStorageErrorCode.FILE_TOO_LARGE);
        }
        String declared = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_CONTENT_TYPES.contains(declared)) {
            throw new BusinessException(FileStorageErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private StoredFile store(Long brandId, MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return fileStorageService.store(
                    new FileReference(REFERENCE_TYPE, brandId),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    content,
                    file.getSize());
        } catch (IOException unreadable) {
            // 업로드 스트림이 중간에 끊겼다. 사용자가 할 수 있는 일이 저장 실패와 같다.
            throw new BusinessException(FileStorageErrorCode.FILE_STORAGE_FAILED);
        }
    }
}
