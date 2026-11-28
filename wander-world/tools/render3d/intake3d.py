"""
Intake for 3D building sources (0.15), alongside tools/asset_intake.py (PNG):
  - COLLADA meshes (.dae): refused if they contain DOCTYPE/ENTITY (entity expansion, external references), aren't
    well-formed XML with a COLLADA root, or are over 20 MB. Accepted files are copied unchanged (they're data only,
    parsed by our own renderer, never shipped).
  - DDS textures: decoded by Pillow inside quarantine, size-checked (<= 2048 px) and written as plain PNG pixels; the
    result then goes through asset_intake.py like any PNG.
Usage: python3 -I intake3d.py <raw_dir> <decoded_dir> <clean_dir>
"""
import shutil, subprocess, sys
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

raw, decoded, clean = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
ok = bad = 0
for f in sorted(raw.rglob("*.dae")):
    data = f.read_bytes(); why = None
    if b"<!DOCTYPE" in data or b"<!ENTITY" in data: why = "DOCTYPE/ENTITY"
    elif len(data) > 20_000_000: why = "too large"
    else:
        try:
            if not ET.fromstring(data).tag.endswith("COLLADA"): why = "not COLLADA"
        except ET.ParseError as e: why = f"parse: {e}"
    if why: bad += 1; print("REJECT", f, why); continue
    out = clean / f.relative_to(raw); out.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(f, out); ok += 1
print(f"dae: {ok} accepted, {bad} rejected")
n = 0
for f in sorted(raw.rglob("*.dds")):
    with Image.open(f) as im:
        if im.format != "DDS" or max(im.size) > 2048:
            print("REJECT", f, im.format, im.size); continue
        im.load()
        out = decoded / f.relative_to(raw).with_suffix(".png"); out.parent.mkdir(parents=True, exist_ok=True)
        Image.frombytes(im.mode, im.size, im.tobytes()).convert("RGBA").save(out); n += 1
print(f"dds: {n} decoded")
tools = Path(__file__).resolve().parents[1]
for src in (raw, decoded):
    subprocess.run([sys.executable, "-I", str(tools / "asset_intake.py"), str(src), str(clean)], check=True)
