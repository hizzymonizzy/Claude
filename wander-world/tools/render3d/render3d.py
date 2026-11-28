"""
A tiny software renderer for 0 A.D. buildings (COLLADA meshes + PNG textures), used by build_buildings.py (0.15).
No GPU, no extra packages: numpy + Pillow. Orthographic camera looking down at a chosen tilt.
Usage as a module: render(parts, tilt_deg, azimuth_deg, px_per_m) -> RGBA image (info["origin"]: ground point 0,0,0).
  parts: list of (dae_path, base_texture_path, ao_texture_path_or_None, 4x4 matrix or (dx, dy, rot_deg))
"""
import math
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

NS = {"c": "http://www.collada.org/2005/11/COLLADASchema"}


def _floats(el):
    return np.array([float(v) for v in el.text.split()], dtype=np.float64)


def load_dae(path):
    """Triangles of every instanced geometry: positions (N,3,3), normals (N,3,3), uv0 (N,3,2), uv1 (N,3,2) or None."""
    root = ET.parse(path).getroot()
    sources = {}
    for src in root.iter("{%s}source" % NS["c"]):
        fa = src.find("c:float_array", NS)
        acc = src.find(".//c:accessor", NS)
        if fa is not None and acc is not None:
            sources[src.get("id")] = _floats(fa).reshape(-1, int(acc.get("stride")))
    verts = {}
    for v in root.iter("{%s}vertices" % NS["c"]):
        inp = v.find("c:input[@semantic='POSITION']", NS)
        verts[v.get("id")] = inp.get("source")[1:]
    geoms = {}
    for g in root.iter("{%s}geometry" % NS["c"]):
        tris_all = []
        for tri in list(g.iter("{%s}triangles" % NS["c"])) + list(g.iter("{%s}polylist" % NS["c"])):
            inputs = tri.findall("c:input", NS)
            stride = max(int(i.get("offset")) for i in inputs) + 1
            p = np.array([int(v) for v in tri.find("c:p", NS).text.split()]).reshape(-1, stride)
            if tri.tag.endswith("polylist"):
                # Polygons of any size: fan-triangulate each one.
                vc = [int(v) for v in tri.find("c:vcount", NS).text.split()]
                rows, k = [], 0
                for c in vc:
                    for j in range(1, c - 1):
                        rows += [p[k], p[k + j], p[k + j + 1]]
                    k += c
                p = np.array(rows)
            data = {}
            for i in inputs:
                sem, off, src = i.get("semantic"), int(i.get("offset")), i.get("source")[1:]
                if sem == "VERTEX":
                    data["pos"] = sources[verts[src]][p[:, off]]
                elif sem == "NORMAL":
                    data["nrm"] = sources[src][p[:, off]]
                elif sem == "TEXCOORD":
                    data["uv%s" % i.get("set", "0")] = sources[src][p[:, off]][:, :2]
            tris_all.append(data)
        geoms[g.get("id")] = tris_all
    out = []
    for node in root.iter("{%s}node" % NS["c"]):
        ig = node.find("c:instance_geometry", NS)
        if ig is None:
            continue
        m = node.find("c:matrix", NS)
        mat = _floats(m).reshape(4, 4) if m is not None else np.eye(4)
        for d in geoms.get(ig.get("url")[1:], []):
            pos = d["pos"] @ mat[:3, :3].T + mat[:3, 3]
            nrm = d.get("nrm", np.zeros_like(pos)) @ mat[:3, :3].T
            out.append((pos, nrm, d.get("uv0"), d.get("uv1")))
    pos = np.concatenate([o[0] for o in out]).reshape(-1, 3, 3)
    nrm = np.concatenate([o[1] for o in out]).reshape(-1, 3, 3)
    uv0 = np.concatenate([o[2] for o in out]).reshape(-1, 3, 2)
    uv1 = np.concatenate([o[3] if o[3] is not None else o[2] for o in out]).reshape(-1, 3, 2)
    return pos, nrm, uv0, uv1


def _tex(path):
    if path is None:
        return None
    return np.asarray(Image.open(path).convert("RGBA"), dtype=np.float32) / 255.0


def _sample(tex, u, v):
    h, w = tex.shape[:2]
    x = np.mod(u, 1.0) * (w - 1)
    y = np.mod(1.0 - v, 1.0) * (h - 1)
    return tex[y.astype(int), x.astype(int)]


def render(parts, tilt_deg=0.0, azimuth_deg=30.0, px_per_m=12.0, size=None, light=(-0.5, 0.6, 0.65), ss=2):
    """Render buildings (Z up, metres) with an orthographic camera. tilt 0 = straight down."""
    tris, nrms, uvs0, uvs1, texi = [], [], [], [], []
    textures = []
    for k, (dae, base, ao, place) in enumerate(parts):
        try:
            pos, nrm, uv0, uv1 = load_dae(dae)
            tb = _tex(base); ta = _tex(ao) if ao and Path(ao).exists() else None
        except Exception:
            continue   # a part whose files didn't pass intake (e.g. DDS textures) is left out
        if len(place) == 3:
            dx, dy, rot = place; r = math.radians(rot)
            M = np.eye(4); M[:3, :3] = [[math.cos(r), -math.sin(r), 0], [math.sin(r), math.cos(r), 0], [0, 0, 1]]; M[:3, 3] = [dx, dy, 0]
        else:
            M = np.array(place)
        pos = pos @ M[:3, :3].T + M[:3, 3]; nrm = nrm @ M[:3, :3].T
        tris.append(pos); nrms.append(nrm); uvs0.append(uv0); uvs1.append(uv1)
        texi.append(np.full(len(pos), len(textures))); textures.append((tb, ta))
    P = np.concatenate(tris); N = np.concatenate(nrms); U0 = np.concatenate(uvs0); U1 = np.concatenate(uvs1); TI = np.concatenate(texi)
    # Camera: rotate around Z by azimuth, then tilt around X so we look down at an angle.
    a, t = math.radians(azimuth_deg), math.radians(tilt_deg)
    Rz = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    Rx = np.array([[1, 0, 0], [0, math.cos(-t), -math.sin(-t)], [0, math.sin(-t), math.cos(-t)]])
    V = P @ (Rx @ Rz).T          # screen x = V.x, screen y = V.y (up), depth = V.z (bigger = nearer the camera)
    s = px_per_m * ss
    minx, maxx = V[..., 0].min(), V[..., 0].max(); miny, maxy = V[..., 1].min(), V[..., 1].max()
    pad = 4 * ss
    W = int((maxx - minx) * s) + 2 * pad if size is None else size[0] * ss
    H = int((maxy - miny) * s) + 2 * pad if size is None else size[1] * ss
    cx = (minx + maxx) / 2; cy = (miny + maxy) / 2
    X = (V[..., 0] - cx) * s + W / 2; Y = H / 2 - (V[..., 1] - cy) * s; Z = V[..., 2]
    color = np.zeros((H, W, 4), np.float32); depth = np.full((H, W), -1e9, np.float32)
    L = np.array(light, dtype=np.float64); L /= np.linalg.norm(L)
    for i in range(len(P)):
        x, y, z = X[i], Y[i], Z[i]
        x0, x1 = max(int(x.min()), 0), min(int(x.max()) + 1, W - 1)
        y0, y1 = max(int(y.min()), 0), min(int(y.max()) + 1, H - 1)
        if x1 < x0 or y1 < y0:
            continue
        den = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
        if abs(den) < 1e-9:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((y[1] - y[2]) * (gx - x[2]) + (x[2] - x[1]) * (gy - y[2])) / den
        w1 = ((y[2] - y[0]) * (gx - x[2]) + (x[0] - x[2]) * (gy - y[2])) / den
        w2 = 1 - w0 - w1
        inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not inside.any():
            continue
        zz = w0 * z[0] + w1 * z[1] + w2 * z[2]
        sub = depth[y0:y1 + 1, x0:x1 + 1]
        vis = inside & (zz > sub)
        if not vis.any():
            continue
        base, ao = textures[TI[i]]
        u = w0 * U0[i, 0, 0] + w1 * U0[i, 1, 0] + w2 * U0[i, 2, 0]
        v = w0 * U0[i, 0, 1] + w1 * U0[i, 1, 1] + w2 * U0[i, 2, 1]
        c = _sample(base, u[vis], v[vis])
        keep = c[:, 3] > 0.45                      # cut-out edges (thatch, fences)
        if not keep.any():
            continue
        rgb = c[keep, :3].copy()
        if ao is not None:
            u1 = w0 * U1[i, 0, 0] + w1 * U1[i, 1, 0] + w2 * U1[i, 2, 0]
            v1 = w0 * U1[i, 0, 1] + w1 * U1[i, 1, 1] + w2 * U1[i, 2, 1]
            rgb *= _sample(ao, u1[vis][keep], v1[vis][keep])[:, :1] * 0.85 + 0.15
        n = N[i].mean(axis=0); nl = np.linalg.norm(n)
        lam = max(float(np.dot(n / nl, L)), 0.0) if nl > 0 else 0.6
        rgb *= 0.55 + 0.6 * lam
        ys, xs = np.nonzero(vis)
        ys, xs = ys[keep] + y0, xs[keep] + x0
        color[ys, xs, :3] = np.clip(rgb, 0, 1); color[ys, xs, 3] = 1.0
        depth[ys, xs] = zz[vis][keep]
    img = Image.fromarray((color * 255).astype(np.uint8), "RGBA").resize((W // ss, H // ss), Image.LANCZOS)
    o = np.array([0.0, 0.0, 0.0]) @ (Rx @ Rz).T
    img.info["origin"] = (((o[0] - cx) * s + W / 2) / ss, (H / 2 - (o[1] - cy) * s) / ss)   # where the ground point (0,0,0) lands
    return img


def with_shadow(img, dx=6, dy=8, blur=5, alpha=110):
    """A soft drop shadow under the building, as if lit from the upper left."""
    a = img.split()[3].point(lambda v: alpha if v > 0 else 0)
    sh = Image.new("RGBA", (img.width + dx + blur * 2, img.height + dy + blur * 2), (0, 0, 0, 0))
    black = Image.new("RGBA", img.size, (20, 26, 12, 255))
    sh.paste(black, (blur + dx, blur + dy), a)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    sh.alpha_composite(img, (blur, blur))
    if "origin" in img.info:
        sh.info["origin"] = (img.info["origin"][0] + blur, img.info["origin"][1] + blur)
    return sh


