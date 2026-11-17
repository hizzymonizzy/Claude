"""
Builds the map legend: swatches for each road stage and ground type, drawn from the same textures the map
uses, plus a full legend sheet for design review (docs/art/legend.png).

Swatches built from 0 A.D. textures are named oad_legend_* (CC BY-SA 3.0, Wildfire Games); the rest are original.
Run after tools/build_0ad_assets.py and tools/gen_sprites.py:   python3 -I tools/gen_legend.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable-nodpi"
W, H = 240, 64   # swatch size (drawn at 2x for crisp downscale on screen)


def tile(name, size):
    t = Image.open(RES / f"{name}.png").convert("RGB").resize((128, 128), Image.LANCZOS)
    out = Image.new("RGB", size)
    for x in range(0, size[0], 128):
        for y in range(0, size[1], 128):
            out.paste(t, (x, y))
    return out


def textured_line(base, texture, width, y, dash=None, edge=None, edge_extra=0):
    w, h = base.size
    mask = Image.new("L", base.size, 0)
    md = ImageDraw.Draw(mask)
    if edge:
        ImageDraw.Draw(base).line([(14, y), (w - 14, y)], fill=edge, width=width + edge_extra)
    if dash:
        x = 14
        while x < w - 14:
            md.line([(x, y), (min(x + dash, w - 14), y)], fill=255, width=width)
            x += dash * 2
    else:
        md.line([(14, y), (w - 14, y)], fill=255, width=width)
    tex = texture if isinstance(texture, Image.Image) else Image.new("RGB", base.size, texture)
    base.paste(tex, (0, 0), mask)
    return base


def roads():
    dirt = tile("oad_road_dirt", (W, H))
    cobble = tile("oad_road_cobble", (W, H))
    grass = lambda: tile("oad_ground_grass", (W, H))
    y = H // 2
    out = {
        "oad_legend_road_1": textured_line(grass(), (226, 205, 160), 5, y, dash=10),
        "oad_legend_road_2": textured_line(grass(), dirt, 7, y),
        "oad_legend_road_3": textured_line(grass(), dirt, 11, y, edge=(74, 58, 40), edge_extra=5),
        "oad_legend_road_4": textured_line(grass(), cobble, 13, y, edge=(94, 90, 82), edge_extra=6),
    }
    av = textured_line(grass(), cobble, 17, y, edge=(94, 90, 82), edge_extra=6)
    d = ImageDraw.Draw(av)
    for x in range(20, W - 14, 22):        # tree-lined avenue
        for yy in (y - 19, y + 19):
            d.ellipse([x - 6, yy - 6, x + 6, yy + 6], fill=(58, 104, 50), outline=(36, 66, 30), width=2)
            d.ellipse([x - 3, yy - 4, x + 1, yy], fill=(96, 146, 70))
    out["oad_legend_road_5"] = av
    # Your own paths, five stages.
    sandtex = tile("oad_ground_sand", (W, H))
    out["legend_wild_1"] = textured_line(grass(), (242, 232, 204), 3, y, dash=3)
    out["legend_wild_2"] = textured_line(grass(), (230, 211, 168), 5, y, dash=9)
    out["oad_legend_wild_3"] = textured_line(grass(), dirt, 6, y)
    out["oad_legend_wild_4"] = textured_line(grass(), sandtex, 7, y, edge=(110, 86, 56), edge_extra=4)
    w5 = textured_line(grass(), sandtex, 8, y, edge=(110, 86, 56), edge_extra=4)
    d5 = ImageDraw.Draw(w5)
    for x in range(22, W - 14, 30):           # cairns
        for yy in (y - 13, y + 13):
            d5.ellipse([x - 4, yy - 4, x + 4, yy + 4], fill=(141, 138, 132), outline=(80, 78, 74))
    out["oad_legend_wild_5"] = w5
    # Water routes, five stages, on water.
    water = lambda: tile("water_ripple", (W, H))
    out["legend_water_1"] = textured_line(water(), (228, 243, 249), 3, y, dash=4)
    out["legend_water_2"] = textured_line(water(), (203, 230, 242), 4, y, dash=10)
    out["legend_water_3"] = textured_line(water(), (166, 211, 232), 5, y, edge=(62, 127, 163), edge_extra=4)
    w4 = textured_line(water(), (166, 211, 232), 6, y, edge=(62, 127, 163), edge_extra=4)
    for x in range(22, W - 14, 34):
        ImageDraw.Draw(w4).ellipse([x - 4, y + 12, x + 4, y + 20], fill=(232, 131, 58), outline=(120, 60, 20))
    out["legend_water_4"] = w4
    w5w = textured_line(water(), (166, 211, 232), 9, y, edge=(62, 127, 163), edge_extra=5)
    for x in range(22, W - 14, 34):
        for yy in (y - 16, y + 16):
            ImageDraw.Draw(w5w).ellipse([x - 4, yy - 4, x + 4, yy + 4], fill=(232, 131, 58), outline=(120, 60, 20))
    out["legend_water_5"] = w5w
    peek = Image.new("RGB", (W, H), (233, 223, 199))
    pd = ImageDraw.Draw(peek)
    for x in range(14, W - 14, 14):
        pd.line([(x, y), (x + 6, y)], fill=(160, 140, 115), width=4)
    out["legend_road_peek"] = peek
    # The capital medallion as the map draws it: star upper right, monument upper left (see MapScreen.capitalMedal).
    medal = Image.open(RES / "oad_medal_tier_2.png").convert("RGBA")
    star = Image.open(RES / "badge_capital.png").convert("RGBA")
    mon = Image.open(RES / "oad_medal_monument.png").convert("RGBA")
    pad = 26
    cap = Image.new("RGBA", (medal.width + 2 * pad, medal.height + 2 * pad), (0, 0, 0, 0))
    cap.alpha_composite(medal, (pad, pad))
    sz = int(medal.width * 0.42)
    cap.alpha_composite(mon.resize((sz, sz * mon.height // mon.width), Image.LANCZOS), (2, 2))
    cap.alpha_composite(star.resize((sz, sz), Image.LANCZOS), (cap.width - sz - 2, 2))
    out["oad_legend_capital"] = cap
    # A resource pointer waiting at the edge of the map.
    ptr = Image.new("RGBA", (120, 120), (0, 0, 0, 0))
    pdr = ImageDraw.Draw(ptr)
    pdr.ellipse([22, 22, 98, 98], fill=(244, 236, 216, 235), outline=(138, 106, 58), width=5)
    wood = Image.open(RES / "oad_res_wood.png").convert("RGBA").resize((52, 52), Image.LANCZOS)
    ptr.alpha_composite(wood, (34, 34))
    pdr.polygon([(104, 46), (118, 60), (104, 74)], fill=(91, 70, 54))
    out["oad_legend_pointer"] = ptr
    g = Image.new("RGBA", (120, 120), (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)
    gd.ellipse([20, 20, 100, 100], fill=(244, 236, 216, 220), outline=(63, 157, 74), width=8)
    g.alpha_composite(Image.open(RES / "oad_res_food.png").convert("RGBA").resize((52, 52), Image.LANCZOS), (34, 34))
    out["legend_gathering"] = g
    out["legend_ground_fog"] = Image.open(RES / "fog_parchment.png").convert("RGB").resize((W, H))
    out["oad_legend_ground_explored"] = grass()
    out["oad_legend_ground_woods"] = tile("oad_ground_forest", (W, H))
    out["oad_legend_ground_sand"] = tile("oad_ground_sand", (W, H))
    out["oad_legend_ground_water"] = tile("oad_water_sea", (W, H))
    out["oad_legend_ground_shore"] = tile("oad_ground_sand_wet", (W, H))
    return out


# (image, title, description) — the legend, in display order.
LEGEND = [
    ("Ground", None, None),
    ("legend_ground_fog", "Fog", "Unexplored. Walk to clear it."),
    ("oad_legend_ground_explored", "Explored ground", "Cleared by your walks; worn roads clear wider."),
    ("oad_legend_ground_woods", "Woods & parks", "Named green places you've discovered."),
    ("oad_legend_ground_sand", "Sand & beaches", "Beaches and dunes you've discovered."),
    ("oad_legend_ground_water", "Water", "Clear and blue where you've been; faded under the fog."),
    ("oad_legend_ground_shore", "Shore", "Wet sand along the coast. Walk it for food, like roads for stone."),
    ("Roads", None, None),
    ("oad_legend_road_1", "Trail", "Walked on 1 day."),
    ("oad_legend_road_2", "Footpath", "3 days."),
    ("oad_legend_road_3", "Lane", "10 days."),
    ("oad_legend_road_4", "Cobbled road", "30 days."),
    ("oad_legend_road_5", "Avenue", "100 days. Tree-lined."),
    ("legend_road_peek", "Unwalked street", "Inside explored ground every street shows its status; dashed = not walked yet."),
    ("Your own paths", None, None),
    ("legend_wild_1", "Faint track", "Off the map's roads, 1 day."),
    ("legend_wild_2", "Trodden path", "3 days."),
    ("oad_legend_wild_3", "Dirt track", "10 days."),
    ("oad_legend_wild_4", "Gravel trail", "30 days."),
    ("oad_legend_wild_5", "Ranger trail", "100 days. Cairns mark the way."),
    ("Water routes", None, None),
    ("legend_water_1", "Wake", "Paddled on 1 day."),
    ("legend_water_2", "Paddle line", "3 days."),
    ("legend_water_3", "Paddle route", "10 days."),
    ("legend_water_4", "Marked route", "30 days. Buoys."),
    ("legend_water_5", "Waterway", "100 days."),
    ("Settlements", None, None),
    ("oad_medal_tier_1", "Camp", "3 visit-days in a month."),
    ("oad_medal_tier_2", "Hamlet", "10 visit-days."),
    ("oad_medal_tier_3", "Village", "25 visit-days."),
    ("oad_medal_tier_4", "Town", "60 visit-days."),
    ("oad_medal_tier_5", "City", "150 visit-days."),
    ("oad_medal_tier_6", "Metropolis", "Capital only, 400."),
    ("oad_medal_tier_0", "Ruins", "Faded from neglect; never lost."),
    ("oad_legend_capital", "Capital", "Your home: star badge; an obelisk once you raise a monument."),
    ("Markers", None, None),
    ("oad_medal_waypoint", "Waypoint", "Any area you've walked in."),
    ("medal_waystone", "Waystone", "Where you set off on foot after a drive, away from home and known ground."),
    ("medal_waystone_great", "Great waystone", "Arrival in a new region; links regions."),
    ("Places", None, None),
    ("oad_medal_place_park", "Park", "Green space."),
    ("oad_medal_place_wood", "Woods", "Woodland, reserves, wetland."),
    ("oad_medal_place_beach", "Beach", "Sand by the water."),
    ("oad_medal_place_water", "Lake or river", "Named water."),
    ("medal_poi_hospital", "Hospital", "Hospitals and clinics."),
    ("oad_medal_poi_government", "Government", "Town hall, courthouse, offices."),
    ("oad_medal_poi_emergency", "Police & fire", "Stations."),
    ("oad_medal_poi_civic", "Civic", "Post office, community centre, market."),
    ("oad_medal_poi_school", "School", "Schools, colleges, universities."),
    ("oad_medal_poi_library", "Library", ""),
    ("oad_medal_poi_worship", "Place of worship", ""),
    ("oad_medal_poi_culture", "Culture", "Museums, theatres, cinemas."),
    ("oad_medal_poi_sports", "Sports", "Stadiums and sports centres."),
    ("oad_medal_poi_transport", "Station", "Train, bus and ferry."),
    ("oad_medal_poi_harbour", "Harbour", "Marinas."),
    ("oad_medal_poi_lighthouse", "Lighthouse", ""),
    ("oad_medal_poi_historic", "Historic", "Monuments, memorials, ruins."),
    ("oad_medal_place_store", "Store", "A big store you've walked through."),
    ("oad_medal_place_generic", "Place", "Other named places."),
    ("Economy", None, None),
    ("oad_medal_gold", "Gold", "Your weekly-goal effort and distance. One purse, usable everywhere."),
    ("oad_res_wood", "Wood", "30 s in or along (15 m) a wood or park. New ones pay double; favourites thin, rest restores."),
    ("oad_res_food", "Food", "Walk shores and field or garden edges (2 per km), forage in woods, paddle, visit markets and harbours."),
    ("oad_res_stone", "Stone", "Kilometres walked on roads; points to streets you haven't walked."),
    ("oad_res_metal", "Metal", "Discovering and revisiting places; points to undiscovered ones."),
    ("oad_medal_monument", "Monument", "A milestone, raised at your capital."),
    ("oad_legend_pointer", "Best source", "The arrow points to the best node you haven't gathered today: fresh beats thinned."),
    ("legend_gathering", "Gathering", "Nodes stay put; tap one for details. Green ring: you're in its zone. 30 s gathers; a tick = gathered today. Resources bank in the area you gather them; roads you've walked join banks."),
    ("You", None, None),
    ("sprite_wanderer", "Walking", ""),
    ("sprite_wanderer_paddle", "Paddling", ""),
]


def sheet(swatches):
    font = ImageFont.load_default(size=22)
    small = ImageFont.load_default(size=17)
    head = ImageFont.load_default(size=28)
    rows = len(LEGEND)
    S = Image.new("RGB", (900, 70 * rows + 40), (244, 236, 216))
    d = ImageDraw.Draw(S)
    y = 20
    for key, title, desc in LEGEND:
        if title is None:
            d.text((24, y + 18), key, fill=(91, 70, 54), font=head)
            y += 70
            continue
        img = swatches.get(key) or Image.open(RES / f"{key}.png").convert("RGBA")
        if img.width > img.height * 2:
            img = img.resize((180, 48))
        else:
            img.thumbnail((56, 56))
        S.paste(img, (24 + (180 - img.width) // 2, y + (60 - img.height) // 2), img if img.mode == "RGBA" else None)
        d.text((230, y + 8), title, fill=(40, 30, 20), font=font)
        d.text((230, y + 36), desc, fill=(110, 90, 70), font=small)
        y += 70
    return S


def main():
    sw = roads()
    for name, img in sw.items():
        img.save(RES / f"{name}.png", optimize=True)
    out = ROOT / "docs/art/legend.png"
    sheet(sw).save(out)
    print(f"wrote {len(sw)} swatches and {out}")


if __name__ == "__main__":
    main()
