package com.kyuhyeong.account.ai.service;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * Claude 전송 전 영수증 이미지 다운스케일.
 *
 * <p>폰 카메라 원본(3~8MB, 4000px+)을 그대로 보내면:
 * <ul>
 *   <li>Anthropic API 이미지 한도(base64 후 5MB) 초과 시 400 거부 — base64 는 원본의
 *       약 1.37배라 raw 약 3.6MB 가 실질 상한</li>
 *   <li>장변 1568px 초과분은 Claude 가 어차피 축소 — 토큰 비용·지연만 증가</li>
 * </ul>
 *
 * <p>그래서 장변 1568px 초과 또는 3MB 초과 이미지를 JPEG 로 재압축한다. EXIF 회전은
 * Thumbnailator 가 자동 보정 (세로로 찍은 영수증이 눕는 문제 방지). 디코드 불가 포맷
 * (WebP 등 ImageIO 미지원)이나 처리 실패 시엔 원본을 그대로 반환 — 다운스케일은
 * 최적화이지 필수 단계가 아니므로 실패가 분석 자체를 막아선 안 된다.
 *
 * <p>디스크에 보존되는 원본({@code ReceiptStorage})은 영향받지 않는다 — 본 클래스는
 * Claude 전송용 사본만 만든다.
 */
final class ImageDownscaler {

    private static final Logger log = LoggerFactory.getLogger(ImageDownscaler.class);

    /** Claude Vision 권장 최대 장변 — 이보다 크면 서버측에서 어차피 축소된다. */
    private static final int MAX_DIMENSION = 1568;

    /** raw 기준 재압축 임계 바이트 — base64 1.37배 후에도 API 한도(5MB) 안쪽이도록 여유. */
    private static final int MAX_BYTES = 3 * 1024 * 1024;

    private static final float JPEG_QUALITY = 0.85f;

    private ImageDownscaler() {
    }

    /** 다운스케일 결과 — 재압축 시 mediaType 은 image/jpeg 로 바뀐다. */
    record Downscaled(byte[] bytes, String mediaType) {}

    /**
     * 필요 시에만 다운스케일한다. 장변 ≤ 1568px 이고 3MB 이하면 원본 그대로,
     * 디코드 불가·처리 실패 시에도 원본 그대로 반환한다.
     */
    static Downscaled downscale(byte[] bytes, String mediaType) {
        try {
            int[] dimensions = readDimensions(bytes);
            if (dimensions == null) {
                // ImageIO 미지원 포맷 (예: WebP) — 원본 그대로 전송
                return new Downscaled(bytes, mediaType);
            }
            int longestSide = Math.max(dimensions[0], dimensions[1]);
            if (longestSide <= MAX_DIMENSION && bytes.length <= MAX_BYTES) {
                return new Downscaled(bytes, mediaType);
            }

            // 작은 이미지를 업스케일하지 않도록 target 은 원본 장변과 1568 중 작은 쪽
            int target = Math.min(MAX_DIMENSION, longestSide);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(bytes))
                    .size(target, target)
                    // PNG 알파 채널이 JPEG 인코딩을 깨지 않도록 RGB 로 평탄화
                    .imageType(BufferedImage.TYPE_INT_RGB)
                    .outputFormat("jpg")
                    .outputQuality(JPEG_QUALITY)
                    .toOutputStream(out);
            byte[] resized = out.toByteArray();

            log.info("Receipt image downscaled: {}x{} {}KB ({}) -> max {}px {}KB (image/jpeg)",
                    dimensions[0], dimensions[1], bytes.length / 1024, mediaType,
                    target, resized.length / 1024);
            return new Downscaled(resized, "image/jpeg");
        } catch (Exception e) {
            log.warn("Image downscale failed — sending original ({} bytes, {})",
                    bytes.length, mediaType, e);
            return new Downscaled(bytes, mediaType);
        }
    }

    /** 전체 디코드 없이 헤더에서 가로/세로를 읽는다. 리더가 없으면 null. */
    private static int[] readDimensions(byte[] bytes) throws Exception {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }
}
