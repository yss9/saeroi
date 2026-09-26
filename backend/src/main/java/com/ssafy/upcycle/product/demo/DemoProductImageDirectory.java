package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.product.entity.ProductImageType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 운영자가 넣어 둔 진짜 상품 사진을 읽는다.
 *
 * <p>경로는 설정으로만 지정한다 ({@code app.demo-catalog.image-dir}). <b>기본값이 없다</b> —
 * 기본 경로를 두면 그 폴더가 우연히 존재하는 환경에서 의도하지 않은 사진이 데모 상품에
 * 붙고, 로컬마다 경로가 달라 어차피 아무에게도 맞지 않는다. 지정하지 않으면 이 클래스는
 * 언제나 빈 결과를 돌려주고 데모 상품은 임시 이미지를 쓴다.
 *
 * <h2>파일 배치</h2>
 *
 * <pre>
 *   {image-dir}/{데모 키}/
 *   ├─ 01-main.webp      대표 사진 — 상품 썸네일이자 갤러리의 MAIN
 *   ├─ 02-angle.webp     다른 각도
 *   ├─ 03-detail.webp    소재·봉제선 확대
 *   └─ 04-context.webp   착용·사용·배치 장면
 * </pre>
 *
 * <p>로드맵이 정한 파일명 규약을 그대로 쓴다. 폴더 이름은 상품의 데모 키다 — 상품명은
 * 한글이라 경로로 쓰기 나쁘고, 이름이 바뀌어도 키는 그대로이기 때문이다.
 *
 * <p><b>{@code 01-main} 이 없으면 그 상품은 없는 것으로 본다.</b> 각도·상세 컷만 있고 대표
 * 사진이 없으면 목록 카드에 걸 사진이 없다. 나머지 세 장은 <b>있는 것만</b> 쓴다 — 지갑이나
 * 리빙 제품에 억지로 네 장을 요구하지 않는다.
 *
 * <p>확장자는 {@code webp}·{@code png}·{@code jpg}·{@code jpeg} 를 받는다. 저장소가 확장자와
 * 미디어 타입을 대조하므로 여기서 짝을 맞춰 넘긴다.
 */
@Component
public class DemoProductImageDirectory {

    private static final Logger log = LoggerFactory.getLogger(DemoProductImageDirectory.class);

    /** 갤러리에서의 자리와 파일명 앞머리. 순서가 곧 갤러리 순서다. */
    private static final List<SlotFile> SLOTS = List.of(
            new SlotFile(ProductImageType.MAIN, "01-main"),
            new SlotFile(ProductImageType.ANGLE, "02-angle"),
            new SlotFile(ProductImageType.DETAIL, "03-detail"),
            new SlotFile(ProductImageType.CONTEXT, "04-context"));

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "webp", "image/webp",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg");

    private final String imageDir;

    public DemoProductImageDirectory(@Value("${app.demo-catalog.image-dir:}") String imageDir) {
        this.imageDir = imageDir == null ? "" : imageDir.trim();
    }

    /** 경로가 지정되지 않았으면 이 클래스는 아무 일도 하지 않는다. */
    public boolean isConfigured() {
        return !imageDir.isEmpty();
    }

    /**
     * 그 상품의 사진들. 대표 사진이 없으면 빈 목록이다.
     *
     * <p>돌려주는 순서가 갤러리 순서이고 첫 장이 언제나 {@link ProductImageType#MAIN} 이다.
     */
    public List<DemoProductImage> find(String demoKey) {
        if (!isConfigured()) {
            return List.of();
        }
        Path productDir = Path.of(imageDir, demoKey);
        if (!Files.isDirectory(productDir)) {
            return List.of();
        }

        List<DemoProductImage> images = new ArrayList<>();
        for (SlotFile slot : SLOTS) {
            readSlot(productDir, slot).ifPresent(images::add);
        }
        if (images.isEmpty() || images.get(0).type() != ProductImageType.MAIN) {
            // 대표 사진이 없는 묶음은 쓰지 않는다. 목록 카드에 걸 사진이 없어서다.
            log.warn("Demo product images ignored, 01-main is missing: {}", productDir);
            return List.of();
        }
        return images;
    }

    private Optional<DemoProductImage> readSlot(Path productDir, SlotFile slot) {
        for (Map.Entry<String, String> supported : CONTENT_TYPES.entrySet()) {
            String filename = slot.prefix() + "." + supported.getKey();
            Path file = productDir.resolve(filename);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            return Optional.of(new DemoProductImage(
                    slot.type(), filename, supported.getValue(), read(file)));
        }
        return Optional.empty();
    }

    private byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            // 데모 준비 중 파일이 잘못 놓인 경우다. 무엇을 못 읽었는지 알려야 고칠 수 있다.
            throw new UncheckedIOException(
                    "Cannot read demo product image: " + file, unreadable);
        }
    }

    private record SlotFile(ProductImageType type, String prefix) {}
}
