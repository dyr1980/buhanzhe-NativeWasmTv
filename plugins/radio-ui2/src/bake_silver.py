"""Bake the optical record's metal in continuous angular color, without a Skia LUT.

Only Python's standard library is required. The ordered sub-level rounding keeps
8-bit gradients smooth without filling the downloadable PNG with random noise.
"""
import bisect
import hashlib
import math
import pathlib
import struct
import zlib

SIZE = 1000
CENTER = SIZE / 2.0
PHASE = math.radians(146.0)  # The stronger reflection starts in the lower-left.
SILVER_STOPS = (0., .028, .065, .14, .24, .36, .435, .472, .5,
                .528, .565, .64, .74, .86, .935, .972, 1.)
SILVER = (0xe9ecf3, 0xb5bed2, 0x6e82a6, 0x435a82, 0x253154,
          0x364970, 0x63779c, 0x93a1bc, 0xa9b1c5, 0x95a0bb,
          0x65799e, 0x41577f, 0x253154, 0x364970, 0x6e82a6,
          0xb5bed2, 0xe9ecf3)
GROOVE = (0xf5f7fa, 0xc5cfde, 0x7e8ca8, 0xbac5d8, 0xd4dbe5,
          0xbac5d8, 0x7e8ca8, 0xc5cfde, 0xf5f7fa)
GROOVE_STOPS = tuple(i / 8. for i in range(9))
RADII = tuple(221. + i * 19.7 for i in range(6))
SHADE_STOPS = (0., .597, .62, .98, 1.)
SHADE = (0x00000000, 0x00000000, 0x18000b22, 0x00364f7c, 0x24eaf5ff)
SUBPIXELS = tuple((x, y) for y in (-.375, -.125, .125, .375)
                  for x in (-.375, -.125, .125, .375))


def channels(color, alpha=False):
    result = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
    return (((color >> 24) & 255) / 255.,) + result if alpha else result


def mix(stops, colors, position):
    i = min(len(stops) - 2, max(0, bisect.bisect_right(stops, position) - 1))
    weight = (position - stops[i]) / (stops[i + 1] - stops[i])
    return tuple(a + (b - a) * weight for a, b in zip(colors[i], colors[i + 1]))


SILVER_RGB = tuple(channels(c) for c in SILVER)
GROOVE_RGB = tuple(channels(c) for c in GROOVE)
SHADE_ARGB = tuple(channels(c, True) for c in SHADE)


def coverage(dx, dy, radius, half_width):
    """Sixteen coverage samples only along the anti-aliased circle boundaries."""
    distance = abs(math.hypot(dx, dy) - radius)
    if distance < half_width - .71:
        return 1.
    if distance > half_width + .71:
        return 0.
    return sum(abs(math.hypot(dx + sx, dy + sy) - radius) <= half_width
               for sx, sy in SUBPIXELS) / 16.


def over(rgb, alpha, foreground, opacity):
    """Keep premultiplied values while composing the layered metal."""
    inverse = 1. - opacity
    return (tuple(f * opacity + b * inverse for f, b in zip(foreground, rgb)),
            opacity + alpha * inverse)


def chunk(name, payload):
    return struct.pack('>I', len(payload)) + name + payload + struct.pack(
        '>I', zlib.crc32(name + payload) & 0xffffffff)


def ordered_rounding():
    matrix = [[0]]
    for _ in range(3):
        size = len(matrix)
        matrix = [[matrix[y % size][x % size] * 4 +
                   ((0, 2), (3, 1))[y // size][x // size]
                   for x in range(size * 2)] for y in range(size * 2)]
    return tuple(tuple((v + .5) / 64. for v in row) for row in matrix)


def bake(target, recipe):
    dither = ordered_rounding()
    rows = []
    previous = bytes(SIZE * 4)
    for y in range(SIZE):
        row = bytearray(SIZE * 4)
        dy = y + .5 - CENTER
        if abs(dy) <= 342:
            extent = math.sqrt(342. * 342. - dy * dy)
            start = max(0, math.floor(CENTER - extent - 1))
            end = min(SIZE, math.ceil(CENTER + extent + 1))
            for x in range(start, end):
                dx = x + .5 - CENTER
                radius = math.hypot(dx, dy)
                if radius < 203.1 or radius > 341.5:
                    continue
                angle = ((math.atan2(dy, dx) - PHASE) / math.tau) % 1.
                silver = mix(SILVER_STOPS, SILVER_RGB, angle)
                alpha = coverage(dx, dy, 272., 68.)
                rgb = tuple(c * alpha for c in silver)
                shade = mix(SHADE_STOPS, SHADE_ARGB, min(1., radius / 341.))
                shade_alpha = shade[0] * max(0., min(1., 341.5 - radius))
                rgb, alpha = over(rgb, alpha, shade[1:], shade_alpha)
                groove = mix(GROOVE_STOPS, GROOVE_RGB, angle)
                for line_radius in RADII:
                    if abs(radius - line_radius) < 1.6:
                        value = coverage(dx, dy, line_radius, .825) * (55. / 255.)
                        rgb, alpha = over(rgb, alpha, groove, value)
                if abs(radius - 340.) < 1.7:
                    rgb, alpha = over(rgb, alpha, groove,
                                      coverage(dx, dy, 340., .95) * (225. / 255.))
                if abs(radius - 204.) < 1.3:
                    rgb, alpha = over(rgb, alpha, groove,
                                      coverage(dx, dy, 204., .55) * (130. / 255.))
                if alpha <= 0.:
                    continue
                threshold = dither[y & 7][x & 7]
                offset = x * 4
                for channel in range(3):
                    row[offset + channel] = max(0, min(255, int(rgb[channel] / alpha + threshold)))
                row[offset + 3] = min(255, round(alpha * 255.))
        # Up/Sub filters compress smooth colors and the repeating dither well.
        sub = bytes((value - (row[i - 4] if i >= 4 else 0)) & 255
                    for i, value in enumerate(row))
        up = bytes((value - previous[i]) & 255 for i, value in enumerate(row))
        choices = ((0, bytes(row)), (1, sub), (2, up))
        filter_id, filtered = min(choices, key=lambda pair: sum(min(v, 256 - v) for v in pair[1]))
        rows.append(bytes((filter_id,)) + filtered)
        previous = bytes(row)
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
           + chunk(b'tEXt', b'RecipeSHA256\0' + recipe.encode('ascii'))
           + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b''))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(png)
    print('Baked', target, 'bytes=', len(png))


if __name__ == '__main__':
    source = pathlib.Path(__file__).resolve()
    target = source.parent.parent / 'assets/radio/optical-silver.png'
    recipe = hashlib.sha256(source.read_bytes()).hexdigest()
    if target.exists() and b'RecipeSHA256\0' + recipe.encode('ascii') in target.read_bytes():
        print('Silver texture is current:', target)
    else:
        bake(target, recipe)
