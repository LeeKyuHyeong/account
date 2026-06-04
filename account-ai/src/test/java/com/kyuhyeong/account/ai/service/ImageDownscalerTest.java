package com.kyuhyeong.account.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ImageDownscaler} 단위 테스트.
 *
 * <p>실제 이미지 인코딩/디코딩 (ImageIO + Thumbnailator) 을 통과하는 케이스 검증:
 * 큰 이미지는 1568px 로 축소, 작은 이미지·디코드 불가 바이트는 원본 그대로.
 */
class ImageDownscalerTest {

    @Test
    @DisplayName("장변 1568px 초과 이미지 — 1568px 이하 JPEG 로 축소")
    void downscalesLargeImage() throws IOException {
        byte[] large = jpegOf(4000, 3000);

        ImageDownscaler.Downscaled result = ImageDownscaler.downscale(large, "image/jpeg");

        assertThat(result.mediaType()).isEqualTo("image/jpeg");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isLessThanOrEqualTo(1568);
        // 비율 유지 (4:3)
        assertThat(decoded.getWidth()).isEqualTo(1568);
        assertThat(decoded.getHeight()).isEqualTo(1176);
    }

    @Test
    @DisplayName("작은 이미지 — 원본 바이트/타입 그대로 통과 (업스케일·재압축 없음)")
    void passesThroughSmallImage() throws IOException {
        byte[] small = jpegOf(800, 600);

        ImageDownscaler.Downscaled result = ImageDownscaler.downscale(small, "image/jpeg");

        assertThat(result.bytes()).isSameAs(small);
        assertThat(result.mediaType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("디코드 불가 바이트 (미지원 포맷 등) — 원본 그대로 통과, 예외 없음")
    void passesThroughUndecodableBytes() {
        byte[] garbage = "not-an-image".getBytes();

        ImageDownscaler.Downscaled result = ImageDownscaler.downscale(garbage, "image/webp");

        assertThat(result.bytes()).isSameAs(garbage);
        assertThat(result.mediaType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("PNG 큰 이미지 — 알파 채널이 있어도 JPEG 재압축 성공")
    void downscalesLargePngWithAlpha() throws IOException {
        BufferedImage img = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);

        ImageDownscaler.Downscaled result = ImageDownscaler.downscale(out.toByteArray(), "image/png");

        assertThat(result.mediaType()).isEqualTo("image/jpeg");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(1568);
    }

    private byte[] jpegOf(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
