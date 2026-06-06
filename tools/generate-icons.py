# favicon + PWA 아이콘 생성기 — 단일 ₩ 픽토그램 소스.
#
# 모든 아이콘(PWA PNG 180/192/512 + favicon.ico 16/32/48)을 같은 좌표에서 렌더링해
# 픽토그램을 통일한다. static/icons/favicon.svg 는 아래 좌표를 그대로 옮긴 정적 파일 —
# 좌표를 바꾸면 SVG 도 함께 갱신할 것.
#
# 사용 (프로젝트 루트에서):
#   python tools/generate-icons.py
#
# 의존성: Pillow (pip install Pillow)

from pathlib import Path

from PIL import Image, ImageDraw

STATIC = Path(__file__).resolve().parent.parent / "account-api/src/main/resources/static"

BLUE = (49, 130, 246, 255)  # #3182F6 — manifest theme_color 와 동일
WHITE = (255, 255, 255, 255)

# 512 viewBox 기준 좌표 (favicon.svg 와 1:1 동일)
W_POINTS = [(140, 150), (198, 362), (256, 170), (314, 362), (372, 150)]
BARS = [((120, 215), (392, 215)), ((120, 277), (392, 277))]
# 16px 전용 — 두 줄은 뭉개져서 한 줄로 단순화 (스트로크보다 좁은 간격은 표현 불가)
BARS_TINY = [((120, 246), (392, 246))]
STROKE = 38  # PWA 아이콘용. favicon(≤48px) 은 작게 봐도 읽히도록 더 굵게 렌더.

SUPERSAMPLE = 4  # 안티앨리어싱 — 4배로 그린 뒤 LANCZOS 축소


def render(size: int, stroke: int = STROKE, bars=BARS) -> Image.Image:
    big = size * SUPERSAMPLE
    s = big / 512
    img = Image.new("RGBA", (big, big), BLUE)
    draw = ImageDraw.Draw(img)
    width = round(stroke * s)
    radius = width / 2

    # W 폴리라인 (꺾임은 joint='curve' 로 둥글게)
    pts = [(x * s, y * s) for x, y in W_POINTS]
    draw.line(pts, fill=WHITE, width=width, joint="curve")
    caps = [pts[0], pts[-1]]

    # 가로 바
    for (ax, ay), (bx, by) in bars:
        pa, pb = (ax * s, ay * s), (bx * s, by * s)
        draw.line([pa, pb], fill=WHITE, width=width)
        caps += [pa, pb]

    # PIL 의 line 은 끝이 각지므로 원을 덧그려 round cap 처리
    for cx, cy in caps:
        draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=WHITE)

    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    icons_dir = STATIC / "icons"
    icons_dir.mkdir(parents=True, exist_ok=True)

    # PWA 아이콘 (manifest.webmanifest + apple-touch-icon)
    for size in (512, 192, 180):
        out = icons_dir / f"icon-{size}.png"
        render(size).save(out)
        print(f"wrote {out}")

    # favicon.ico — 작은 크기에서 뭉개지지 않게 굵은 스트로크로 렌더 (16px 은 한 줄 변형)
    frames = [render(48, stroke=48), render(32, stroke=48),
              render(16, stroke=52, bars=BARS_TINY)]
    ico = STATIC / "favicon.ico"
    frames[0].save(ico, format="ICO", append_images=frames[1:],
                   sizes=[(48, 48), (32, 32), (16, 16)])
    print(f"wrote {ico}")


if __name__ == "__main__":
    main()
