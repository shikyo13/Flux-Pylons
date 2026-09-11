"""Editable Minecraft geometry for the recovered Flux Pylons instrument identity.

Used by generate_textures.py so block halves and the inventory silhouette cannot
drift apart. Coordinates are in sixteenths of a block; every face has explicit UVs.
No geometry or pixels are extracted from the large concept PNGs.
"""
from copy import deepcopy
import math


def box(name, start, end, texture="steel", *, tint=None, full_uv=False, front_only=False):
    dx, dy, dz = (b - a for a, b in zip(start, end))
    faces = {}
    for face in ("north",) if front_only else ("down", "up", "north", "south", "west", "east"):
        width, height = (dx, dz) if face in ("up", "down") else (dx, dy) if face in ("north", "south") else (dz, dy)
        faces[face] = {"texture": "#" + texture,
                       "uv": [0, 0, 16 if full_uv else min(16, width), 16 if full_uv else min(16, height)]}
        if tint is not None:
            faces[face].update(tintindex=tint, neoforge_data={"block_light": 15, "sky_light": 15})
    result = {"name": name, "from": start, "to": end, "faces": faces}
    if tint is not None:
        result["shade"] = False
    return result


def ring(name, y, radius=5.2, thickness=0.7, texture="copper"):
    parts = []
    length = 2 * radius * math.tan(math.pi / 8) + 0.2
    for i in range(8):
        angle = i * math.pi / 4
        x, z = 8 + radius * math.sin(angle), 8 - radius * math.cos(angle)
        w, d = (thickness, length) if i % 2 == 0 and i % 4 == 2 else (length, thickness)
        part = box(f"{name} segment {i}",
                   [round(x - w / 2, 3), y, round(z - d / 2, 3)],
                   [round(x + w / 2, 3), y + thickness, round(z + d / 2, 3)], texture)
        if i % 2:
            part["rotation"] = {"origin": [round(x, 3), y + thickness / 2, round(z, 3)],
                                "axis": "y", "angle": -45 if i % 4 == 1 else 45}
        parts.append(part)
    return parts


def pylon_halves():
    bottom = [box("anchored steel foot", [0, 0, 0], [16, 2.5, 16]),
              box("copper foundation rim", [0.5, 2.5, 0.5], [15.5, 3.25, 15.5], "copper"),
              box("service housing", [2, 3.25, 2], [14, 7.5, 14]),
              box("inner power spine", [6, 7.5, 6], [10, 16, 10]),
              box("lower column", [7.4, 9, 4.9], [8.6, 16, 5.1], "energy", tint=0, full_uv=True)]
    for x in (1, 11.5):
        for z in (1, 11.5):
            bottom.append(box("raised corner anchorage", [x, 2.5, z], [x + 3.5, 7.75, z + 3.5]))
            bottom.append(box("corner copper clamp", [x + 1, 3, z + 1], [x + 2.5, 8.5, z + 2.5], "copper"))
    for z in (3.1, 11.7):
        for angle in (-45, 45):
            brace = box("cross-braced shaft", [7.45, 8.5, z], [8.55, 16, z + 1.2])
            brace["rotation"] = {"origin": [8, 12.25, z + 0.6], "axis": "z", "angle": angle}
            bottom.append(brace)
    for x in (3.1, 11.7):
        for angle in (-45, 45):
            brace = box("side shaft bracing", [x, 8.5, 7.45], [x + 1.2, 16, 8.55])
            brace["rotation"] = {"origin": [x + 0.6, 12.25, 8], "axis": "x", "angle": angle}
            bottom.append(brace)
    # Recessed service readouts repeat on opposite faces for placement readability.
    for z in (1.9, 14.02):
        panel = box("recessed service readout", [5, 4.25, z], [11, 6.7, z + 0.08], "energy", tint=0, full_uv=True)
        bottom.append(panel)
    top = [box("continuation spine", [6, 0, 6], [10, 4.75, 10]),
           box("copper induction base", [4, 4, 4], [12, 5.25, 12], "copper"),
           box("core pedestal", [6, 5.25, 6], [10, 7.5, 10]),
           box("contained energy column", [7.4, 0, 7.4], [8.6, 10, 8.6], "energy", tint=0, full_uv=True),
           box("contained energy core", [6.5, 8.8, 6.5], [9.5, 11.8, 9.5], "energy", tint=0, full_uv=True)]
    for x in (3.25, 11.65):
        for z in (3.25, 11.65):
            bottom.append(box("steel frame column", [x, 7, z], [x + 1.1, 16, z + 1.1]))
            top.append(box("steel containment post", [x, 0, z], [x + 1.1, 15.5, z + 1.1]))
    for y in (6, 9.8, 14):
        top.extend(ring("copper containment coil", y))
    top.extend(ring("steel crown cap", 15.1, radius=5.2, thickness=0.75, texture="steel"))
    return bottom, top


def gadget():
    parts = [box("worn instrument housing", [3, 1, 5.5], [13, 14, 9.5], "body", full_uv=True),
             box("rear access plate", [3.7, 2, 9.45], [12.3, 13, 9.75], "body", full_uv=True)]
    for x in (2.7, 12.5):
        parts.append(box("copper edge bumper", [x, 1.5, 5.1], [x + 0.8, 13.5, 9.8], "copper"))
    for y in (1.1, 13.2):
        parts.append(box("copper end bumper", [3.4, y, 5.1], [12.6, y + 0.65, 9.8], "copper"))
    parts.extend([
        box("dark recessed display bezel", [5.6, 5.5, 5.03], [11.9, 11.65, 5.52], "body"),
        box("network readout", [6, 5.9, 4.97], [11.5, 11.25, 5.05], "energy", tint=0, full_uv=True, front_only=True),
        # North faces read right-to-left in model X: the indicators are to the viewer's right.
        box("green upper status lamp", [3.9, 9.6, 4.95], [5, 10.95, 5.55], "indicator", tint=1, full_uv=True, front_only=True),
        box("red lower status lamp", [3.9, 7.7, 4.95], [5, 9.05, 5.55], "indicator", tint=2, full_uv=True, front_only=True),
        box("short antenna copper socket", [9.55, 13.9, 6.35], [11.65, 14.7, 8.45], "copper"),
        box("short antenna", [10.1, 14.6, 6.9], [11.1, 17.4, 7.9], "body"),
        box("antenna copper tip", [9.98, 17.1, 6.78], [11.22, 17.65, 8.02], "copper"),
        box("bottom connector", [6.5, 0.5, 6], [9.5, 2, 9], "body")])
    for y in (3, 3.75, 4.5):
        parts.append(box("service vent lip", [6, y, 5.04], [11.4, y + 0.18, 5.52], "body"))
    for x, y in ((3.9, 2.5), (11.7, 2.5), (3.9, 12.4), (11.7, 12.4)):
        parts.append(box("front fastener", [x, y, 5.01], [x + 0.45, y + 0.45, 5.53], "body"))
    return {"parent": "minecraft:block/block", "gui_light": "front",
            "textures": {"body": "quantumflux:item/gadget_body", "copper": "quantumflux:item/gadget_copper",
                         "energy": "quantumflux:item/gadget_screen_off", "indicator": "quantumflux:item/gadget_indicator",
                         "particle": "quantumflux:item/gadget_body"},
            "display": {
                "gui": {"rotation": [15, 205, 0], "translation": [0, -0.7, 0], "scale": [0.88, 0.88, 0.88]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
                "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                "thirdperson_righthand": {"rotation": [75, 15, 0], "translation": [0, 2, 0], "scale": [0.65, 0.65, 0.65]},
                "thirdperson_lefthand": {"rotation": [75, -15, 0], "translation": [0, 2, 0], "scale": [0.65, 0.65, 0.65]},
                "firstperson_righthand": {"rotation": [0, 155, 5], "translation": [-3, 5, 0], "scale": [0.5, 0.5, 0.5]},
                "firstperson_lefthand": {"rotation": [0, -155, -5], "translation": [-3, 5, 0], "scale": [0.5, 0.5, 0.5]}},
            "elements": parts}


def generated_models():
    lower, upper = pylon_halves()
    materials = {"steel": "quantumflux:block/pylon_steel", "copper": "quantumflux:block/pylon_copper",
                 "energy": "quantumflux:block/pylon_energy_off", "particle": "quantumflux:block/pylon_steel"}
    result = {}
    for name, elements in (("bottom", lower), ("top", upper)):
        result[f"block/quantum_pylon_{name}.json"] = {
            "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            # The world renderer supplies the animated orb. A baked cube at its
            # center occludes that translucent field; keep it only in the item.
            "textures": materials,
            "elements": [part for part in elements if part["name"] != "contained energy core"]}
        result[f"block/quantum_pylon_{name}_active.json"] = {
            "parent": f"quantumflux:block/quantum_pylon_{name}",
            "textures": {"energy": "quantumflux:block/pylon_energy"}}
    complete = deepcopy(lower + upper)
    for part in complete[len(lower):]:
        part["from"][1] += 16
        part["to"][1] += 16
        if "rotation" in part:
            part["rotation"]["origin"][1] += 16
    result["item/quantum_pylon.json"] = {
        "parent": "minecraft:block/block", "gui_light": "front", "render_type": "minecraft:cutout",
        "textures": {**materials, "energy": "quantumflux:item/gadget_energy"},
        "display": {
            "gui": {"rotation": [18, 225, 0], "translation": [0, -3.6, 0], "scale": [0.49, 0.49, 0.49]},
            "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -4, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.35, 0.35, 0.35]}},
        "elements": complete}
    result["item/quantum_gadget_body.json"] = gadget()
    result["item/quantum_gadget.json"] = {
        "parent": "quantumflux:item/quantum_gadget_body",
        "overrides": [{"predicate": {"quantumflux:active": 1}, "model": "quantumflux:item/quantum_gadget_active"}]}
    result["item/quantum_gadget_active.json"] = {
        "parent": "quantumflux:item/quantum_gadget_body", "textures": {"energy": "quantumflux:item/gadget_energy"}}
    return result
