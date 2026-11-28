"""
Resolves 0 A.D. building actors (XML) into render parts: mesh + base texture + AO map + placement, following props
(fences, carts, plants) to their attach points. Files come from the official repo clone via `git show` into raw/,
then pass the intake checks (intake3d.py) before rendering. Used by build_buildings.py.
"""
import json, subprocess, sys
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np

REPO = RAW = None   # set by configure()
ART = "binaries/data/mods/public/art"
SKIP_POINTS = {"smoke", "garrisoned", "projectile", "loaded-", "fire"}
NS = "{http://www.collada.org/2005/11/COLLADASchema}"


def configure(repo, raw):
    global REPO, RAW
    REPO, RAW = Path(repo), Path(raw)


def get(rel):
    """Copy art/<rel> from the repo into raw/ (once). Returns the raw path, or None if missing."""
    out = RAW / rel
    if not out.exists():
        r = subprocess.run(["git", "-C", str(REPO), "show", f"HEAD:{ART}/{rel}"], capture_output=True)
        if r.returncode != 0:
            return None
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(r.stdout)
    return out


def attach_matrix(dae_rel, point):
    """The transform of node prop_<point> in a mesh (where a prop sits), or identity."""
    p = RAW / "meshes" / dae_rel
    try:
        root = ET.parse(p).getroot()
    except Exception:
        return np.eye(4)
    def walk(node, parent):
        m = node.find(NS + "matrix")
        mat = parent @ (np.array([float(v) for v in m.text.split()]).reshape(4, 4) if m is not None else np.eye(4))
        if node.get("name") in (f"prop_{point}", f"prop-{point}"):
            return mat
        for ch in node.findall(NS + "node"):
            r = walk(ch, mat)
            if r is not None:
                return r
        return None
    for scene in root.iter(NS + "visual_scene"):
        for n in scene.findall(NS + "node"):
            r = walk(n, np.eye(4))
            if r is not None:
                return r
    return np.eye(4)


def resolve(actor_rel, variant=0, mat=np.eye(4), inherited=None, depth=0):
    """Parts for one actor: the variant-th choice in each group (clamped), plus its props."""
    p = get(f"actors/{actor_rel}")
    if p is None or depth > 3:
        return []
    root = ET.parse(p).getroot()
    mesh, tex, props = None, dict(inherited or {}), []
    for g in root.findall("group"):
        vs = g.findall("variant")
        if not vs:
            continue
        # The variant number picks among meshes; every other group keeps its first (default) choice.
        has_mesh = any(x.find("mesh") is not None for x in vs)
        v = vs[min(variant, len(vs) - 1)] if has_mesh else vs[0]
        if v.get("file") and len(v) == 0:
            continue   # a shared variant file with nothing of its own (destruction, etc.)
        m = v.find("mesh")
        if m is not None:
            mesh = m.text.strip()
        for t in v.findall("textures/texture"):
            tex[t.get("name")] = t.get("file")
        for pr in v.findall("props/prop"):
            props.append((pr.get("actor"), pr.get("attachpoint")))
    parts = []
    if mesh and get(f"meshes/{mesh}") and tex.get("baseTex"):
        base = get(f"textures/skins/{tex['baseTex']}")
        ao = get(f"textures/skins/{tex['aoTex']}") if tex.get("aoTex") else None
        if base:
            parts.append({"dae": f"meshes/{mesh}", "base": f"textures/skins/{tex['baseTex']}",
                          "ao": f"textures/skins/{tex['aoTex']}" if ao else None, "matrix": mat.tolist()})
    for actor, point in props:
        if not actor or actor.startswith("particle/") or any(point.startswith(s) for s in SKIP_POINTS):
            continue
        m2 = mat @ attach_matrix(mesh, point) if mesh else mat
        # A prop's AO map refers to the parent's; props don't inherit base textures.
        parts += resolve(actor, 0, m2, None, depth + 1)
    return parts


