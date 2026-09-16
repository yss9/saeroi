package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.service.BrandLogoService;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 데모 브랜드의 로고를 걸어 준다.
 *
 * <p>BRANDS 화면이 로고 없이 전부 빈 카드로 나오면 목록·정렬·검색이 도는지 눈으로 확인할 수
 * 없다. 그래서 상품 대표 이미지와 같은 방식으로 임시 로고를 만들어 올린다.
 *
 * <h2>언제 올리는가</h2>
 *
 * <p><b>우리가 올린 로고가 없을 때만</b>이다. {@code company_profile.logo_url} 이
 * {@code BRAND_LOGO/} 로 시작하면 이미 파일로 올라간 로고가 있다는 뜻이라 손대지 않는다 —
 * 누군가 브랜드 계정으로 진짜 로고를 올렸다면 재기동이 그것을 덮어써서는 안 된다.
 *
 * <p>비어 있거나, 업체 정보 수정 폼에 손으로 입력된 옛 자유 문자열이면 올린다. 그 값은
 * 공개 응답에서 어차피 {@code null} 로 접히므로 화면에 로고가 없는 것과 같다.
 *
 * <p>{@code image-refresh=true} 는 상품 이미지와 같은 탈출구다 — 임시 로고 규격을 바꿨을 때
 * 한 번 밀어 준다.
 *
 * <p>상품 이미지와 달리 해시를 대조하지 않는다. 진짜 로고가 들어오는 경로가 브랜드 본인의
 * 업로드 API 하나뿐이고, 그것은 {@code BRAND_LOGO/} 키를 남기므로 접두어만 보면 충분하다.
 *
 * <p>진짜 로고가 모두 준비되면 이 클래스를 파일째 지우고 시더에서 호출 한 줄만 빼면 된다.
 */
@Component
public class DemoBrandLogoBinder {

    private static final Logger log = LoggerFactory.getLogger(DemoBrandLogoBinder.class);

    private final FileStorageService fileStorageService;
    private final DemoPlaceholderImageFactory placeholderFactory;
    private final boolean imageRefresh;

    public DemoBrandLogoBinder(
            FileStorageService fileStorageService,
            DemoPlaceholderImageFactory placeholderFactory,
            @Value("${app.demo-catalog.image-refresh:false}") boolean imageRefresh) {
        this.fileStorageService = fileStorageService;
        this.placeholderFactory = placeholderFactory;
        this.imageRefresh = imageRefresh;
    }

    /** 로고가 없으면 임시 로고를 올려 건다. 이미 파일로 올라간 로고가 있으면 그대로 둔다. */
    public void bind(CompanyProfile profile, String brandKey) {
        boolean hasUploadedLogo = BrandLogoService.isBrandLogoKey(profile.getLogoUrl());
        if (hasUploadedLogo && !imageRefresh) {
            return;
        }

        DemoProductImage logo = placeholderFactory.createBrandLogo(brandKey);
        String storageKey = fileStorageService.store(
                        new FileReference(BrandLogoService.REFERENCE_TYPE, profile.getUserId()),
                        logo.filename(),
                        logo.contentType(),
                        new ByteArrayInputStream(logo.content()),
                        logo.size())
                .storageKey();
        profile.updateLogoUrl(storageKey);
        log.info("Demo brand logo updated: key={} reason={}",
                brandKey, hasUploadedLogo ? "image-refresh" : "missing");
    }
}
