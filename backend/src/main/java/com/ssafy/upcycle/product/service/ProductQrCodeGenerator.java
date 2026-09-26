package com.ssafy.upcycle.product.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ssafy.upcycle.product.config.ProductQrProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 제품 개체의 QR 이미지를 만든다.
 *
 * <h2>QR 에 담는 것은 토큰이 아니라 주소다</h2>
 *
 * <p>손님은 휴대폰 <b>기본 카메라</b>로 찍는다. 토큰만 담으면 알 수 없는 문자열이 뜨고
 * 끝이다. 브라우저가 바로 열리는 주소여야 Passport 화면에 닿는다.
 *
 * <p>{@link ProductQrTokenGenerator}(토큰 발급) 옆에 두었다. 토큰을 만드는 쪽과 그것을
 * 주소로 바꿔 그리는 쪽이 떨어져 있으면 형식이 갈라진다 — 한쪽이 접두어를 바꾸면 다른 쪽은
 * 모른다.
 *
 * <h2>왜 서버가 그리는가</h2>
 *
 * <p>브랜드는 배치 하나에 개체 수십 개의 라벨을 <b>한 번에 인쇄</b>한다. 브라우저에서
 * 50장을 그려 묶는 것보다 서버가 만드는 편이 낫고, 인쇄물은 한 번 나가면 되돌릴 수 없으므로
 * <b>모든 라벨이 같은 방식으로 만들어졌다는 보장</b>이 중요하다.
 */
@Component
@RequiredArgsConstructor
public class ProductQrCodeGenerator {

    /** {@code product_item.qr_token} 과 같은 규칙. 엔티티가 저장할 때 쓰는 검사다. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    /** 기본 한 변 픽셀. 라벨로 인쇄해도 읽히는 크기다. */
    public static final int DEFAULT_SIZE = 512;

    /**
     * 하한. 이보다 작으면 인쇄 후 스캔이 되지 않는다 — 화면에서는 읽혀도 종이에서는
     * 모듈 하나가 잉크 번짐에 묻힌다.
     */
    public static final int MIN_SIZE = 128;

    /**
     * 상한. 없으면 요청 하나로 수십 MB PNG 를 만들 수 있고, 그 메모리는 요청 수만큼
     * 곱해진다.
     */
    public static final int MAX_SIZE = 2048;

    private static final String IMAGE_FORMAT = "PNG";

    /**
     * 인코딩 힌트.
     *
     * <ul>
     *   <li>{@code CHARACTER_SET} — URL 에 ASCII 만 들어가지만 명시하지 않으면 ZXing 이
     *       플랫폼 기본 인코딩을 쓴다. 서버 로캘에 따라 결과가 달라지면 안 된다</li>
     *   <li>{@code ERROR_CORRECTION M} — 약 15% 손상까지 복구한다. 라벨은 긁히고 접힌다.
     *       H(30%) 는 같은 크기에서 모듈이 촘촘해져 오히려 인쇄물에서 불리하다</li>
     *   <li>{@code MARGIN 4} — QR 규격이 요구하는 조용한 영역(quiet zone)이다. 줄이면
     *       스캐너가 코드의 경계를 찾지 못한다</li>
     * </ul>
     */
    private static final Map<EncodeHintType, Object> HINTS = Map.of(
            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN, 4);

    private final ProductQrProperties properties;

    /**
     * QR 이 담을 주소.
     *
     * <p>이 문자열이 곧 인쇄물의 내용이다. 나중에 바꾸면 이미 나간 QR 은 죽으므로,
     * 조립 방식을 한 곳에만 둔다.
     */
    public String urlFor(String qrToken) {
        return properties.normalizedBaseUrl() + "/" + requireValidToken(qrToken);
    }

    /** 기본 크기 PNG. */
    public byte[] pngFor(String qrToken) {
        return pngFor(qrToken, DEFAULT_SIZE);
    }

    /**
     * PNG 바이트.
     *
     * @param size 한 변 픽셀. {@link #MIN_SIZE}~{@link #MAX_SIZE}
     */
    public byte[] pngFor(String qrToken, int size) {
        String url = urlFor(qrToken);
        requireValidSize(size);
        try {
            BitMatrix matrix =
                    new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, HINTS);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, IMAGE_FORMAT, png);
            return png.toByteArray();
        } catch (WriterException | IOException failure) {
            // 입력은 우리가 만든 짧은 ASCII URL 이고 크기도 검증을 통과했다. 여기 닿으면
            // 라이브러리나 실행 환경의 문제이지 사용자가 할 수 있는 일이 없다.
            throw new IllegalStateException("Failed to render QR code for " + url, failure);
        }
    }

    /**
     * 토큰 형식 검사.
     *
     * <p>엔티티가 이미 같은 규칙으로 저장하지만 여기서 다시 본다 — 이 클래스는 DB 를 거치지
     * 않고 문자열만 받으므로, 잘못된 값이 오면 <b>아무도 열 수 없는 주소가 인쇄된다.</b>
     */
    private String requireValidToken(String qrToken) {
        if (qrToken == null || !TOKEN_PATTERN.matcher(qrToken).matches()) {
            throw new IllegalArgumentException("Invalid QR token: " + qrToken);
        }
        return qrToken;
    }

    private void requireValidSize(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "QR size must be between " + MIN_SIZE + " and " + MAX_SIZE + " but was " + size);
        }
    }
}
