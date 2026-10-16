import os
import sys

import brotli
from PIL import Image

TEXT = (".js", ".html", ".css", ".json")


def keep(src, dst, data):
    if len(data) >= os.path.getsize(src):
        if os.path.exists(dst):
            os.remove(dst)
        return 0
    with open(dst, "wb") as out:
        out.write(data)
    stamp = os.path.getmtime(src) + 1
    os.utime(dst, (stamp, stamp))
    return len(data)


def webp(path):
    with Image.open(path) as im:
        im.load()
        from io import BytesIO
        buf = BytesIO()
        im.save(buf, "WEBP", lossless=True, quality=100, method=6, exact=True)
        return buf.getvalue()


def main(root):
    saved = 0
    for folder, _, files in os.walk(root):
        for name in files:
            path = os.path.join(folder, name)
            if name.endswith(TEXT):
                with open(path, "rb") as src:
                    raw = src.read()
                packed = keep(path, path + ".br", brotli.compress(raw, quality=11))
            elif name.endswith(".png"):
                packed = keep(path, path + ".webp", webp(path))
            else:
                continue
            if packed:
                saved += os.path.getsize(path) - packed
                print("%s: %d -> %d" % (os.path.relpath(path, root), os.path.getsize(path), packed))
    print("сжато, экономия %d КБ" % (saved // 1024))


if __name__ == "__main__":
    main(sys.argv[1])
