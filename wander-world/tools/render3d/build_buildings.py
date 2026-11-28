"""
Builds Wander World's settlement buildings (0.15) by rendering 0 A.D.'s own 3D models, bird's-eye at 35°, for each
culture. The app never renders 3D: it draws these pictures, placed on a settlement's hexes and scaled with the map.

Steps (all in a quarantine folder you pass in):
  1. resolve each building's actor (mesh, texture, AO map, props) from the official repo clone (`git show`);
  2. intake: meshes checked (no DOCTYPE/ENTITY, well-formed COLLADA), DDS decoded, every PNG through asset_intake.py;
  3. render each building at PX_PER_M, padded so its ground point is the image centre (so the map anchors it there);
  4. write app/src/main/res/drawable-nodpi/b3d_<culture>_<key>.png, ui/Buildings3d.kt, docs/art/BUILDINGS_3D.md.

0 A.D. art © Wildfire Games, CC BY-SA 3.0; these renders are adaptations under the same licence.
Run:  python3 -I tools/render3d/build_buildings.py <0ad_repo_clone> <quarantine_dir>
"""
import json, subprocess, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import actors   # noqa: E402
import render3d  # noqa: E402
from PIL import Image  # noqa: E402

RES = ROOT / "app/src/main/res/drawable-nodpi"
KT = ROOT / "app/src/main/java/world/wander/app/ui/Buildings3d.kt"
DOC = ROOT / "docs/art/BUILDINGS_3D.md"
PX_PER_M = 5.0
TILT, AZIMUTH = 35.0, 30.0
EYE = "props/special/eyecandy/"

CULTURES = ["britons", "romans", "persians"]
# Our key -> actor per culture ("{c}" is the culture folder). Houses fill a settlement's paved hexes; the rest are
# the development story's projects (Projects.ALL ids), plus the centre building by tier.
SHARED = {"campfire": EYE + "campfire.xml", "well": EYE + "well_1_a.xml", "forager_cache": EYE + "produce_bins.xml",
          "lean_to": "structures/tent_big.xml", "signpost": EYE + "standing_stones.xml", "vegetable_patch": EYE + "hay_large.xml",
          "fountain": EYE + "statue_aphrodite_small.xml", "bridge": EYE + "bridge_edge_wooden.xml"}
PER = {
    "house_0": "structures/{c}/house.xml:0", "house_1": "structures/{c}/house.xml:1",
    "house_2": "structures/{c}/house.xml:2", "house_3": "structures/{c}/house.xml:3",
    "meeting_hall": "structures/{c}/barracks.xml", "woodshed": "structures/{c}/storehouse.xml",
    "market": "structures/{c}/market.xml", "market_hall": "structures/{c}/market.xml", "mill": "structures/{c}/farmstead.xml",
    "shrine": {"britons": "structures/britons/temple.xml", "romans": "structures/romans/temple.xml", "persians": "structures/persians/shrine.xml"},
    "town_hall": {"britons": "structures/britons/civic_centre.xml", "romans": "structures/romans/civic_centre.xml", "persians": "structures/persians/civil_centre.xml"},
    "inn": "structures/{c}/house.xml:3", "walls": "structures/{c}/wall_tower.xml", "pier": "structures/{c}/dock.xml",
    "harbour": "structures/{c}/dock.xml", "lighthouse": "structures/{c}/scout_tower.xml", "guildhall": "structures/{c}/blacksmith.xml",
    "library": {"britons": "structures/britons/temple.xml", "romans": "structures/romans/temple.xml", "persians": "structures/persians/temple.xml"},
    "great_hall": {"britons": "structures/britons/barracks.xml", "romans": "structures/romans/sp_forumbuilding_1.xml", "persians": "structures/persians/hall.xml"},
    "wonder": {"britons": "structures/britons/wonder.xml", "romans": "structures/romans/sp_pantheon.xml", "persians": "structures/persians/babylonian_tower.xml"},
}


def spec(key, culture):
    v = PER.get(key) or SHARED[key]
    v = v[culture] if isinstance(v, dict) else v
    return v.replace("{c}", culture)


def main():
    repo, q = Path(sys.argv[1]), Path(sys.argv[2])
    raw, decoded, clean = q / "raw", q / "decoded", q / "clean"
    actors.configure(repo, raw)
    keys = list(PER) + list(SHARED)
    plan = {}
    for c in CULTURES:
        for k in keys:
            actor, _, var = spec(k, c).partition(":")
            plan[(c, k)] = (spec(k, c), actors.resolve(actor.replace("art/actors/", ""), int(var or 0)))
    subprocess.run([sys.executable, "-I", str(HERE / "intake3d.py"), str(raw), str(decoded), str(clean)], check=True)

    def cleanpath(rel):
        p = clean / rel
        return p if p.exists() else (clean / rel).with_suffix(".png")

    made, rows = [], []
    for (c, k), (src, parts) in plan.items():
        ps = [(cleanpath(p["dae"]), cleanpath(p["base"]), cleanpath(p["ao"]) if p["ao"] else None, p["matrix"]) for p in parts]
        try:
            im = render3d.render(ps, tilt_deg=TILT, azimuth_deg=AZIMUTH, px_per_m=PX_PER_M, ss=3)
        except ValueError:
            print("no usable parts:", c, k, src); continue
        im = render3d.with_shadow(im, dx=3, dy=4, blur=3, alpha=120)
        ox, oy = im.info["origin"]
        # Pad so the ground point sits exactly at the centre: the map anchors icons at their centre.
        w = int(2 * max(ox, im.width - ox)) + 2; h = int(2 * max(oy, im.height - oy)) + 2
        canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        canvas.alpha_composite(im, (int(w / 2 - ox), int(h / 2 - oy)))
        name = f"b3d_{c}_{k}"
        canvas.save(RES / f"{name}.png", optimize=True)
        made.append(name); rows.append(f"| `{name}.png` | `{src}` |")
        print("rendered", name, canvas.size)
    KT.write_text(
        "package world.wander.app.ui\n\nimport world.wander.app.R\n\n"
        "/** Settlement buildings rendered from 0 A.D.'s 3D models (generated by tools/render3d/build_buildings.py, 0.15). */\n"
        "object Buildings3d {\n"
        f"    /** Pixels per metre the pictures were rendered at; the map scales them to the ground. */\n    const val PX_PER_M = {PX_PER_M}f\n"
        f"    val CULTURES = listOf({', '.join(repr(c).replace(chr(39), chr(34)) for c in CULTURES)})\n"
        "    val images: Map<String, Int> = mapOf(\n" +
        "".join(f"        \"{n}\" to R.drawable.{n},\n" for n in sorted(made)) +
        "    )\n}\n")
    DOC.write_text("# Settlement buildings rendered from 0 A.D. (alpha 0.15)\n\n"
                   "Art from **0 A.D.** © Wildfire Games (https://play0ad.com), **CC BY-SA 3.0**. Each picture is a render of the "
                   "listed actor's 3D model with its own textures, made by `tools/render3d/build_buildings.py` "
                   f"(bird's-eye {TILT:.0f}°, {PX_PER_M:.0f} px per metre). Source: official mirror `github.com/0ad/0ad`. Meshes passed "
                   "`intake3d.py` (well-formed COLLADA, no DOCTYPE/ENTITY); DDS textures were decoded in quarantine; every texture "
                   "passed `tools/asset_intake.py`.\n\n| App file | Actor (under `binaries/data/mods/public/art/actors/`) |\n|---|---|\n"
                   + "\n".join(sorted(rows)) + "\n")
    print(f"{len(made)} buildings written")


if __name__ == "__main__":
    main()
