"""
Builds the hex map symbols (0.13, design: docs/art/mockup_hex_icons.png) from the app's existing art:
  hex_tier_N[_cap|_capmon].png   settlement hexes: medallion picture cut to a hex, frame by tier, pips, capital star
  hexframe_<state>.png           resource badge frames (best, near, new, gathered, thinned, plain): the icon sits on top
  cellart_<project>.png          a building's picture cut to a hex, drawn on its developed hex at close zoom
  bldg_hex_<project>.png         the same building as a framed hex badge on its ground, for further out
Art: 0 A.D. (CC BY-SA 3.0, Wildfire Games) and original sprites; outputs are derived from them under the same terms.
Run after gen_sprites.py and build_0ad_assets.py:   python3 -I tools/gen_hex_icons.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "mockups"))
import hexicons as HX  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

RES = HX.RES

# Each project's picture (cut to a hex) and the ground of its developed hex.
ART = {
    "campfire": "bldg_campfire", "well": "bldg_well", "fountain": "bldg_fountain", "bridge": "bldg_bridge",
    "meeting_hall": "oad_medal_bldg_meeting_hall", "town_hall": "oad_medal_bldg_town_hall", "market": "oad_medal_bldg_market",
    "market_hall": "oad_medal_bldg_market_hall", "inn": "oad_medal_bldg_inn", "pier": "oad_medal_bldg_pier",
    "lighthouse": "oad_medal_bldg_lighthouse", "walls": "oad_medal_bldg_walls", "great_hall": "oad_medal_bldg_great_hall",
    # 0.13 projects: nearest existing art until dedicated pictures are made
    "signpost": "medal_waystone", "forager_cache": "oad_medal_place_wood", "lean_to": "oad_medal_tier_1",
    "woodshed": "oad_medal_place_wood", "vegetable_patch": None, "mill": "oad_medal_poi_civic", "shrine": "oad_medal_poi_worship",
    "library": "oad_medal_poi_library", "guildhall": "oad_medal_poi_government", "harbour": "oad_medal_poi_harbour",
    "wonder_1": "oad_medal_monument", "wonder_2": "oad_medal_monument", "wonder_3": "oad_medal_tier_6", "wonder_4": "oad_medal_tier_6",
}
DIRT = ("oad_road_dirt", (1.0, 1.0))
COBBLE = ("oad_road_cobble", (1.0, 1.0))
FARM = ("oad_ground_farm", (1.0, 1.2))
GROUND = {k: DIRT for k in ["campfire", "signpost", "forager_cache", "lean_to", "meeting_hall", "woodshed"]}
GROUND.update({"vegetable_patch": FARM, "mill": FARM})


def cell_art(name, size=128):
    a = HX.medal_inner(name, 1.25 if name.startswith("oad_") else 1.45).resize((size, size), Image.LANCZOS)
    m = HX.hexmask(size, size / 2 - 2)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(a, (0, 0), m)
    return out


def frame(state, size=96):
    col, w, alpha, _ = HX.STATE[state] if state != "plain" else ((184, 154, 90), 4, 235, "")
    out = Image.new("RGBA", (size + 8, size + 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    c = (out.width / 2, out.height / 2)
    d.polygon(HX.hexpts(*c, size / 2 - 2), fill=(244, 236, 216, alpha))
    d.polygon(HX.hexpts(*c, size / 2 - 2), outline=col, width=w)
    return out


def main():
    n = 0
    star = HX.img("badge_capital")
    mon = HX.img("oad_medal_monument")
    for t in range(7):
        base = HX.settlement_hex(t, 112)
        base.save(RES / f"hex_tier_{t}.png", optimize=True)
        cap = HX.settlement_hex(t, 112, capital=True)
        cap.save(RES / f"hex_tier_{t}_cap.png", optimize=True)
        capmon = cap.copy()
        m = HX.medal_inner("oad_medal_monument").resize((40, 40), Image.LANCZOS)
        mm = HX.hexmask(40, 18)
        mc = Image.new("RGBA", (40, 40), (0, 0, 0, 0)); mc.paste(m, (0, 0), mm)
        capmon.alpha_composite(mc, (0, 0))
        capmon.save(RES / f"hex_tier_{t}_capmon.png", optimize=True)
        n += 3
    for st in ["best", "near", "new", "gathered", "thinned", "plain"]:
        frame(st).save(RES / f"hexframe_{st}.png", optimize=True); n += 1
    for pid, art in ART.items():
        if art:
            cell_art(art).save(RES / f"cellart_{pid}.png", optimize=True); n += 1
        badge = HX.developed_cell(pid, GROUND.get(pid, COBBLE), art, 96)
        badge.save(RES / f"bldg_hex_{pid}.png", optimize=True); n += 1
    del star, mon
    print(f"wrote {n} hex icons to {RES}")


if __name__ == "__main__":
    main()
