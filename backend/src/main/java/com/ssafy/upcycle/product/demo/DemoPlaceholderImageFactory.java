package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductImageType;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 진짜 상품 사진이 준비되기 전까지 걸어 둘 임시 이미지를 만든다.
 *
 * <p><b>이 클래스는 지워질 것을 전제로 한다.</b> 시연 상품 48개의 실제 사진이 모두 들어오면
 * 파일째 삭제하고 {@link DemoProductImageBinder} 의 폴백 한 줄만 빼면 된다 — 그래서 저장·
 * 교체 판단은 여기 두지 않고 전부 Binder 가 갖는다.
 *
 * <p><b>Git 에 바이너리를 넣지 않는다.</b> 임시물이라 넣었다 빼는 왕복이 생기고, 그 사이
 * 저장소에는 쓰이지 않는 파일이 남는다. 대신 기동할 때 만든다.
 *
 * <h2>텍스트를 그리지 않는다</h2>
 *
 * <p>글자를 넣으면 폰트에 의존하게 되고, 폰트가 없는 슬림 컨테이너에서는 글자가 네모로
 * 깨지거나 렌더링이 실패한다. 분류는 <b>색과 도형</b>으로만 구분한다 — 최종 분류 12개가
 * 서로 다른 색을 갖고, 상위 분류(의류·가방·그 밖)마다 도형이 다르다. SHOP 2단 메뉴 필터가
 * 실제로 걸리는지 눈으로 확인할 수 있어야 하기 때문이다.
 *
 * <h2>⚠️ 규격을 바꾸면 한 번 밀어야 한다</h2>
 *
 * <p>{@link DemoProductImageBinder} 는 "지금 걸려 있는 이미지가 임시물인가" 를
 * <b>바이트 해시</b>로 판단한다 ({@link #hashes()}). 그래서 여기 색·크기·도형을 하나라도
 * 바꾸면 <b>이미 업로드된 임시 이미지의 해시가 목록에서 빠지고, 그 이미지는 진짜 사진으로
 * 오판되어 영영 교체되지 않는다.</b>
 *
 * <p>규격을 바꿨다면 {@code app.demo-catalog.image-refresh=true} 로 한 번 기동해 전부 다시
 * 올려야 한다. 그 설정은 진짜 사진이 없을 때도 현재 규격의 임시 이미지로 밀어 준다.
 *
 * <p>같은 이유로 <b>생성 결과는 결정적이어야 한다</b> — 같은 분류면 언제나 같은 바이트가
 * 나와야 평상시 재기동에서 "바뀐 이미지" 로 오인되지 않는다. 난수·시각·글자를 넣지 않는
 * 것이 그 조건이다.
 */
@Component
public class DemoPlaceholderImageFactory {

    /** 4:5. 로드맵이 정한 상품 사진 비율이라 진짜 사진으로 바뀌어도 레이아웃이 흔들리지 않는다. */
    private static final int WIDTH = 800;
    private static final int HEIGHT = 1000;

    private static final String FORMAT = "png";
    private static final String CONTENT_TYPE = "image/png";
    private static final String FILENAME = "placeholder.png";

    /**
     * 최종 분류 12개의 바탕색.
     *
     * <p>키는 하위 분류가 있으면 그 이름, 없으면 상위 분류 이름이다 — SHOP 2단 메뉴의
     * 마지막 칸과 정확히 같은 12개가 된다.
     */
    private static final Map<String, Color> SLOT_COLORS = slotColors();

    private static Map<String, Color> slotColors() {
        Map<String, Color> colors = new LinkedHashMap<>();
        // CLOTHING — 따뜻한 계열
        colors.put("TOP", new Color(0xC9A227));
        colors.put("OUTER", new Color(0x8A5A2B));
        colors.put("PANTS", new Color(0x4F6D3A));
        colors.put("SKIRT", new Color(0xB5533C));
        colors.put("DRESS", new Color(0x7E4A6B));
        // BAG — 푸른 계열
        colors.put("TOTE", new Color(0x2F6F8F));
        colors.put("SHOULDER", new Color(0x3A5BA0));
        colors.put("CROSSBODY", new Color(0x2E8B7A));
        colors.put("BACKPACK", new Color(0x1F4257));
        // 하위 분류가 없는 상위 분류
        colors.put("WALLET", new Color(0x6B4F8A));
        colors.put("ACCESSORY", new Color(0xC7643C));
        colors.put("HOME", new Color(0x5A6B5D));
        return colors;
    }

    /**
     * 그 분류의 임시 이미지. 언제나 {@link ProductImageType#MAIN} 한 장이다.
     *
     * <p>갤러리는 채우지 않는다. 같은 그림 네 장을 상세 화면에 늘어놓아도 보여 줄 것이 없고,
     * 공개 상세는 갤러리가 비면 대표 이미지 한 장으로 물러서도록 이미 만들어져 있다.
     */
    public DemoProductImage create(ProductCategory category, ProductSubcategory subcategory) {
        return new DemoProductImage(
                ProductImageType.MAIN, FILENAME, CONTENT_TYPE, render(category, subcategory));
    }

    /**
     * 이 팩토리가 만들 수 있는 모든 이미지의 해시.
     *
     * <p>{@link DemoProductImageBinder} 가 "지금 걸려 있는 것이 임시물인가" 를 이 집합으로
     * 판단한다. 12개뿐이라 기동할 때 한 번 만들어 둔다.
     */
    public Set<String> hashes() {
        Set<String> hashes = new LinkedHashSet<>();
        for (String slot : SLOT_COLORS.keySet()) {
            hashes.add(sha256(render(slot)));
        }
        return hashes;
    }

    /** 바이트가 같은지 비교하는 유일한 기준. 저장된 파일 쪽도 같은 방식으로 계산한다. */
    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest(content)) {
                hex.append("%02x".formatted(b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 은 모든 JVM 이 반드시 제공한다.
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private byte[] render(ProductCategory category, ProductSubcategory subcategory) {
        return render(slotOf(category, subcategory));
    }

    /**
     * 분류 하나를 그린다. <b>바탕 단색 + 가운데 도형 하나</b>가 전부다.
     *
     * <p>도형이 상위 분류를 말한다 — 의류는 세로 사각, 가방은 둥근 사각, 나머지는 원.
     * 색만으로는 섬네일이 작게 줄었을 때 구분이 어렵다.
     */
    private byte[] render(String slot) {
        Color background = SLOT_COLORS.get(slot);
        if (background == null) {
            throw new IllegalArgumentException("No placeholder color for slot: " + slot);
        }

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(background);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            graphics.setColor(lighten(background));
            drawShape(graphics, slot);
        } finally {
            graphics.dispose();
        }
        return encode(image);
    }

    private void drawShape(Graphics2D graphics, String slot) {
        int width = WIDTH / 2;
        int height = HEIGHT / 2;
        int x = (WIDTH - width) / 2;
        int y = (HEIGHT - height) / 2;

        switch (shapeGroupOf(slot)) {
            case CLOTHING -> graphics.fillRect(x + width / 6, y, width * 2 / 3, height);
            case BAG -> graphics.fillRoundRect(x, y + height / 6, width, height * 2 / 3, 80, 80);
            case OTHER -> graphics.fillOval(x, y + height / 4, width, width);
        }
    }

    private ShapeGroup shapeGroupOf(String slot) {
        return switch (slot) {
            case "TOP", "OUTER", "PANTS", "SKIRT", "DRESS" -> ShapeGroup.CLOTHING;
            case "TOTE", "SHOULDER", "CROSSBODY", "BACKPACK" -> ShapeGroup.BAG;
            default -> ShapeGroup.OTHER;
        };
    }

    /** 바탕과 같은 색조에서 밝기만 올린다. 색을 새로 정하지 않아 12개 색 대조가 유지된다. */
    private Color lighten(Color base) {
        return new Color(
                Math.min(255, base.getRed() + 60),
                Math.min(255, base.getGreen() + 60),
                Math.min(255, base.getBlue() + 60));
    }

    private byte[] encode(BufferedImage image) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, FORMAT, bytes)) {
                throw new IllegalStateException("No PNG writer is available in this JVM");
            }
        } catch (IOException failure) {
            // 메모리에 쓰는 중이라 실제로는 일어나지 않는다.
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }

    /** SHOP 2단 메뉴의 마지막 칸. 하위 분류가 없는 상위 분류는 그 이름이 곧 칸이다. */
    private static String slotOf(ProductCategory category, ProductSubcategory subcategory) {
        return subcategory == null ? category.name() : subcategory.name();
    }

    private enum ShapeGroup {
        CLOTHING,
        BAG,
        OTHER
    }
}
