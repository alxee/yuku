#!/usr/bin/env python3
"""
Bakes res/drawable-nodpi/grain_tile.png — the grain the pre-31 splash window
is tiled with (see drawable/splash_window.xml).

Same noise the app's own surfaces use at runtime (ui/Grain.kt): one balanced
black/white sample per pixel at a CONSTANT amplitude, so the field has no
clumping of its own. The wash's strength is baked into the alpha channel here,
since a <bitmap> in a layer-list has no alpha of its own to set — which is
also why this sits between the two runtime strengths: one tile has to serve
both the light and the dark splash background.

No third-party deps on purpose (no Pillow): the PNG is written by hand, the
same way the other tools/ renderers are one-file and self-contained.

    python3 tools/render_grain_tile.py
"""

import binascii
import os
import random
import struct
import zlib

TILE = 128
ALPHA = 10        # out of 255, i.e. ±10 levels on the surface underneath
SEED = 0x5EEDCA75  # the seed ui/Grain.kt uses, so the two fields are siblings

OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "res", "drawable-nodpi", "grain_tile.png",
)


def chunk(tag: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + tag
        + data
        + struct.pack(">I", binascii.crc32(tag + data) & 0xFFFFFFFF)
    )


def main() -> None:
    rnd = random.Random(SEED)
    rows = bytearray()
    for _ in range(TILE):
        rows.append(0)  # filter type 0 (None) — the data is noise, so no
                        # predictor helps it and every row stays independent.
        for _ in range(TILE):
            v = 255 if rnd.getrandbits(1) else 0
            rows += bytes((v, v, v, ALPHA))

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", TILE, TILE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(rows), 9))
        + chunk(b"IEND", b"")
    )
    with open(os.path.normpath(OUT), "wb") as f:
        f.write(png)
    print(f"wrote {os.path.normpath(OUT)} ({len(png)} bytes)")


if __name__ == "__main__":
    main()
