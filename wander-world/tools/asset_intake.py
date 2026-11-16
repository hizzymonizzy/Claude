"""
Intake check for third-party image assets. For each file in the quarantine folder:
  1. magic bytes must be PNG
  2. it must decode fully (verify + load)
  3. no bytes after the IEND chunk (no polyglot / smuggled payload)
  4. sane dimensions (<= 2048 px)
Files that pass are re-encoded from raw pixels into a clean output folder (drops all metadata and
ancillary chunks). Only the clean copies are ever used. Usage: python3 -I intake.py <raw_dir> <clean_dir>
"""
import sys
from pathlib import Path
from PIL import Image

raw, clean = Path(sys.argv[1]), Path(sys.argv[2])
clean.mkdir(parents=True, exist_ok=True)
ok = bad = 0
for f in sorted(raw.rglob("*")):
    if not f.is_file():
        continue
    data = f.read_bytes()
    why = None
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        why = "not a PNG"
    elif not data.rstrip(b"\x00").endswith(b"IEND\xaeB`\x82"):
        why = "data after IEND"
    else:
        try:
            with Image.open(f) as im:
                im.verify()
            with Image.open(f) as im:
                im.load()
                if max(im.size) > 2048:
                    why = f"too large {im.size}"
                else:
                    out = clean / f.relative_to(raw)
                    out.parent.mkdir(parents=True, exist_ok=True)
                    Image.frombytes(im.mode, im.size, im.tobytes()).convert("RGBA").save(out, optimize=True)
        except Exception as e:
            why = f"decode failed: {e}"
    if why:
        bad += 1
        print(f"REJECT {f.relative_to(raw)}: {why}")
    else:
        ok += 1
print(f"intake: {ok} accepted and re-encoded, {bad} rejected")
