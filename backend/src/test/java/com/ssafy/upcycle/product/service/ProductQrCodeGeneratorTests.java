package com.ssafy.upcycle.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.ssafy.upcycle.product.config.ProductQrProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * QR 생성 모듈.
 *
 * <p><b>여기서 고정하는 것은 "만들어진 QR 을 실제로 읽으면 우리가 의도한 주소가 나오는가"
 * 다.</b> PNG 가 만들어졌다는 것만 확인하면, 인코딩 설정이 틀려 스캔이 안 되는 이미지를
 * 수십 장 인쇄한 뒤에야 알게 된다 — 그때는 물건에 이미 붙어 있다.
 *
 * <p>그래서 ZXing 의 디코더로 되읽는다. 서버는 평소 QR 을 읽지 않지만(읽는 것은 손님의
 * 휴대폰 카메라다), 이 왕복 검증만큼은 서버가 할 수 있는 유일한 확인이다.
 */
class ProductQrCodeGeneratorTests {

    private static final String BASE_URL = "https://upcycle.example.com/passport";
    private static final String TOKEN = "a3f97b2c1d4e5f60718293a4b5c6d7e8";

    private final ProductQrCodeGenerator generator =
            new ProductQrCodeGenerator(new ProductQrProperties(BASE_URL));

    // ── 주소 조립 ────────────────────────────────────────────

    @Test
    void theQrCarriesAPassportUrlNotABareToken() {
        // 손님은 기본 카메라로 찍는다. 토큰만 담으면 문자열이 뜨고 끝이다.
        assertThat(generator.urlFor(TOKEN)).isEqualTo(BASE_URL + "/" + TOKEN);
    }

    @Test
    void aTrailingSlashInTheBaseUrlDoesNotDoubleUp() {
        ProductQrCodeGenerator withSlash =
                new ProductQrCodeGenerator(new ProductQrProperties(BASE_URL + "/"));

        assertThat(withSlash.urlFor(TOKEN)).isEqualTo(BASE_URL + "/" + TOKEN);
    }

    @Test
    void rejectsATokenThatCouldNeverBeOpened() {
        // 이 클래스는 DB 를 거치지 않고 문자열만 받는다. 잘못된 값이 오면
        // 아무도 열 수 없는 주소가 인쇄된다.
        assertThatThrownBy(() -> generator.urlFor("not-a-token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.urlFor("A3F97B2C1D4E5F60718293A4B5C6D7E8"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.urlFor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 이미지 ──────────────────────────────────────────────

    @Test
    void producesAPng() throws Exception {
        byte[] png = generator.pngFor(TOKEN);

        // PNG 시그니처. 확장자가 아니라 내용으로 확인한다.
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        assertThat(decodeImage(png).getWidth())
                .isEqualTo(ProductQrCodeGenerator.DEFAULT_SIZE);
    }

    @Test
    void theRenderedCodeDecodesBackToTheSameUrl() throws Exception {
        // 이 테스트가 이 클래스의 존재 이유다. 인코딩 설정이 틀리면 PNG 는 만들어지지만
        // 스캔이 되지 않고, 그 사실은 인쇄된 뒤에야 드러난다.
        byte[] png = generator.pngFor(TOKEN);

        assertThat(decodeQr(png)).isEqualTo(generator.urlFor(TOKEN));
    }

    @Test
    void staysReadableAtTheSmallestAllowedSize() throws Exception {
        // 하한이 실제로 읽히는 크기인지 확인한다. 숫자만 정해 두고 검증하지 않으면
        // 언젠가 누가 그 값을 낮춘다.
        byte[] png = generator.pngFor(TOKEN, ProductQrCodeGenerator.MIN_SIZE);

        assertThat(decodeQr(png)).isEqualTo(generator.urlFor(TOKEN));
    }

    @Test
    void differentTokensProduceDifferentImages() throws Exception {
        byte[] first = generator.pngFor(TOKEN);
        byte[] second = generator.pngFor("b4e08c3d2e5f6071829304b5c6d7e8f9");

        assertThat(first).isNotEqualTo(second);
        assertThat(decodeQr(second)).endsWith("b4e08c3d2e5f6071829304b5c6d7e8f9");
    }

    @Test
    void theSameTokenAlwaysProducesTheSameImage() {
        // 같은 라벨을 다시 뽑았는데 다른 이미지가 나오면, 인쇄물과 화면을 대조할 수 없다.
        assertThat(generator.pngFor(TOKEN)).isEqualTo(generator.pngFor(TOKEN));
    }

    // ── 크기 ────────────────────────────────────────────────

    @Test
    void refusesASizeThatWouldNotSurvivePrinting() {
        assertThatThrownBy(
                () -> generator.pngFor(TOKEN, ProductQrCodeGenerator.MIN_SIZE - 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesASizeThatWouldEatMemory() {
        // 상한이 없으면 요청 하나로 수십 MB PNG 를 만들 수 있고, 그 메모리는 요청 수만큼
        // 곱해진다.
        assertThatThrownBy(
                () -> generator.pngFor(TOKEN, ProductQrCodeGenerator.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void honoursTheRequestedSize() throws Exception {
        assertThat(decodeImage(generator.pngFor(TOKEN, 256)).getWidth()).isEqualTo(256);
    }

    // ── helpers ─────────────────────────────────────────────

    private BufferedImage decodeImage(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /** 만든 QR 을 다시 읽는다. 평소 서버가 하는 일은 아니지만 왕복 확인에는 이 방법뿐이다. */
    private String decodeQr(byte[] png) throws IOException, NotFoundException {
        BufferedImage image = decodeImage(png);
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        try {
            return new QRCodeReader().decode(bitmap).getText();
        } catch (com.google.zxing.ChecksumException | com.google.zxing.FormatException broken) {
            throw new AssertionError("생성한 QR 이 읽히지 않는다", broken);
        }
    }
}
