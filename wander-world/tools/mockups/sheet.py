"""
Design: the Goal tab as a character sheet, achievements, and Settings in tabs (player, 2026-11-23).
  1 Character   wanderer, title and level, this week's quest (Mon–Sun active minutes), goal band, stats, week history
  2 Achievements  ladders with tiers (bronze/silver/gold hexes), progress to the next tier, rare-look rewards
  3 Settings    tabs: Tracking · Health · Map · Data · Credits (Credits shown)
The quest and band numbers are placeholders until the health-goal research is in. Invented names only.
Run:  python3 -I tools/mockups/sheet.py → docs/art/mockup_character_sheet.png
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import hexicons as HX  # noqa: E402
import shop as SH  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

OUT = SH.ROOT / "docs/art/mockup_character_sheet.png"
PW, PH = SH.PW, SH.PH
F, INK, SOFT = SH.F, SH.INK, SH.SOFT
GREEN = (70, 120, 60)


def bar(d, x, y, w, frac, h=14, color=(110, 82, 50), back=(226, 214, 190)):
    d.rounded_rectangle((x, y, x + w, y + h), h // 2, fill=back)
    d.rounded_rectangle((x, y, x + max(h, w * min(frac, 1)), y + h), h // 2, fill=color)


def tier_hex(size, tier):
    """A small achievement badge: bronze / silver / gold hex, or an empty one."""
    frames = ["bronze", "silver", "gold"]
    inner = Image.new("RGBA", (64, 64), (236, 226, 202, 255) if tier < 0 else (88, 66, 44, 255))
    if tier < 0:
        return HX.framed_hex(inner, size, ((170, 156, 130), (220, 208, 184)), width=3, shadow=False)
    return HX.framed_hex(inner, size, HX.FRAMES[frames[tier]], width=4, shadow=False)


def screen_character():
    S, d = SH.base()
    SH.window(d, (24, 90, PW - 24, PH - 60), "Character")
    # header: portrait, name, title, level
    S.alpha_composite(SH.img("sprite_wanderer_crimson", 140), (50, 140))
    d.text((220, 140), "The Wanderer", font=F(34), fill=INK)
    d.text((220, 186), "Rambler · Level 7", font=F(24), fill=(126, 96, 40))
    bar(d, 220, 226, 380, 0.62)
    d.text((220, 248), "62% to Level 8 · levels come from distance walked", font=F(18), fill=SOFT)
    S.alpha_composite(SH.img("banner_crimson", 70), (PW - 110, 140))
    # this week's quest
    SH.window(d, (50, 300, PW - 50, 560), "This week")
    d.text((76, 338), "128", font=F(64), fill=INK)
    d.text((200, 360), "/ 150 active minutes", font=F(28), fill=SOFT)
    bar(d, 76, 430, PW - 200, 128 / 150, h=22)
    for k, dn in enumerate("MTWTFSS"):   # day pips
        x = 76 + k * ((PW - 200) / 6.6)
        filled = k < 4
        d.ellipse((x, 468, x + 26, 494), fill=(126, 96, 40) if filled else (226, 214, 190), outline=SH.FRAME_DARK)
        d.text((x + 6, 498), dn, font=F(16), fill=SOFT)
    d.text((76, 526), "3 days left · 22 to go · your world holds if you make it", font=F(20), fill=GREEN)
    # band
    d.text((60, 590), "Goal band", font=F(26), fill=(91, 70, 54))
    for i, (nm, mins, on) in enumerate([("Stroller", 150, True), ("Rambler", 200, False), ("Trekker", 250, False), ("Pathfinder", 300, False)]):
        x = 60 + i * 152
        d.rounded_rectangle((x, 630, x + 140, 690), 12, fill=SH.FRAME_DARK if on else SH.PANEL, outline=SH.FRAME_DARK, width=2)
        d.text((x + 12, 636), nm, font=F(20), fill=(246, 230, 190) if on else INK)
        d.text((x + 12, 662), f"{mins} min", font=F(16), fill=(220, 200, 160) if on else SOFT)
    d.text((60, 700), "Moves up after 3 strong weeks out of 4; eases after 2 light weeks.", font=F(18), fill=SOFT)
    # stats
    d.text((60, 744), "Stats", font=F(26), fill=(91, 70, 54))
    stats = [("oad_res_food", "Endurance", "132 min / week"), ("legend_road_peek", "Cartographer", "141 roads"),
             ("oad_res_wood", "Explorer", "1,733 hexes"), ("oad_res_stone", "Forager", "61 gathered"),
             ("oad_medal_tier_1", "Settler", "1 settlement"), ("badge_capital", "Steadfast", "0 weeks in a row")]
    for i, (icon, name, val) in enumerate(stats):
        x = 60 + (i % 2) * 310
        y = 790 + (i // 2) * 86
        im = SH.img(icon)
        im.thumbnail((52, 52))
        S.alpha_composite(im, (x, y + 6))
        d.text((x + 66, y + 2), name, font=F(24), fill=INK)
        d.text((x + 66, y + 34), val, font=F(20), fill=SOFT)
    # week history
    d.text((60, 1060), "Weeks", font=F(26), fill=(91, 70, 54))
    weeks = [(92, "held"), (181, "held"), (128, "now")]
    base_y, top_y = 1290, 1110
    goal_y = base_y - (150 / 250) * (base_y - top_y)
    for i, (m, outcome) in enumerate(weeks):
        x = 90 + i * 130
        h = (m / 250) * (base_y - top_y)
        d.rectangle((x, base_y - h, x + 80, base_y), fill=(126, 96, 40) if outcome != "now" else (190, 160, 110))
        d.text((x + 4, base_y + 8), ["Nov 9", "Nov 16", "now"][i], font=F(18), fill=SOFT)
    d.line((70, goal_y, PW - 70, goal_y), fill=GREEN, width=3)
    d.text((PW - 230, goal_y - 30), "goal 150", font=F(18), fill=GREEN)
    return S


ACH = [  # name, tier reached (-1 none, 0 bronze, 1 silver, 2 gold), progress text, frac to next, reward
    ("Long road", 0, "14 / 50 km", 14 / 50, None),
    ("Cartographer", 1, "141 / 250 roads", 141 / 250, "Cartographer's hat at 250"),
    ("Discoverer", 0, "10 / 25 places", 10 / 25, None),
    ("Pathfinder", -1, "14 / 25 new trails", 14 / 25, "Pathfinder cloak"),
    ("Founder", -1, "1 / 3 settlements", 1 / 3, None),
    ("Tidewalker", -1, "0.4 / 10 km paddled", 0.04, "Tidewalker banner"),
    ("Steadfast", -1, "0 / 4 weeks over the goal in a row", 0, "Laurel outfit at 12"),
]


def screen_achievements():
    S, d = SH.base()
    SH.window(d, (24, 90, PW - 24, PH - 60), "Achievements")
    d.text((60, 140), "3 earned · 21 to go", font=F(22), fill=SOFT)
    y = 190
    for name, tier, prog, frac, reward in ACH:
        for k in range(3):  # three tier hexes
            S.alpha_composite(tier_hex(46, k if k <= tier else -1), (60 + k * 40, y + 4))
        d.text((200, y), name, font=F(26), fill=INK)
        d.text((200, y + 34), prog, font=F(20), fill=SOFT)
        bar(d, 200, y + 66, 300, frac)
        if reward:
            d.text((200, y + 88), "Reward: " + reward, font=F(18), fill=(126, 96, 40))
        y += 150
    return S


def screen_settings():
    S, d = SH.base()
    SH.window(d, (24, 90, PW - 24, PH - 60), "Settings")
    tabs = ["Tracking", "Health", "Map", "Data", "Credits"]
    x = 50
    for t in tabs:
        w = d.textlength(t, font=F(22)) + 28
        on = t == "Credits"
        d.rounded_rectangle((x, 140, x + w, 188), 10, fill=SH.FRAME_DARK if on else SH.PANEL, outline=SH.FRAME_DARK, width=2)
        d.text((x + 14, 150), t, font=F(22), fill=(246, 230, 190) if on else INK)
        x += w + 10
    y = 220
    for title, lines in [
        ("Art", ["Map textures, buildings and medallions adapted from", "0 A.D. by Wildfire Games, CC BY-SA 3.0.",
                 "Sprites, banners and outfits: original to this app."]),
        ("Map data", ["© OpenStreetMap contributors, ODbL.", "Fetched from the official Overpass API."]),
        ("Map tiles", ["OpenFreeMap; rendered with MapLibre (BSD)."]),
        ("Hexes", ["H3 by Uber (Apache 2.0)."]),
        ("Health", ["Health Connect by Google; your data stays on this phone."]),
    ]:
        SH.window(d, (50, y, PW - 50, y + 70 + 32 * len(lines)))
        d.text((76, y + 22), title, font=F(26), fill=INK)
        for i, ln in enumerate(lines):
            d.text((76, y + 60 + 32 * i), ln, font=F(20), fill=SOFT)
        y += 100 + 32 * len(lines)
    d.text((60, y + 10), "Full licences: tap a line to open its notice.", font=F(18), fill=SOFT)
    return S


def main():
    gap, top = 40, 110
    shots = [("1  Character", "Your wanderer, level, this week's quest, goal band, stats, weeks", screen_character()),
             ("2  Achievements", "Ladders with bronze, silver and gold; some reward rare looks", screen_achievements()),
             ("3  Settings in tabs", "Tracking · Health · Map · Data · Credits (Credits shown)", screen_settings())]
    sheet = Image.new("RGB", (len(shots) * (PW + gap) + gap, PH + top + 30), SH.PARCH)
    d = ImageDraw.Draw(sheet)
    for i, (t, sub, im) in enumerate(shots):
        x = gap + i * (PW + gap)
        d.text((x, 20), t, font=F(34), fill=(91, 70, 54))
        d.text((x, 66), sub, font=F(20), fill=SOFT)
        sheet.paste(im.convert("RGB"), (x, top))
        d.rounded_rectangle((x - 3, top - 3, x + PW + 3, top + PH + 3), 24, outline=(60, 50, 40), width=4)
    sheet.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
