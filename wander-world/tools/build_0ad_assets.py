"""
Builds Wander World's map art from vetted 0 A.D. assets.

Input:  a folder of CLEAN images produced by tools/asset_intake.py (never raw downloads).
Output: app/src/main/res/drawable-nodpi/oad_*.png  and  docs/art/0AD_ASSETS.md (provenance list).

What it makes:
  - terrain patterns (256 px, seamless): grass, forest floor, dirt road, cobblestone, farmland, sand, snow
  - medallions: a 0 A.D. portrait cropped to a circle in a gold ring (settlement tiers, waypoints, places)
  - resource icons (for the economy phase)

0 A.D. art © Wildfire Games, CC BY-SA 3.0. The images produced here are adaptations (resized, cropped,
framed, recolored) and are therefore also CC BY-SA 3.0. See docs/security.md and the in-app Credits.

Run:  python3 -I tools/build_0ad_assets.py <clean_dir>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable-nodpi"
SRC_PREFIX = "binaries/data/mods/public/art/textures/"

TERRAIN = {   # our name -> (clean file, 0 A.D. source path)
    "ground_grass": ("terrain/grass_01.png", "terrain/types/temperate/grass_01.png"),
    "ground_meadow": ("terrain/grass_03.png", "terrain/types/temperate/grass_03.png"),
    "ground_forest": ("terrain/forestfloor_003.png", "terrain/types/temperate/forestfloor_003.png"),
    "ground_farm": ("terrain/farmland_01.png", "terrain/types/temperate/farmland_01.png"),
    "ground_sand": ("terrain/sand_01.png", "terrain/types/sahara/sand_01.png"),
    "ground_snow": ("terrain/snow_01.png", "terrain/types/alpine_arctic/snow_01.png"),
    "road_dirt": ("terrain/mud_01.png", "terrain/types/temperate/mud_01.png"),
    "road_cobble": ("terrain/paving_001.png", "terrain/types/temperate/paving_001.png"),
    # Charted water: the sea floor seen through clear water (tinted blue below), and wet sand for shorelines.
    "water_sea": ("terrain/aegean_ocean_coral_01.png", "terrain/types/aegean_anatolia/ocean_coral_01.png"),
    "ground_sand_wet": ("terrain/aegean_sand_01_wet.png", "terrain/types/aegean_anatolia/sand_01_wet.png"),
}

PORTRAIT = "ui/session/portraits/"
MEDALLIONS = {  # our name -> (clean file, source, options)
    "tier_0": ("structures/wall.png", PORTRAIT + "structures/wall.png", {"ruins": True}),
    "tier_1": ("gaia/special_settlement.png", PORTRAIT + "gaia/special_settlement.png", {}),
    "tier_2": ("structures/house.png", PORTRAIT + "structures/house.png", {}),
    "tier_3": ("structures/civic_centre.png", PORTRAIT + "structures/civic_centre.png", {}),
    "tier_4": ("structures/market.png", PORTRAIT + "structures/market.png", {}),
    "tier_5": ("structures/tower.png", PORTRAIT + "structures/tower.png", {}),
    "tier_6": ("structures/wonder.png", PORTRAIT + "structures/wonder.png", {}),
    "waypoint": ("structures/outpost.png", PORTRAIT + "structures/outpost.png", {"small": True}),
    "place_park": ("gaia/flora_tree_apple.png", PORTRAIT + "gaia/flora_tree_apple.png", {"small": True, "green": True}),
    "place_wood": ("gaia/flora_tree_pine.png", PORTRAIT + "gaia/flora_tree_pine.png", {"small": True, "green": True}),
    "place_store": ("structures/storehouse.png", PORTRAIT + "structures/storehouse.png", {"small": True}),
    "place_generic": ("structures/monument.png", PORTRAIT + "structures/monument.png", {"small": True}),
    # Points of interest (ring colour by family: gold civic, green nature, blue water, sand beach).
    "poi_government": ("structures/palace.png", PORTRAIT + "structures/palace.png", {"small": True}),
    "poi_emergency": ("structures/sentry_tower.png", PORTRAIT + "structures/sentry_tower.png", {"small": True}),
    "poi_civic": ("structures/stoa.png", PORTRAIT + "structures/stoa.png", {"small": True}),
    "poi_school": ("structures/gymnasium.png", PORTRAIT + "structures/gymnasium.png", {"small": True}),
    "poi_library": ("structures/library_scroll.png", PORTRAIT + "structures/library_scroll.png", {"small": True}),
    "poi_worship": ("structures/temple.png", PORTRAIT + "structures/temple.png", {"small": True}),
    "poi_culture": ("structures/theater.png", PORTRAIT + "structures/theater.png", {"small": True}),
    "poi_sports": ("structures/range.png", PORTRAIT + "structures/range.png", {"small": True}),
    "poi_transport": ("structures/corral.png", PORTRAIT + "structures/corral.png", {"small": True}),
    "poi_harbour": ("structures/dock.png", PORTRAIT + "structures/dock.png", {"small": True, "ring": "blue"}),
    "poi_lighthouse": ("structures/lighthouse.png", PORTRAIT + "structures/lighthouse.png", {"small": True, "ring": "blue"}),
    "poi_historic": ("structures/statue.png", PORTRAIT + "structures/statue.png", {"small": True}),
    "place_water": ("gaia/fauna_fish.png", PORTRAIT + "gaia/fauna_fish.png", {"small": True, "ring": "blue"}),
    "gold": ("gaia/special_treasure.png", PORTRAIT + "gaia/special_treasure.png", {"small": True}),
    "monument": ("gaia/special_obelisk.png", PORTRAIT + "gaia/special_obelisk.png", {}),
    "place_beach": ("gaia/flora_tree_date_palm.png", PORTRAIT + "gaia/flora_tree_date_palm.png", {"small": True, "ring": "sand"}),
}

RESOURCES = {n: (f"resources/{n}.png", f"ui/session/icons/resources/{n}.png") for n in ("food", "wood", "stone", "metal")}

GOLD = (196, 160, 82)
GOLD_DARK = (110, 80, 40)
GREEN = (98, 140, 76)
GREEN_DARK = (48, 78, 40)
RINGS = {"gold": (GOLD, GOLD_DARK), "green": (GREEN, GREEN_DARK),
         "blue": ((90, 140, 190), (30, 60, 100)), "sand": ((222, 196, 140), (130, 100, 60))}


def pattern(src: Path, size=256, lighten=1.0, warm=0.0) -> Image.Image:
    im = Image.open(src).convert("RGB")
    # Downscale the whole seamless tile so it stays seamless.
    im = im.resize((size, size), Image.LANCZOS)
    if lighten != 1.0:
        im = ImageEnhance.Brightness(im).enhance(lighten)
    if warm:
        im = Image.blend(im, Image.new("RGB", im.size, (214, 186, 140)), warm)
    return im


def medallion(src: Path, size: int, ruins=False, green=False, small=False, ring_name=None) -> Image.Image:
    ring = 10 if not small else 7
    inner = size - 2 * ring
    p = Image.open(src).convert("RGBA").resize((inner, inner), Image.LANCZOS)
    if ruins:   # weathered: desaturate, darken, mossy tint
        p = ImageEnhance.Color(p).enhance(0.25)
        p = Image.blend(p, Image.new("RGBA", p.size, (70, 100, 60, 255)), 0.25)
    mask = Image.new("L", (inner, inner), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, inner - 1, inner - 1], fill=255)
    out = Image.new("RGBA", (size + 8, size + 10), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    # Drop shadow, ring, portrait.
    shadow = Image.new("L", out.size, 0)
    ImageDraw.Draw(shadow).ellipse([5, 8, size + 5, size + 8], fill=150)
    out.putalpha(shadow.filter(ImageFilter.GaussianBlur(3)))
    out = Image.composite(Image.new("RGBA", out.size, (30, 22, 14, 255)), Image.new("RGBA", out.size, (0, 0, 0, 0)), out.getchannel("A"))
    d = ImageDraw.Draw(out)
    face, edge = RINGS[ring_name or ("green" if green else "gold")]
    d.ellipse([1, 1, size - 1, size - 1], fill=face, outline=edge, width=3)
    d.ellipse([ring - 2, ring - 2, size - ring + 1, size - ring + 1], outline=edge, width=2)
    out.paste(p, (ring, ring), mask)
    return out


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    clean = Path(sys.argv[1])
    RES.mkdir(parents=True, exist_ok=True)
    rows = []
    for name, (f, src) in TERRAIN.items():
        # Dirt roads are lightened toward sandy earth so they read against grass.
        lighten, warm = (1.45, 0.25) if name == "road_dirt" else (1.0, 0.0)
        img = pattern(clean / f, lighten=lighten, warm=warm)
        note = "resized to 256 px" + (", lightened and warmed" if warm else "")
        if name == "water_sea":   # sea floor under clear water: blue overlay, slightly brighter
            img = ImageEnhance.Brightness(Image.blend(img, Image.new("RGB", img.size, (58, 128, 170)), 0.55)).enhance(1.08)
            note += ", tinted blue as water"
        img.save(RES / f"oad_{name}.png", optimize=True)
        rows.append((f"oad_{name}.png", SRC_PREFIX + src, note))
    for name, (f, src, opts) in MEDALLIONS.items():
        size = 96 if opts.get("small") else 128
        medallion(clean / f, size, ruins=opts.get("ruins", False), green=opts.get("green", False), small=opts.get("small", False),
                  ring_name=opts.get("ring")) \
            .save(RES / f"oad_medal_{name}.png", optimize=True)
        rows.append((f"oad_medal_{name}.png", SRC_PREFIX + src, "cropped to a circle, framed" + (", weathered" if opts.get("ruins") else "")))
    for name, (f, src) in RESOURCES.items():
        Image.open(clean / f).convert("RGBA").save(RES / f"oad_res_{name}.png", optimize=True)
        rows.append((f"oad_res_{name}.png", SRC_PREFIX + src, "re-encoded"))

    doc = ROOT / "docs/art/0AD_ASSETS.md"
    lines = [
        "# 0 A.D. assets in Wander World",
        "",
        "Art from **0 A.D.** © Wildfire Games (https://play0ad.com), licensed **CC BY-SA 3.0**",
        "(https://creativecommons.org/licenses/by-sa/3.0/). Files below are adaptations of the listed originals",
        "(resized, cropped, framed or recolored) and are distributed under the same license.",
        "",
        "Source: official mirror `github.com/0ad/0ad`, branch `master`. Every file passed `tools/asset_intake.py`",
        "(PNG check, full decode, no trailing data, re-encoded from pixels) before `tools/build_0ad_assets.py` built these.",
        "",
        "| App file | Original (in the 0 A.D. repository) | Changes |",
        "|---|---|---|",
    ] + [f"| `{a}` | `{b}` | {c} |" for a, b, c in rows]
    doc.write_text("\n".join(lines) + "\n")
    print(f"wrote {len(rows)} files to {RES} and {doc}")


if __name__ == "__main__":
    main()
