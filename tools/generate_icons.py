#!/usr/bin/env python3
"""从一张透明背景的源图生成 Android 全套启动图标。

为什么自己写解析器：这台机器上装不了 Pillow，而图标生成又必须可复现——
把「怎么切、切多大、什么底色」写成代码，比丢一堆手工导出的 PNG 靠谱。

用法：
    python tools/generate_icons.py <源图.png>

输出（全部写进 app/src/main/res）：
    mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png          48~192dp 圆角方形
    mipmap-*/ic_launcher_round.png                                   圆形版本
    mipmap-*/ic_launcher_foreground.png                              108dp 自适应前景（62% 安全区）
    mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
    ../docs/branding/icon-512.png                                    README 用的大图

依赖：仅标准库（zlib / struct）。
"""

from __future__ import annotations

import os
import struct
import sys
import zlib

# 与终端配色一致的深蓝黑，角色是浅色头发 + 深色立绘，压得住
BACKGROUND = (22, 32, 46)
# 自适应图标的安全区：内容控制在 108dp 画布的 62%，圆形遮罩不会切到角色
ADAPTIVE_CONTENT_RATIO = 0.62
# 传统图标（launcher 不一定加遮罩）内容占比稍大，并自带圆角底
LEGACY_CONTENT_RATIO = 0.80
LEGACY_CORNER_RATIO = 0.22

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}


# ---------------------------------------------------------------- PNG 读

def read_png(path: str) -> tuple[int, int, bytes]:
    """读取 8bit、非隔行的 PNG，统一转成 RGBA。"""
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("不是 PNG 文件")
    pos, idat, palette, trns = 8, b"", None, None
    width = height = depth = color_type = None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            if interlace:
                raise ValueError("不支持隔行扫描 PNG")
        elif kind == b"IDAT":
            idat += chunk
        elif kind == b"PLTE":
            palette = chunk
        elif kind == b"tRNS":
            trns = chunk
        elif kind == b"IEND":
            break
    if depth == 8:
        pass
    elif depth == 16:
        pass
    else:
        raise ValueError(f"仅支持 8/16bit，当前 {depth}")

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    raw = zlib.decompress(idat)
    stride = width * channels * (depth // 8)
    out = bytearray(height * stride)
    prev = bytearray(stride)
    ptr = 0
    for y in range(height):
        filter_type = raw[ptr]
        ptr += 1
        line = bytearray(raw[ptr:ptr + stride])
        ptr += stride
        if filter_type == 1:
            for i in range(channels * (depth // 8), stride):
                line[i] = (line[i] + line[i - channels * (depth // 8)]) & 255
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif filter_type == 3:
            step = channels * (depth // 8)
            for i in range(stride):
                left = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 255
        elif filter_type == 4:
            step = channels * (depth // 8)
            for i in range(stride):
                left = line[i - step] if i >= step else 0
                up = prev[i]
                upleft = prev[i - step] if i >= step else 0
                p = left + up - upleft
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - upleft)
                predictor = left if (pa <= pb and pa <= pc) else (up if pb <= pc else upleft)
                line[i] = (line[i] + predictor) & 255
        elif filter_type != 0:
            raise ValueError(f"未知的 PNG 行过滤器 {filter_type}")
        out[y * stride:(y + 1) * stride] = line
        prev = line

    # 统一成 RGBA，16bit 取高字节
    pixels = bytearray(width * height * 4)
    if color_type == 6 and depth == 8:
        pixels[:] = out
    else:
        for i in range(width * height):
            if color_type == 6:  # 16bit RGBA
                base = i * 8
                rgba = (out[base], out[base + 2], out[base + 4], out[base + 6])
            elif color_type == 2:  # RGB
                base = i * 3 * (depth // 8)
                rgba = (out[base], out[base + depth // 8], out[base + 2 * (depth // 8)], 255)
            elif color_type == 0:  # 灰度
                v = out[i * (depth // 8)]
                rgba = (v, v, v, 255)
            elif color_type == 4:  # 灰度 + alpha
                v = out[i * 2 * (depth // 8)]
                a = out[i * 2 * (depth // 8) + (depth // 8)]
                rgba = (v, v, v, a)
            elif color_type == 3:  # 调色板
                idx = out[i]
                r, g, b = palette[idx * 3:idx * 3 + 3]
                a = trns[idx] if trns and idx < len(trns) else 255
                rgba = (r, g, b, a)
            else:
                raise ValueError("不支持的颜色类型")
            pixels[i * 4:(i + 1) * 4] = bytes(rgba)
    return width, height, bytes(pixels)


# ---------------------------------------------------------------- PNG 写

def write_png(path: str, width: int, height: int, rgba: bytes) -> None:
    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


# ---------------------------------------------------------------- 采样

def crop_alpha_bbox(width: int, height: int, rgba: bytes, threshold: int = 12):
    left, top, right, bottom = width, height, -1, -1
    for y in range(height):
        row = y * width * 4
        for x in range(width):
            if rgba[row + x * 4 + 3] > threshold:
                if x < left:
                    left = x
                if x > right:
                    right = x
                if y < top:
                    top = y
                if y > bottom:
                    bottom = y
    if right < 0:
        raise ValueError("源图全透明")
    return left, top, right + 1, bottom + 1


def sample_bilinear(rgba: bytes, width: int, height: int, x: float, y: float):
    """双线性采样，返回（r, g, b, a），按预乘 alpha 插值避免黑边。"""
    x = min(max(x, 0.0), width - 1.001)
    y = min(max(y, 0.0), height - 1.001)
    x0, y0 = int(x), int(y)
    fx, fy = x - x0, y - y0
    acc = [0.0, 0.0, 0.0, 0.0]
    for dy, wy in ((0, 1 - fy), (1, fy)):
        for dx, wx in ((0, 1 - fx), (1, fx)):
            px = min(x0 + dx, width - 1)
            py = min(y0 + dy, height - 1)
            idx = (py * width + px) * 4
            a = rgba[idx + 3] / 255.0
            w = wx * wy
            acc[0] += rgba[idx] * a * w
            acc[1] += rgba[idx + 1] * a * w
            acc[2] += rgba[idx + 2] * a * w
            acc[3] += a * w
    if acc[3] <= 1e-6:
        return 0, 0, 0, 0
    return (
        int(round(acc[0] / acc[3])),
        int(round(acc[1] / acc[3])),
        int(round(acc[2] / acc[3])),
        int(round(acc[3] * 255)),
    )


def blend(dst, src, coverage: float):
    """把 src 以 coverage 比例叠加到 dst（都是 0~255 的 RGBA）。"""
    sa = src[3] / 255.0 * coverage
    if sa <= 0:
        return dst
    da = dst[3] / 255.0
    out_a = sa + da * (1 - sa)
    if out_a <= 0:
        return (0, 0, 0, 0)
    return (
        int(round((src[0] * sa + dst[0] * da * (1 - sa)) / out_a)),
        int(round((src[1] * sa + dst[1] * da * (1 - sa)) / out_a)),
        int(round((src[2] * sa + dst[2] * da * (1 - sa)) / out_a)),
        int(round(out_a * 255)),
    )


def rounded_corner_coverage(x: int, y: int, size: int, radius: float) -> float:
    """圆角矩形在像素 (x, y) 处的覆盖率，四角 2×2 超采样做抗锯齿。"""
    if radius <= 0:
        return 1.0
    hits = 0
    for ox in (0.25, 0.75):
        for oy in (0.25, 0.75):
            px, py = x + ox, y + oy
            cx = min(max(px, radius), size - radius)
            cy = min(max(py, radius), size - radius)
            if (px - cx) ** 2 + (py - cy) ** 2 <= radius * radius:
                hits += 1
    return hits / 4.0


def circle_coverage(x: int, y: int, size: int) -> float:
    radius = size / 2.0
    hits = 0
    for ox in (0.25, 0.75):
        for oy in (0.25, 0.75):
            dx = x + ox - radius
            dy = y + oy - radius
            if dx * dx + dy * dy <= radius * radius:
                hits += 1
    return hits / 4.0


# ---------------------------------------------------------------- 合成

def render(
    source: bytes,
    sw: int,
    sh: int,
    box: tuple[int, int, int, int],
    size: int,
    content_ratio: float,
    background,
    mask: str = "none",
    corner_ratio: float = 0.0,
    silhouette: bool = False,
    supersample: int = 2,
) -> bytes:
    """把源图裁剪区域按比例放进 size×size 画布，可选背景、遮罩、单色剪影。

    先按 supersample 倍绘制、再盒式降采样，抗锯齿只需一遍，比逐像素算覆盖率简单。
    """
    left, top, right, bottom = box
    crop_w, crop_h = right - left, bottom - top
    scale = (size * content_ratio) / max(crop_w, crop_h)
    draw_w, draw_h = crop_w * scale, crop_h * scale
    offset_x = (size - draw_w) / 2.0
    offset_y = (size - draw_h) / 2.0
    radius = size * corner_ratio

    big = size * supersample
    out = bytearray(big * big * 4)
    for y in range(big):
        for x in range(big):
            inside = True
            if mask == "circle":
                inside = circle_coverage(x, y, big) >= 0.5
            elif mask == "rounded":
                inside = rounded_corner_coverage(x, y, big, radius * supersample) >= 0.5
            if not inside:
                continue

            dst = (0, 0, 0, 0)
            if background is not None:
                dst = (background[0], background[1], background[2], 255)

            # 目标像素中心 -> 源图连续坐标（像素 x 覆盖 [x, x+1)，中心在 x+0.5）
            px = (x + 0.5) / supersample
            py = (y + 0.5) / supersample
            u = (px - offset_x) / scale + left
            v = (py - offset_y) / scale + top
            if left <= u < right and top <= v < bottom:
                r, g, b, a = sample_bilinear(source, sw, sh, u - 0.5, v - 0.5)
                if a > 0:
                    src = (255, 255, 255, a) if silhouette else (r, g, b, a)
                    dst = blend(dst, src, 1.0)

            idx = (y * big + x) * 4
            out[idx:idx + 4] = bytes(dst)

    if supersample == 1:
        return bytes(out)

    final = bytearray(size * size * 4)
    factor = supersample * supersample
    for y in range(size):
        for x in range(size):
            acc_r = acc_g = acc_b = acc_a = 0
            for dy in range(supersample):
                for dx in range(supersample):
                    idx = ((y * supersample + dy) * big + (x * supersample + dx)) * 4
                    a = out[idx + 3]
                    acc_r += out[idx] * a
                    acc_g += out[idx + 1] * a
                    acc_b += out[idx + 2] * a
                    acc_a += a
            idx = (y * size + x) * 4
            if acc_a == 0:
                final[idx:idx + 4] = b"\x00\x00\x00\x00"
            else:
                final[idx] = acc_r // acc_a
                final[idx + 1] = acc_g // acc_a
                final[idx + 2] = acc_b // acc_a
                final[idx + 3] = acc_a // factor
    return bytes(final)


# ---------------------------------------------------------------- 主流程

def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    source_path = argv[1]
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.join(here, "..", "app", "src", "main", "res")

    width, height, rgba = read_png(source_path)
    box = crop_alpha_bbox(width, height, rgba)
    print(f"source {width}x{height}, content box {box}")

    for density, factor in DENSITIES.items():
        folder = os.path.join(res, f"mipmap-{density}")

        legacy = int(round(48 * factor))
        write_png(
            os.path.join(folder, "ic_launcher.png"),
            legacy,
            legacy,
            render(
                rgba, width, height, box, legacy, LEGACY_CONTENT_RATIO,
                BACKGROUND, mask="rounded", corner_ratio=LEGACY_CORNER_RATIO,
            ),
        )
        write_png(
            os.path.join(folder, "ic_launcher_round.png"),
            legacy,
            legacy,
            render(
                rgba, width, height, box, legacy, LEGACY_CONTENT_RATIO * 1.05,
                BACKGROUND, mask="circle",
            ),
        )

        adaptive = int(round(108 * factor))
        write_png(
            os.path.join(folder, "ic_launcher_foreground.png"),
            adaptive,
            adaptive,
            render(rgba, width, height, box, adaptive, ADAPTIVE_CONTENT_RATIO, None),
        )
        print(f"  {density}: legacy {legacy}px, adaptive {adaptive}px")

    xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    anydpi = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, name), "w", encoding="utf-8", newline="\n") as handle:
            handle.write(xml)

    preview = os.path.join(here, "..", "docs", "branding", "icon-512.png")
    write_png(
        preview,
        512,
        512,
        render(rgba, width, height, box, 512, 0.74, BACKGROUND, mask="rounded", corner_ratio=0.22),
    )
    print(f"preview -> {os.path.normpath(preview)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
