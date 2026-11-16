"""
Art-direction mockups for the world view (option B: pre-rendered isometric on a hex board).

A tiny software renderer: hex tiles and simple 3D shapes (boxes, roofs, cones, cylinders, tree blobs)
projected isometrically, lit by a sun, with cast shadows and a tilt-shift blur. Everything is drawn
from code; no third-party art. Two looks:
  - "stylized":  flat colors, crisp, toy-like (the feel of CC0 low-poly kits)
  - "realistic": textured, muted natural palette, soft shadows, ambient occlusion, more detail
                 (the feel of 0 A.D.-style assets)

Run:  python3 -I tools/mockups/diorama.py   → docs/art/*.png
"""
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

OUT = Path(__file__).resolve().parents[2] / "docs" / "art"
SQ3 = math.sqrt(3)

LIGHT = np.array([-0.55, -0.35, 0.76]); LIGHT /= np.linalg.norm(LIGHT)   # sun from upper left
VIEW = np.array([1.0, 1.0, 1.25]); VIEW /= np.linalg.norm(VIEW)          # camera at +x +y, above


def mix(c, k):
    return tuple(int(max(0, min(255, v * k))) for v in c[:3])


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Scene:
    def __init__(self, w, h, scale, origin, style, seed=1):
        self.w, self.h, self.s = w, h, scale
        self.ox, self.oy = origin
        self.style = style
        self.real = style == "realistic"
        self.rnd = random.Random(seed)
        self.tiles = []      # (depth, draw-fn)
        self.objects = []    # (depth, faces | callable)
        self.shadows = []    # 2D polygons / ellipses on the shadow layer
        self.ground = []     # flat decals drawn after tiles (roads, fields)

    # ---- projection ----
    def p(self, x, y, z=0.0):
        return (self.ox + (x - y) * self.s * 0.866, self.oy + (x + y) * self.s * 0.5 - z * self.s * 1.05)

    def shadow_pt(self, x, y, z, gz):
        k = (z - gz) / LIGHT[2]
        return self.p(x - LIGHT[0] * k, y - LIGHT[1] * k, gz)

    # ---- shading ----
    def shade(self, color, n):
        lam = max(0.0, float(np.dot(n, LIGHT)))
        if self.real:
            c = mix(color, 0.55 + 0.6 * lam)
            if lam < 0.25:  # cool skylight in shade
                c = lerp(c, (90, 110, 140), 0.12)
            return c
        return mix(color, 0.68 + 0.45 * lam)

    # ---- tiles ----
    def hex_tile(self, q, r, top, color, side=(120, 95, 70), texture=None):
        cx, cy = 1.5 * q, SQ3 * (r + q / 2)
        corners = [(cx + math.cos(math.radians(60 * i)), cy + math.sin(math.radians(60 * i))) for i in range(6)]
        depth = cx + cy

        def draw(d: ImageDraw.ImageDraw):
            # Sides (only the ones facing the camera), then the top.
            for i in range(6):
                (x0, y0), (x1, y1) = corners[i], corners[(i + 1) % 6]
                n = np.array([(x0 + x1) / 2 - cx, (y0 + y1) / 2 - cy, 0.0]); n /= np.linalg.norm(n)
                if np.dot(n, VIEW) <= 0:
                    continue
                d.polygon([self.p(x0, y0, top), self.p(x1, y1, top), self.p(x1, y1, -0.25), self.p(x0, y0, -0.25)],
                          fill=self.shade(side, n))
            if self.real:
                j = self.rnd.random()
                top_c = (int(color[0] * (0.94 + 0.1 * j)), int(color[1] * (0.95 + 0.07 * (1 - j))), int(color[2] * (0.94 + 0.08 * j)))
            else:
                top_c = color
            d.polygon([self.p(x, y, top) for x, y in corners], fill=top_c,
                      outline=mix(color, 0.8 if self.real else 0.85), width=1 if self.real else 2)
            if texture:
                texture(self, d, cx, cy, top)
        self.tiles.append((depth, draw))
        return cx, cy

    # ---- 3D shapes ----
    def _poly_faces(self, polys, color, center):
        faces = []
        c = np.array(center)
        for pts, col in polys:
            a, b, d = (np.array(pts[i]) for i in (0, 1, 2))
            n = np.cross(b - a, d - a)
            if np.linalg.norm(n) == 0:
                continue
            n = n / np.linalg.norm(n)
            centroid = np.mean(np.array(pts), axis=0)
            if np.dot(n, centroid - c) < 0:
                n = -n
            faces.append((pts, col or color, n))
        return faces

    def add_faces(self, faces, x, y, gz, cast=True, extra_depth=0.0):
        self.objects.append((x + y + 0.5 + extra_depth, faces))
        if cast:
            pts = [self.shadow_pt(px, py, pz, gz) for f in faces for (px, py, pz) in f[0]]
            self.shadows.append(("poly", convex_hull(pts)))

    def box(self, x, y, z, dx, dy, dz, color, top=None):
        x0, x1, y0, y1, z1 = x - dx / 2, x + dx / 2, y - dy / 2, y + dy / 2, z + dz
        polys = [
            ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], top),
            ([(x1, y0, z), (x1, y1, z), (x1, y1, z1), (x1, y0, z1)], None),
            ([(x0, y1, z), (x1, y1, z), (x1, y1, z1), (x0, y1, z1)], None),
            ([(x0, y0, z), (x0, y1, z), (x0, y1, z1), (x0, y0, z1)], None),
            ([(x0, y0, z), (x1, y0, z), (x1, y0, z1), (x0, y0, z1)], None),
        ]
        return self._poly_faces(polys, color, (x, y, z + dz / 2))

    def gable(self, x, y, z, dx, dy, h, color, wall, along_x=True, o=0.04):
        x0, x1, y0, y1 = x - dx / 2 - o, x + dx / 2 + o, y - dy / 2 - o, y + dy / 2 + o
        if along_x:
            r = [(x0, y, z + h), (x1, y, z + h)]
            polys = [([(x0, y0, z), (x1, y0, z), r[1], r[0]], None), ([(x0, y1, z), (x1, y1, z), r[1], r[0]], None),
                     ([(x - dx / 2, y - dy / 2, z), (x - dx / 2, y + dy / 2, z), (x - dx / 2, y, z + h)], wall),
                     ([(x + dx / 2, y - dy / 2, z), (x + dx / 2, y + dy / 2, z), (x + dx / 2, y, z + h)], wall)]
        else:
            r = [(x, y0, z + h), (x, y1, z + h)]
            polys = [([(x0, y0, z), (x0, y1, z), r[1], r[0]], None), ([(x1, y0, z), (x1, y1, z), r[1], r[0]], None),
                     ([(x - dx / 2, y - dy / 2, z), (x + dx / 2, y - dy / 2, z), (x, y - dy / 2, z + h)], wall),
                     ([(x - dx / 2, y + dy / 2, z), (x + dx / 2, y + dy / 2, z), (x, y + dy / 2, z + h)], wall)]
        return self._poly_faces(polys, color, (x, y, z))

    def pyramid(self, x, y, z, dx, dy, h, color):
        a = [(x - dx / 2, y - dy / 2, z), (x + dx / 2, y - dy / 2, z), (x + dx / 2, y + dy / 2, z), (x - dx / 2, y + dy / 2, z)]
        apex = (x, y, z + h)
        return self._poly_faces([([a[i], a[(i + 1) % 4], apex], None) for i in range(4)], color, (x, y, z))

    def cylinder(self, x, y, z, r, h, color, n=10, top=None):
        ring = [(x + r * math.cos(2 * math.pi * i / n), y + r * math.sin(2 * math.pi * i / n)) for i in range(n)]
        polys = [([(*ring[i], z), (*ring[(i + 1) % n], z), (*ring[(i + 1) % n], z + h), (*ring[i], z + h)], None) for i in range(n)]
        polys.append(([(px, py, z + h) for px, py in ring], top))
        return self._poly_faces(polys, color, (x, y, z + h / 2))

    def cone(self, x, y, z, r, h, color, n=10):
        ring = [(x + r * math.cos(2 * math.pi * i / n), y + r * math.sin(2 * math.pi * i / n)) for i in range(n)]
        return self._poly_faces([([(*ring[i], z), (*ring[(i + 1) % n], z), (x, y, z + h)], None) for i in range(n)], color, (x, y, z + h * 0.3))

    # ---- composite objects ----
    def house(self, x, y, gz, w, dep, h, wall, roof, along_x=True, roof_h=None, chimney=False, trim=None):
        k = 1.35
        w, dep, h = w * k, dep * k, h * k
        roof_h = roof_h * k if roof_h else None
        faces = self.box(x, y, gz, w, dep, h, wall)
        faces += self.gable(x, y, gz + h, w, dep, roof_h or h * 0.8, roof, wall, along_x)
        if chimney:
            faces += self.box(x + w * 0.25, y, gz + h, 0.06, 0.06, (roof_h or h * 0.8) + 0.06, (120, 70, 55))
        self.add_faces(faces, x, y, gz)
        # Windows, door and (realistic) timber/shingle lines are painted after the faces.
        self.objects.append((x + y + 0.5001, lambda dr: self._house_details(dr, x, y, gz, w, dep, h, trim)))

    def _house_details(self, dr, x, y, gz, w, dd, h, trim):
        win = (245, 215, 120) if self.real else (250, 225, 140)
        for t in (0.3, 0.7):          # windows on the +y face (facing camera-left)
            px, py = x - w / 2 + w * t, y + dd / 2 + 0.001
            dr.polygon([self.p(px - 0.035, py, gz + h * 0.45), self.p(px + 0.035, py, gz + h * 0.45),
                        self.p(px + 0.035, py, gz + h * 0.75), self.p(px - 0.035, py, gz + h * 0.75)], fill=win, outline=trim)
        px = x + w / 2 + 0.001        # door on the +x face
        dr.polygon([self.p(px, y - 0.04, gz), self.p(px, y + 0.04, gz), self.p(px, y + 0.04, gz + h * 0.62), self.p(px, y - 0.04, gz + h * 0.62)],
                   fill=(95, 65, 45))
        if self.real:                 # ambient occlusion along the base
            dr.line([self.p(x - w / 2, y + dd / 2, gz + 0.01), self.p(x + w / 2, y + dd / 2, gz + 0.01), self.p(x + w / 2, y - dd / 2, gz + 0.01)],
                    fill=(40, 45, 35), width=max(2, int(self.s * 0.03)))

    def tree(self, x, y, gz, size=1.0, kind="oak", color=None):
        s = size * 1.25
        if kind == "pine":
            faces = self.cylinder(x, y, gz, 0.025 * s, 0.12 * s, (95, 70, 50), n=6)
            col = color or (52, 104, 62)
            for i, (r, z0, h) in enumerate([(0.17, 0.08, 0.28), (0.13, 0.22, 0.24), (0.09, 0.34, 0.2)]):
                faces += self.cone(x, y, gz + z0 * s, r * s, h * s, mix(col, 1 + 0.06 * i), n=9)
            self.add_faces(faces, x, y, gz)
            return
        if kind == "palm":
            self.objects.append((x + y + 0.5, lambda d: self._palm(d, x, y, gz, s)))
            self.shadows.append(("ellipse", self.shadow_pt(x + 0.08 * s, y, gz + 0.55 * s, gz), 0.2 * s))
            return
        # Broadleaf: trunk + clustered canopy blobs.
        self.add_faces(self.cylinder(x, y, gz, 0.025 * s, 0.16 * s, (105, 78, 55), n=6), x, y, gz, cast=False)
        col = color or (70, 128, 60)
        blobs = [(0, 0, 0.26, 0.15), (-0.07, 0.04, 0.22, 0.11), (0.06, 0.05, 0.21, 0.11), (0.01, -0.06, 0.33, 0.1)]
        self.objects.append((x + y + 0.51, lambda d: [self._blob(d, x + bx * s, y + by * s, gz + bz * s, br * s, col) for bx, by, bz, br in blobs]))
        self.shadows.append(("ellipse", self.shadow_pt(x, y, gz + 0.26 * s, gz), 0.17 * s))

    def _blob(self, d, x, y, z, r, col):
        cx, cy = self.p(x, y, z)
        R = r * self.s
        steps = 6 if self.real else 3
        for i in range(steps):
            t = i / steps
            k = 0.62 + 0.6 * t
            off = R * 0.35 * t
            rr = R * (1 - 0.55 * t)
            d.ellipse([cx - rr - off, cy - rr - off * 0.8, cx + rr - off, cy + rr - off * 0.8], fill=mix(col, k))
        if self.real:
            for _ in range(10):
                a, rad = self.rnd.random() * 6.28, self.rnd.random() * R * 0.8
                px, py = cx + math.cos(a) * rad, cy + math.sin(a) * rad
                d.ellipse([px - 2, py - 2, px + 2, py + 2], fill=mix(col, 0.75 + self.rnd.random() * 0.6))

    def _palm(self, d, x, y, gz, s):
        pts = [self.p(x + 0.08 * s * (t ** 2), y, gz + 0.55 * s * t) for t in np.linspace(0, 1, 8)]
        d.line(pts, fill=(140, 110, 75), width=max(3, int(self.s * 0.035 * s)))
        tx, ty = pts[-1]
        for i in range(7):
            a = 2 * math.pi * i / 7 + 0.3
            L = self.s * 0.24 * s
            ex, ey = tx + math.cos(a) * L, ty + math.sin(a) * L * 0.55 + L * 0.25
            mx, my = tx + math.cos(a) * L * 0.5, ty + math.sin(a) * L * 0.3 - L * 0.12
            col = mix((60, 140, 70), 0.85 + 0.25 * math.cos(a + 2.2))
            d.polygon([(tx, ty), (mx - 3, my), (ex, ey), (mx + 3, my + 5)], fill=col)

    def tent(self, x, y, gz, color=(232, 217, 176)):
        self.add_faces(self.pyramid(x, y, gz, 0.3, 0.3, 0.32, color), x, y, gz)

    def campfire(self, x, y, gz):
        def draw(d):
            cx, cy = self.p(x, y, gz)
            r = self.s * 0.045
            d.ellipse([cx - r, cy - r * 0.5, cx + r, cy + r * 0.5], fill=(90, 80, 70))
            d.polygon([(cx - r * 0.6, cy), (cx, cy - r * 2.2), (cx + r * 0.6, cy)], fill=(240, 150, 50))
            d.polygon([(cx - r * 0.3, cy), (cx, cy - r * 1.3), (cx + r * 0.3, cy)], fill=(255, 225, 120))
        self.objects.append((x + y + 0.5, draw))

    def peak(self, x, y, gz, r, h, rock=(158, 150, 138), snow=(248, 250, 252)):
        faces = self.cone(x, y, gz, r, h, rock, n=7)
        faces += self.cone(x, y, gz + h * 0.5, r * 0.52, h * 0.52, snow, n=7)
        self.add_faces(faces, x, y, gz)

    def bush(self, x, y, gz, col=(74, 122, 58)):
        self.objects.append((x + y + 0.5, lambda d: self._blob(d, x, y, gz + 0.05, 0.07, col)))

    def scatter(self, layout, avoid, n_trees=2, n_bush=3, heights=None, palette=None):
        """Lush filler: a few trees and bushes on open grass tiles."""
        palette = palette or [(70, 124, 58), (88, 138, 64), (62, 112, 60)]
        for (q, r), (col, _) in layout.items():
            if (q, r) in avoid or col not in (GRASS, MEADOW, FOREST_FLOOR):
                continue
            cx, cy = hex_center(q, r)
            gz = (heights or {}).get((q, r), 0.0)
            for _ in range(n_trees if col != FOREST_FLOOR else 7):
                a, rr = self.rnd.random() * 6.28, 0.2 + self.rnd.random() * 0.55
                self.tree(cx + math.cos(a) * rr, cy + math.sin(a) * rr, gz, 0.75 + self.rnd.random() * 0.4, color=self.rnd.choice(palette))
            for _ in range(n_bush):
                a, rr = self.rnd.random() * 6.28, self.rnd.random() * 0.7
                self.bush(cx + math.cos(a) * rr, cy + math.sin(a) * rr, gz, self.rnd.choice(palette))

    def tower(self, x, y, gz, r, h, color, roof=None):
        faces = self.cylinder(x, y, gz, r, h, color, n=10)
        if roof:
            faces += self.cone(x, y, gz + h, r * 1.2, r * 2.2, roof, n=10)
        self.add_faces(faces, x, y, gz)

    def wall(self, pts, gz, color, h=0.14, t=0.06):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 0.2))
            for i in range(n):
                cx, cy = x0 + (x1 - x0) * (i + 0.5) / n, y0 + (y1 - y0) * (i + 0.5) / n
                ax = abs(x1 - x0) >= abs(y1 - y0)
                L = math.hypot(x1 - x0, y1 - y0) / n
                self.add_faces(self.box(cx, cy, gz, L if ax else t, t if ax else L, h, color), cx, cy, gz)

    # ---- ground decals ----
    def road(self, pts, gz, width=0.09, color=(176, 146, 104)):
        def draw(d):
            line = [self.p(x, y, gz + 0.002) for x, y in pts]
            d.line(line, fill=mix(color, 0.82), width=int(self.s * width * 1.25), joint="curve")
            d.line(line, fill=color, width=int(self.s * width), joint="curve")
            if self.real:
                d.line(line, fill=mix(color, 1.12), width=max(1, int(self.s * width * 0.25)), joint="curve")
        self.ground.append(draw)

    # ---- render ----
    def render(self, title=None):
        sky = Image.new("RGB", (self.w, self.h), (205, 225, 220) if self.real else (196, 228, 236))
        g = ImageDraw.Draw(sky)
        for i in range(self.h):   # soft vertical gradient behind the board
            t = i / self.h
            g.line([(0, i), (self.w, i)], fill=lerp((190, 215, 222), (226, 236, 214), t))
        img = sky
        d = ImageDraw.Draw(img)
        for _, fn in sorted(self.tiles, key=lambda t: t[0]):
            fn(d)
        for fn in self.ground:
            fn(d)
        # Shadows on their own layer (blurred for the realistic look).
        sh = Image.new("L", (self.w, self.h), 0)
        sd = ImageDraw.Draw(sh)
        for s in self.shadows:
            if s[0] == "poly":
                if len(s[1]) >= 3:
                    sd.polygon(s[1], fill=255)
            else:
                (cx, cy), r = s[1], s[2] * self.s
                sd.ellipse([cx - r, cy - r * 0.5, cx + r, cy + r * 0.5], fill=255)
        if self.real:
            sh = sh.filter(ImageFilter.GaussianBlur(self.s * 0.03))
        shade = Image.new("RGB", img.size, (30, 45, 40))
        img = Image.composite(shade, img, sh.point(lambda v: int(v * (0.42 if self.real else 0.3))))
        d = ImageDraw.Draw(img)
        for _, obj in sorted(self.objects, key=lambda o: o[0]):
            if callable(obj):
                obj(d)
            else:
                for pts, col, n in sorted(obj, key=lambda f: np.mean([p[0] + p[1] for p in f[0]]) + np.mean([p[2] for p in f[0]]) * 0.2):
                    if np.dot(n, VIEW) <= 0:
                        continue
                    c = self.shade(col, n)
                    d.polygon([self.p(*p) for p in pts], fill=c, outline=None if self.real else mix(col, 0.55))
        if self.real:
            rng = np.random.default_rng(5)
            low = Image.fromarray((rng.normal(128, 30, (self.h // 24 + 1, self.w // 24 + 1))).clip(0, 255).astype(np.uint8)).resize((self.w, self.h), Image.BICUBIC)
            fine = rng.normal(0, 7, (self.h, self.w))
            m = (np.asarray(low).astype(np.float32) - 128) / 128 * 0.07 + 1.0 + fine / 255
            img = Image.fromarray(np.clip(np.asarray(img).astype(np.float32) * m[..., None], 0, 255).astype(np.uint8))
        img = self.tilt_shift(img)
        if title:
            d = ImageDraw.Draw(img)
            f = ImageFont.load_default(size=int(self.h * 0.035))
            d.text((self.w * 0.03, self.h * 0.03), title, fill=(40, 50, 40), font=f)
        return img

    def tilt_shift(self, img):
        blurred = img.filter(ImageFilter.GaussianBlur(self.s * (0.09 if self.real else 0.06)))
        mask = Image.new("L", img.size)
        md = ImageDraw.Draw(mask)
        focus, band = self.h * 0.55, self.h * 0.17
        for yy in range(self.h):
            t = max(0.0, (abs(yy - focus) - band) / (self.h * 0.3))
            md.line([(0, yy), (self.w, yy)], fill=int(255 * min(1.0, t)))
        out = Image.composite(blurred, img, mask)
        out = ImageEnhance.Color(out).enhance(1.28 if self.real else 1.15)
        out = ImageEnhance.Contrast(out).enhance(1.06)
        if self.real:   # film grain + vignette
            noise = (np.random.default_rng(3).normal(0, 5, (self.h, self.w, 1))).astype(np.int16)
            arr = np.clip(np.asarray(out).astype(np.int16) + noise, 0, 255).astype(np.uint8)
            out = Image.fromarray(arr)
            yy, xx = np.mgrid[0:self.h, 0:self.w]
            v = 1 - 0.28 * (((xx - self.w / 2) / (self.w / 2)) ** 2 + ((yy - self.h / 2) / (self.h / 2)) ** 2)
            out = Image.fromarray((np.asarray(out) * np.clip(v, 0.6, 1)[..., None]).astype(np.uint8))
        return out


def convex_hull(points):
    pts = sorted(set((round(x, 1), round(y, 1)) for x, y in points))
    if len(pts) < 3:
        return pts
    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


# ---------------- textures for tile tops ----------------

def grass_tex(sc, d, cx, cy, top):
    if not sc.real:
        return
    for _ in range(40):
        a, r = sc.rnd.random() * 6.28, sc.rnd.random() * 0.85
        x, y = cx + math.cos(a) * r, cy + math.sin(a) * r
        px, py = sc.p(x, y, top)
        c = (78 + sc.rnd.randint(-15, 25), 140 + sc.rnd.randint(-20, 25), 62 + sc.rnd.randint(-10, 15))
        d.line([(px, py), (px + sc.rnd.randint(-2, 2), py - sc.rnd.randint(3, 7))], fill=c, width=2)
    for _ in range(4):   # wildflowers
        a, r = sc.rnd.random() * 6.28, sc.rnd.random() * 0.8
        px, py = sc.p(cx + math.cos(a) * r, cy + math.sin(a) * r, top)
        d.ellipse([px - 2, py - 2, px + 2, py + 2], fill=sc.rnd.choice([(250, 240, 200), (240, 200, 90), (220, 140, 170)]))


def field_tex(color):
    def tex(sc, d, cx, cy, top):
        R = 0.78   # stay inside the hex's inscribed circle
        for i in range(-5, 6):
            off = i * 0.14
            half = math.sqrt(max(0.0, R * R - off * off))
            # Rows run diagonally: direction (1, -1)/√2, offset along (1, 1)/√2.
            ox, oy = off / SQ3 * 1.22, off / SQ3 * 1.22
            a = sc.p(cx + ox - half * 0.707, cy + oy + half * 0.707, top)
            b = sc.p(cx + ox + half * 0.707, cy + oy - half * 0.707, top)
            d.line([a, b], fill=mix(color, 0.78), width=max(2, int(sc.s * 0.03)))
    return tex


def water_tex(sc, d, cx, cy, top):
    for _ in range(8 if sc.real else 4):
        a, r = sc.rnd.random() * 6.28, sc.rnd.random() * 0.7
        px, py = sc.p(cx + math.cos(a) * r, cy + math.sin(a) * r, top)
        w = sc.s * 0.12
        d.line([(px - w, py), (px + w, py)], fill=(225, 240, 245), width=2)


def snow_tex(sc, d, cx, cy, top):
    px, py = sc.p(cx, cy, top)
    r = sc.s * 0.5
    d.polygon([(px - r, py), (px, py - r * 0.45), (px + r, py), (px, py + r * 0.45)], fill=(245, 248, 250))


# ---------------- scenes ----------------

GRASS = (104, 148, 76)
MEADOW = (134, 164, 86)
FOREST_FLOOR = (78, 116, 62)
WATER = (70, 138, 160)
SAND = (222, 204, 156)
WHEAT = (206, 180, 98)


def board(sc, layout, heights=None, margin=0.08, top_room=0.12):
    """layout: dict (q, r) -> (color, texture). Fits the camera to the board, then adds the tiles."""
    xs, ys = [], []
    for (q, r) in layout:
        cx, cy = hex_center(q, r)
        for i in range(6):
            x, y = cx + math.cos(math.radians(60 * i)), cy + math.sin(math.radians(60 * i))
            for z in (-0.25, 0.6):
                xs.append((x - y) * 0.866); ys.append((x + y) * 0.5 - z * 1.05)
    bw, bh = max(xs) - min(xs), max(ys) - min(ys)
    s = min(sc.w * (1 - 2 * margin) / bw, sc.h * (1 - 2 * margin - top_room) / bh)
    sc.s = s
    sc.ox = sc.w / 2 - (min(xs) + bw / 2) * s
    sc.oy = sc.h * (margin + top_room) - min(ys) * s + (sc.h * (1 - 2 * margin - top_room) - bh * s) / 2
    for (q, r), (col, tex) in layout.items():
        top = (heights or {}).get((q, r), 0.0 if col != WATER else -0.06)
        sc.hex_tile(q, r, top, col, texture=tex)


def hex_center(q, r):
    return 1.5 * q, SQ3 * (r + q / 2)


def nj_village(style, w=1600, h=1000):
    """The same New Jersey board with a camp, a village and a town, along a river."""
    sc = Scene(w, h, scale=1, origin=(0, 0), style=style, seed=4)
    layout = {}
    for q in range(-3, 4):
        for r in range(-1, 4):
            if abs(q + r - 1) > 3 or q + r < -1:
                continue
            col, tex = GRASS, grass_tex
            if (q, r) in [(-3, 3), (-2, 3), (-1, 3), (0, 2), (1, 2), (2, 1), (3, 0)]:
                col, tex = WATER, water_tex
            elif (q, r) in [(1, -1), (3, -1), (-1, 0), (-3, 2)]:
                col, tex = FOREST_FLOOR, grass_tex
            elif (q, r) in [(1, 0), (-2, 2)]:
                col, tex = WHEAT, field_tex(WHEAT)
            elif (q, r) in [(0, 3), (2, 2), (-1, 2)]:
                col, tex = MEADOW, grass_tex
            layout[(q, r)] = (col, tex)
    board(sc, layout)

    # Roads between settlements.
    camp = hex_center(-2, 1); vil = hex_center(0, 1); town = hex_center(2, 0)
    sc.road([(camp[0] + 0.2, camp[1] + 0.2), (vil[0] - 0.3, vil[1] + 0.25), (vil[0] + 0.1, vil[1] - 0.1), (town[0] - 0.4, town[1] + 0.3)], 0.0)

    # Camp: tents and a fire.
    x, y = camp
    sc.tent(x - 0.2, y - 0.1, 0.0); sc.tent(x + 0.15, y - 0.25, 0.0, (220, 200, 160)); sc.campfire(x + 0.05, y + 0.12, 0.0)
    sc.tree(x - 0.45, y + 0.35, 0.0, 0.9)

    # Village: timber and brick houses, a well.
    x, y = vil
    brick, timber, slate, thatch = (168, 82, 62), (196, 170, 128), (88, 92, 104), (176, 140, 82)
    sc.house(x - 0.35, y - 0.3, 0.0, 0.34, 0.26, 0.2, timber, thatch if style == "realistic" else (190, 95, 60), True)
    sc.house(x + 0.25, y - 0.35, 0.0, 0.3, 0.28, 0.22, brick, slate, False, chimney=True)
    sc.house(x - 0.05, y + 0.3, 0.0, 0.32, 0.24, 0.2, timber, slate, True)
    sc.add_faces(sc.cylinder(x + 0.35, y + 0.2, 0.0, 0.06, 0.07, (150, 150, 145)), x + 0.35, y + 0.2, 0.0)
    for tx, ty in [(-0.6, 0.1), (0.55, -0.05)]:
        sc.tree(x + tx, y + ty, 0.0, 1.0, color=(78, 136, 64))

    # Town: more houses, a market hall, a stone tower and a low wall.
    x, y = town
    sc.wall([(x - 0.75, y - 0.55), (x + 0.65, y - 0.55), (x + 0.65, y + 0.6)], 0.0, (170, 165, 150))
    sc.house(x - 0.4, y - 0.15, 0.0, 0.36, 0.28, 0.26, brick, slate, True, chimney=True)
    sc.house(x + 0.15, y - 0.25, 0.0, 0.44, 0.3, 0.3, (214, 200, 170), (150, 60, 45), False)
    sc.house(x - 0.1, y + 0.35, 0.0, 0.3, 0.26, 0.22, timber, slate, True)
    sc.house(x + 0.4, y + 0.25, 0.0, 0.26, 0.24, 0.24, brick, (110, 70, 55), False)
    sc.tower(x + 0.6, y - 0.5, 0.0, 0.1, 0.55, (176, 170, 156), roof=(90, 70, 70))

    # Woods on forest tiles and lush filler elsewhere, keeping settlement tiles clear.
    sc.scatter(layout, avoid={(-2, 1), (0, 1), (2, 0)})
    return sc


def region_nj(style, w, h):
    sc = Scene(w, h, scale=1, origin=(0, 0), style=style, seed=11)
    lay = {(q, r): (GRASS, grass_tex) for q in range(-2, 3) for r in range(-1, 3) if abs(q + r) <= 2}
    lay[(2, 0)] = (WATER, water_tex); lay[(1, 1)] = (WATER, water_tex)
    board(sc, lay)
    c = hex_center(0, 0)
    # Colonial brick and clapboard, white trim, slate roofs, autumn maples.
    sc.house(c[0] - 0.3, c[1] - 0.2, 0, 0.38, 0.26, 0.26, (164, 76, 58), (70, 74, 86), True, chimney=True, trim=(240, 240, 235))
    sc.house(c[0] + 0.3, c[1] + 0.1, 0, 0.3, 0.26, 0.24, (230, 228, 215), (70, 74, 86), False, trim=(240, 240, 235))
    sc.house(c[0] - 0.1, c[1] + 0.45, 0, 0.3, 0.24, 0.22, (120, 140, 160), (60, 62, 72), True)
    sc.scatter(lay, avoid={(0, 0)}, n_trees=4, n_bush=2,
               palette=[(70, 128, 58), (196, 120, 50), (180, 70, 45), (210, 170, 60), (84, 136, 62)])
    return sc


def region_hawaii(style, w, h):
    sc = Scene(w, h, scale=1, origin=(0, 0), style=style, seed=12)
    lay = {}
    for q in range(-2, 3):
        for r in range(-1, 3):
            if abs(q + r) > 2:
                continue
            lay[(q, r)] = (WATER, water_tex) if q + r >= 2 or (q, r) in [(2, -1)] else ((SAND, None) if q + r == 1 else (MEADOW, grass_tex))
    lay[(-2, 0)] = ((84, 140, 70), grass_tex)
    board(sc, lay, heights={(-2, 0): 0.35, (-1, -1): 0.18})
    c = hex_center(0, 0)
    # Pastel plantation-style houses with hip roofs, and thatched hale.
    sc.add_faces(sc.box(c[0] - 0.3, c[1] - 0.2, 0, 0.46, 0.36, 0.26, (236, 200, 180)) +
                 sc.pyramid(c[0] - 0.3, c[1] - 0.2, 0.26, 0.58, 0.48, 0.22, (110, 140, 120)), c[0] - 0.3, c[1] - 0.2, 0)
    sc.add_faces(sc.box(c[0] + 0.32, c[1] + 0.2, 0, 0.36, 0.32, 0.2, (190, 160, 110)) +
                 sc.pyramid(c[0] + 0.32, c[1] + 0.2, 0.2, 0.52, 0.46, 0.3, (176, 146, 86)), c[0] + 0.32, c[1] + 0.2, 0)
    for (q, r) in [(-1, 1), (0, 1), (1, 0), (1, -1), (-1, 0)]:
        cx, cy = hex_center(q, r)
        for _ in range(3):
            a, rr = sc.rnd.random() * 6.28, sc.rnd.random() * 0.55
            sc.tree(cx + math.cos(a) * rr, cy + math.sin(a) * rr, 0, 1.0, kind="palm")
    cx, cy = hex_center(-2, 0)
    for _ in range(5):
        a, rr = sc.rnd.random() * 6.28, sc.rnd.random() * 0.5
        sc.tree(cx + math.cos(a) * rr, cy + math.sin(a) * rr, 0.35, 0.9, color=(56, 120, 60))
    return sc


def region_denver(style, w, h):
    sc = Scene(w, h, scale=1, origin=(0, 0), style=style, seed=13)
    lay, heights = {}, {}
    for q in range(-2, 3):
        for r in range(-1, 3):
            if abs(q + r) > 2:
                continue
            back = q + r <= -1
            lay[(q, r)] = ((112, 128, 96), None) if back else (MEADOW if (q + r) % 2 else GRASS, grass_tex)
            heights[(q, r)] = 0.25 if back else 0.0
    lay[(1, 1)] = (WATER, water_tex)
    board(sc, lay, heights)
    for (q, r) in lay:
        if q + r <= -1:
            cx, cy = hex_center(q, r)
            sc.peak(cx, cy, 0.25, 0.85, 1.1 + 0.25 * sc.rnd.random())
    c = hex_center(0, 0)
    # Log lodges with steep roofs, pines and aspens.
    sc.house(c[0] - 0.25, c[1] - 0.05, 0, 0.36, 0.28, 0.2, (130, 88, 58), (70, 60, 55), True, roof_h=0.26, chimney=True)
    sc.house(c[0] + 0.3, c[1] + 0.25, 0, 0.28, 0.24, 0.18, (140, 96, 64), (80, 70, 62), False, roof_h=0.24)
    for (q, r) in [(-1, 1), (1, 0), (2, -1), (-2, 2), (0, 2)]:
        if (q, r) not in lay:
            continue
        cx, cy = hex_center(q, r)
        for _ in range(6):
            a, rr = sc.rnd.random() * 6.28, sc.rnd.random() * 0.6
            sc.tree(cx + math.cos(a) * rr, cy + math.sin(a) * rr, heights.get((q, r), 0), 0.9 + sc.rnd.random() * 0.3, kind="pine")
        sc.tree(cx, cy + 0.2, heights.get((q, r), 0), 0.7, color=(210, 180, 60))   # golden aspen
    return sc


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    W, H = 3200, 2000   # rendered at 2x, then downsampled for smooth edges
    for style in ("stylized", "realistic"):
        img = nj_village(style, W, H).render()
        img.resize((W // 2, H // 2), Image.LANCZOS).save(OUT / f"mockup_{style}.png")
    # Regional architecture, realistic look.
    tiles = []
    for name, fn in [("New Jersey · brick & clapboard, maples", region_nj),
                     ("Honolulu · pastel & thatch, palms, reef", region_hawaii),
                     ("Denver · log lodges, pines, aspens, peaks", region_denver)]:
        im = fn("realistic", 1800, 1400).render(title=name)
        tiles.append(im.resize((900, 700), Image.LANCZOS))
    sheet = Image.new("RGB", (2700, 700))
    for i, t in enumerate(tiles):
        sheet.paste(t, (900 * i, 0))
    sheet.save(OUT / "mockup_regions.png")
    # Side-by-side comparison of the two looks.
    a = Image.open(OUT / "mockup_stylized.png"); b = Image.open(OUT / "mockup_realistic.png")
    cmp = Image.new("RGB", (a.width * 2, a.height))
    cmp.paste(a, (0, 0)); cmp.paste(b, (a.width, 0))
    d = ImageDraw.Draw(cmp)
    f = ImageFont.load_default(size=40)
    d.text((40, 30), "A · Stylized (clean, toy-like)", fill=(30, 40, 30), font=f)
    d.text((a.width + 40, 30), "B · Realistic-leaning (textured, soft light)", fill=(30, 40, 30), font=f)
    cmp.save(OUT / "mockup_compare.png")
    print("wrote", sorted(p.name for p in OUT.glob("mockup_*.png")))


if __name__ == "__main__":
    main()
