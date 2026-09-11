"""Build and validate the production texture set for Flux Pylons.

Runtime pylon, gadget, and effect PNG files are generated here. The
art uses 32 px gadget/effect pixels and 64 px pylon materials:
charcoal steel, warm copper, and neutral energy masks colored at runtime.

Usage:
    python tools/generate_textures.py          # write assets, prune obsolete files
    python tools/generate_textures.py --check  # pixel/metadata determinism + refs
"""

from __future__ import annotations

import argparse
from io import BytesIO
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw
from generate_models import generated_models


PROJECT = Path(__file__).resolve().parent.parent
ASSET_ROOT = PROJECT / "src/main/resources/assets/quantumflux"
TEXTURE_ROOT = ASSET_ROOT / "textures"
MODEL_ROOT = ASSET_ROOT / "models"
SIZE = 32
FRAMES = 16
TRANSPARENT = (0, 0, 0, 0)

STEEL_DARK = (28, 31, 37, 255)
STEEL = (43, 48, 56, 255)
STEEL_LIGHT = (67, 73, 82, 255)
STEEL_EDGE = (91, 96, 105, 255)
COPPER_DARK = (83, 43, 26, 255)
COPPER = (151, 82, 43, 255)
COPPER_LIGHT = (211, 132, 67, 255)
COPPER_HOT = (235, 169, 92, 255)


def noise(x: int, y: int, seed: int = 0) -> int:
    value = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return (value ^ (value >> 16)) & 0xFF


def clamp(value: float, low: int = 0, high: int = 255) -> int:
    return max(low, min(high, int(round(value))))


def png_bytes(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=False, compress_level=9)
    return output.getvalue()


def animation_bytes(frame_time: int = 3) -> bytes:
    payload = {
        "animation": {
            "frametime": frame_time,
            "interpolate": False,
            "frames": list(range(FRAMES)),
        }
    }
    return (json.dumps(payload, indent=2) + "\n").encode("utf-8")


def vertical_strip(frames: list[Image.Image]) -> Image.Image:
    result = Image.new("RGBA", (SIZE, SIZE * len(frames)), TRANSPARENT)
    for index, frame in enumerate(frames):
        result.paste(frame, (0, SIZE * index))
    return result


def steel_albedo(size: int = SIZE) -> Image.Image:
    image = Image.new("RGBA", (size, size), STEEL)
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            grain = (noise(x, y, 11) % 7) - 3
            base = (64 if size == 64 else 55) + grain + (1 if (x + y) % 5 == 0 else 0)
            pixels[x, y] = (base - 3, base, base + 7, 255)

    draw = ImageDraw.Draw(image)
    # Broad plate seams stay legible after mipmapping.
    for p in (0, size // 2 - 1, size // 2, size - 1):
        draw.line((p, 0, p, size - 1), fill=STEEL_DARK)
        draw.line((0, p, size - 1, p), fill=STEEL_DARK)
    draw.line((1, 1, 14, 1), fill=STEEL_LIGHT)
    draw.line((17, 17, 30, 17), fill=STEEL_LIGHT)
    draw.line((2, 28, 13, 17), fill=(52, 57, 66, 255))
    draw.line((18, 13, 29, 2), fill=(52, 57, 66, 255))
    for x, y in ((3, 3), (12, 3), (19, 19), (28, 19), (3, 28), (28, 3)):
        x, y = x * size // 32, y * size // 32
        image.putpixel((x, y), STEEL_EDGE)
        image.putpixel((x + 1, y + 1), STEEL_DARK)
    # Sparse scuffed edges and short cuts survive normal mipmapping; no blur.
    for y in range(2, size - 2):
        for x in range(2, size - 2):
            if noise(x, y, 78) > 251:
                draw.line((x, y, min(size - 2, x + 2), y), fill=(91, 93, 91, 255))
                draw.point((x, y + 1), fill=(25, 27, 29, 255))
    return image


def copper_albedo(size: int = SIZE) -> Image.Image:
    image = Image.new("RGBA", (size, size), COPPER)
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            grain = (noise(x, y, 29) % 13) - 6
            pixels[x, y] = (
                clamp(151 + grain), clamp(82 + grain * 0.65), clamp(43 + grain * 0.35), 255
            )
    draw = ImageDraw.Draw(image)
    for y in range(0, size, 8):
        draw.line((0, y, size - 1, y), fill=COPPER_DARK)
        if y + 1 < size:
            draw.line((0, y + 1, size - 1, y + 1), fill=COPPER_LIGHT)
    for x in range(7, size, 8):
        draw.line((x, 2, x, size - 2), fill=(112, 58, 31, 255))
    for x, y in ((4, 5), (12, 13), (20, 21), (28, 29), (27, 12), (11, 28)):
        image.putpixel((x, y), COPPER_HOT)
        image.putpixel(((x + 1) % SIZE, y), COPPER_DARK)
    for y in range(2, size - 2):
        for x in range(2, size - 2):
            if noise(x // 2, y // 2, 41) > 238:
                draw.point((x, y), fill=(80, 108, 91, 255))
            elif noise(x, y, 51) > 249:
                draw.point((x, y), fill=(180, 175, 151, 255))
    return image


def gadget_body_albedo() -> Image.Image:
    image = steel_albedo()
    draw = ImageDraw.Draw(image)
    draw.rectangle((4, 4, 27, 27), outline=STEEL_DARK)
    draw.rectangle((6, 6, 25, 25), outline=STEEL_LIGHT)
    draw.line((7, 24, 24, 7), fill=(35, 39, 47, 255))
    for x, y in ((7, 7), (24, 7), (7, 24), (24, 24)):
        draw.point((x, y), fill=STEEL_EDGE)
    for y in (23, 25, 27):
        draw.line((9, y, 22, y), fill=(15, 19, 22, 255))
        draw.line((9, y + 1, 22, y + 1), fill=(87, 90, 91, 255))
    return image


def screen_off_albedo() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (13, 15, 18, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 30, 30), outline=(42, 46, 53, 255))
    for y in range(5, 28, 5):
        draw.line((4, y, 27, y), fill=(22, 25, 29, 255))
    draw.rectangle((7, 12, 24, 19), fill=(18, 20, 24, 255), outline=(51, 55, 61, 255))
    return image


def neutral_gadget_energy() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (164, 164, 164, 220))
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 30, 30), outline=(235, 235, 235, 255), width=2)
    for y in range(5, 28, 5):
        draw.line((4, y, 27, y), fill=(198, 198, 198, 230))
    draw.rectangle((7, 11, 24, 20), fill=(91, 91, 91, 210), outline=(252, 252, 252, 255))
    draw.line((10, 16, 14, 16), fill=(255, 255, 255, 255), width=2)
    draw.line((17, 16, 22, 16), fill=(255, 255, 255, 255), width=2)
    return image


def indicator_mask() -> Image.Image:
    image = Image.new("RGBA", (32, 32), (23, 23, 23, 255))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((2, 2, 29, 29), 5, fill=(100, 100, 100, 255), outline=(49, 49, 49, 255), width=2)
    draw.rounded_rectangle((5, 5, 26, 26), 3, fill=(194, 194, 194, 255))
    draw.rectangle((8, 7, 17, 12), fill=(255, 255, 255, 255))
    return image


def upgrade_icon(kind: str) -> Image.Image:
    """Original 32-pixel cartridge silhouettes with readable, distinct glyphs."""
    image = Image.new("RGBA", (32, 32), TRANSPARENT)
    draw = ImageDraw.Draw(image)
    draw.polygon([(6, 2), (23, 2), (27, 6), (27, 27), (24, 30), (6, 30), (3, 27), (3, 5)],
                 fill=STEEL_DARK, outline=(15, 18, 22, 255))
    draw.rectangle((5, 5, 25, 27), fill=STEEL_LIGHT, outline=COPPER)
    draw.line((6, 5, 22, 5), fill=COPPER_HOT)
    draw.line((5, 6, 5, 25), fill=COPPER_LIGHT)
    draw.rectangle((8, 8, 22, 23), fill=(17, 31, 35, 255))
    for x in (9, 13, 17, 21):
        draw.rectangle((x, 27, x + 1, 29), fill=COPPER_HOT)
    for x, y in ((6, 7), (24, 7), (6, 25), (24, 25)):
        draw.point((x, y), fill=STEEL_EDGE)
    light = (125, 238, 234, 255)
    shade = (43, 147, 155, 255)
    if kind == "range":
        draw.line((15, 16, 15, 21), fill=light, width=2)
        draw.line((12, 21, 19, 21), fill=shade)
        draw.line([(10, 14), (12, 11), (18, 11), (21, 14)], fill=shade, width=1)
        draw.line([(12, 16), (13, 14), (17, 14), (19, 16)], fill=light, width=1)
    elif kind == "capacity":
        for x in (10, 15, 20):
            draw.rectangle((x, 10, x + 1, 13), fill=light)
            draw.rectangle((x, 18, x + 1, 21), fill=light)
        draw.line((10, 16, 21, 16), fill=shade)
        draw.line((15, 14, 15, 18), fill=light)
    elif kind == "throughput":
        draw.polygon([(15, 9), (11, 16), (15, 16), (13, 22), (21, 13), (16, 13), (19, 9)], fill=light)
        draw.point((16, 11), fill=(237, 255, 247, 255))
    elif kind == "buffer":
        draw.rectangle((10, 11, 20, 21), outline=light, width=1)
        draw.rectangle((13, 9, 17, 10), fill=light)
        for y in (13, 16, 19):
            draw.line((12, y, 18, y), fill=shade, width=2)
    else:
        raise ValueError(kind)
    return image


def transparent_energy() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)


def pylon_energy_frames() -> list[Image.Image]:
    frames: list[Image.Image] = []
    channels = (3, 10, 16, 22, 29)
    for frame in range(FRAMES):
        image = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
        draw = ImageDraw.Draw(image)
        pulse = (31 - frame * 2) % SIZE
        for index, x in enumerate(channels):
            # Thin neutral traces. Runtime tint supplies all hue.
            draw.line((x, 2, x, 29), fill=(108, 108, 108, 110))
            center = (pulse + index * 5) % SIZE
            for offset, alpha in ((-2, 95), (-1, 180), (0, 255), (1, 180), (2, 95)):
                y = (center + offset) % SIZE
                shade = 255 if offset == 0 else 218
                draw.point((x, y), fill=(shade, shade, shade, alpha))
                if x + 1 < SIZE and abs(offset) <= 1:
                    draw.point((x + 1, y), fill=(shade, shade, shade, alpha // 2))
        frames.append(image)
    return frames


def material_specular(
    albedo: Image.Image, *, smoothness: int, f0_or_metal: int, emissive: bool = False
) -> Image.Image:
    result = Image.new("RGBA", albedo.size, (smoothness, f0_or_metal, 0, 255))
    source = albedo.load()
    target = result.load()
    for y in range(albedo.height):
        for x in range(albedo.width):
            r, g, b, a = source[x, y]
            local_smooth = clamp(smoothness + (max(r, g, b) - min(r, g, b)) * 0.10, 0, 254)
            emission = clamp(90 + a * 0.58, 1, 254) if emissive and a else 255
            target[x, y] = (local_smooth, f0_or_metal, 0, emission)
    return result


def normal_from_albedo(albedo: Image.Image, strength: float = 1.15) -> Image.Image:
    rgba_image = albedo.convert("RGBA")
    source = rgba_image.load()
    width, height = rgba_image.size

    def height_at(x: int, y: int) -> float:
        r, g, b, a = source[x % width, y % height]
        if a == 0:
            return 0.5
        return (r * 0.2126 + g * 0.7152 + b * 0.0722) / 255.0

    result = Image.new("RGBA", rgba_image.size, (128, 128, 255, 255))
    target = result.load()
    for y in range(height):
        for x in range(width):
            if source[x, y][3] == 0:
                target[x, y] = (128, 128, 255, 255)
                continue
            nx = (height_at(x - 1, y) - height_at(x + 1, y)) * strength
            ny = (height_at(x, y - 1) - height_at(x, y + 1)) * strength
            nz = 1.0
            length = math.sqrt(nx * nx + ny * ny + nz * nz)
            nx, ny = nx / length, ny / length
            center = height_at(x, y)
            ao = clamp(238 + center * 17)
            height_value = clamp(190 + center * 65, 1, 255)
            target[x, y] = (clamp(128 + nx * 127), clamp(128 + ny * 127), ao, height_value)
    return result


def flat_normal(albedo: Image.Image) -> Image.Image:
    result = Image.new("RGBA", albedo.size, (128, 128, 255, 255))
    source = albedo.convert("RGBA").load()
    target = result.load()
    for y in range(albedo.height):
        for x in range(albedo.width):
            alpha = source[x, y][3]
            target[x, y] = (128, 128, 255, 224 if alpha else 255)
    return result


def beam_texture(core: bool) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
    pixels = image.load()
    center = (SIZE - 1) / 2
    # Geometry already defines a narrow world-space beam. A second hairline
    # alpha mask reduced the former core to a fraction of a screen pixel.
    sigma = 6.0 if core else 8.0
    ceiling = 255 if core else 230
    for y in range(SIZE):
        travel = 0.88 + 0.12 * math.sin((y / SIZE) * math.tau * 2)
        for x in range(SIZE):
            distance = abs(x - center)
            alpha = clamp(ceiling * math.exp(-(distance * distance) / (2 * sigma * sigma)) * travel)
            if alpha > 1:
                pixels[x, y] = (255, 255, 255, alpha)
    return image


def radial_effect(kind: str) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
    pixels = image.load()
    center = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            distance = math.hypot(x - center, y - center)
            if kind == "orb":
                alpha = clamp(255 * max(0.0, 1.0 - distance / 14.5) ** 0.8)
                value = clamp(192 + 63 * max(0.0, 1.0 - distance / 13.5))
            else:
                alpha = clamp(205 * max(0.0, 1.0 - abs(distance - 10.7) / 1.7))
                value = 255
            if alpha > 2:
                pixels[x, y] = (value, value, value, alpha)
    return image


def generated_assets() -> dict[Path, bytes]:
    steel = steel_albedo(64)
    copper = copper_albedo(64)
    gadget_body = gadget_body_albedo()
    screen_off = screen_off_albedo()
    gadget_energy = neutral_gadget_energy()
    energy_off = transparent_energy()
    energy_frames = pylon_energy_frames()
    energy_strip = vertical_strip(energy_frames)

    output: dict[Path, bytes] = {}

    def add(relative: str, image: Image.Image) -> None:
        output[TEXTURE_ROOT / relative] = png_bytes(image)

    # Pylon material library. Green values 230 and 234 are LabPBR iron/copper.
    add("block/pylon_steel.png", steel)
    add("block/pylon_steel_s.png", material_specular(steel, smoothness=128, f0_or_metal=230))
    add("block/pylon_steel_n.png", normal_from_albedo(steel, 1.05))
    add("block/pylon_copper.png", copper)
    add("block/pylon_copper_s.png", material_specular(copper, smoothness=176, f0_or_metal=234))
    add("block/pylon_copper_n.png", normal_from_albedo(copper, 1.25))

    add("block/pylon_energy_off.png", energy_off)
    add("block/pylon_energy_off_s.png", material_specular(energy_off, smoothness=220, f0_or_metal=12))
    add("block/pylon_energy_off_n.png", flat_normal(energy_off))
    add("block/pylon_energy.png", energy_strip)
    add(
        "block/pylon_energy_s.png",
        vertical_strip([material_specular(frame, smoothness=224, f0_or_metal=12, emissive=True) for frame in energy_frames]),
    )
    add("block/pylon_energy_n.png", vertical_strip([flat_normal(frame) for frame in energy_frames]))
    for suffix in ("", "_s", "_n"):
        output[TEXTURE_ROOT / f"block/pylon_energy{suffix}.png.mcmeta"] = animation_bytes()

    add("item/gadget_body.png", gadget_body)
    add("item/gadget_body_s.png", material_specular(gadget_body, smoothness=142, f0_or_metal=230))
    add("item/gadget_body_n.png", normal_from_albedo(gadget_body, 1.1))
    add("item/gadget_screen_off.png", screen_off)
    add("item/gadget_screen_off_s.png", material_specular(screen_off, smoothness=194, f0_or_metal=10))
    add("item/gadget_screen_off_n.png", normal_from_albedo(screen_off, 0.55))
    add("item/gadget_energy.png", gadget_energy)
    add("item/gadget_energy_s.png", material_specular(gadget_energy, smoothness=226, f0_or_metal=12, emissive=True))
    add("item/gadget_energy_n.png", flat_normal(gadget_energy))
    gadget_copper = copper_albedo()
    add("item/gadget_copper.png", gadget_copper)
    add("item/gadget_copper_s.png", material_specular(gadget_copper, smoothness=155, f0_or_metal=234))
    add("item/gadget_copper_n.png", normal_from_albedo(gadget_copper, 1.0))
    indicator = indicator_mask()
    add("item/gadget_indicator.png", indicator)
    add("item/gadget_indicator_s.png", material_specular(indicator, smoothness=205, f0_or_metal=12, emissive=True))
    add("item/gadget_indicator_n.png", flat_normal(indicator))
    for kind in ("range", "capacity", "throughput", "buffer"):
        add(f"item/{kind}_upgrade.png", upgrade_icon(kind))

    # Translucent block-entity-renderer sprites use vertex color and do not use
    # LabPBR companions. Keeping them neutral prevents baked color contamination.
    add("misc/beam_core.png", beam_texture(True))
    add("misc/beam_glow.png", beam_texture(False))
    add("misc/energy_orb.png", radial_effect("orb"))
    add("misc/energy_ring.png", radial_effect("ring"))
    for name, data in generated_models().items():
        output[MODEL_ROOT / name] = (json.dumps(data, indent=2) + "\n").encode("utf-8")
    return output


def managed_files() -> set[Path]:
    files: set[Path] = set()
    for directory in (TEXTURE_ROOT / "block", TEXTURE_ROOT / "item", TEXTURE_ROOT / "misc"):
        if not directory.exists():
            continue
        files.update(path for path in directory.rglob("*") if path.is_file() and (path.suffix == ".png" or path.name.endswith(".png.mcmeta")))
    return files


def default_face_uv(face_name: str, start: list[float], end: list[float]) -> list[float] | None:
    """Match BlockElement.uvsByFace for faces without an explicit UV rectangle."""
    if face_name == "down":
        return [start[0], 16.0 - end[2], end[0], 16.0 - start[2]]
    if face_name == "up":
        return [start[0], start[2], end[0], end[2]]
    if face_name == "north":
        return [16.0 - end[0], 16.0 - end[1], 16.0 - start[0], 16.0 - start[1]]
    if face_name == "south":
        return [start[0], 16.0 - end[1], end[0], 16.0 - start[1]]
    if face_name == "west":
        return [start[2], 16.0 - end[1], end[2], 16.0 - start[1]]
    if face_name == "east":
        return [16.0 - end[2], 16.0 - end[1], 16.0 - start[2], 16.0 - start[1]]
    return None


def validate_models() -> list[str]:
    errors: list[str] = []
    if not MODEL_ROOT.exists():
        return [f"missing model directory: {MODEL_ROOT}"]
    model_data: dict[Path, dict] = {}
    for model in sorted(MODEL_ROOT.rglob("*.json")):
        try:
            data = json.loads(model.read_text(encoding="utf-8"))
            model_data[model] = data
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"invalid model {model.relative_to(PROJECT)}: {exc}")
    if errors:
        return errors

    def local_parent(data: dict) -> Path | None:
        parent = data.get("parent")
        if isinstance(parent, str) and parent.startswith("quantumflux:"):
            namespace_path = parent.split(":", 1)[1]
            return MODEL_ROOT / f"{namespace_path}.json"
        return None

    def inherited_textures(model: Path, trail: tuple[Path, ...] = ()) -> dict[str, str]:
        if model in trail:
            errors.append(f"cyclic model parent: {model.relative_to(PROJECT)}")
            return {}
        data = model_data.get(model, {})
        textures: dict[str, str] = {}
        parent_path = local_parent(data)
        if parent_path is not None and parent_path in model_data:
            textures.update(inherited_textures(parent_path, trail + (model,)))
        textures.update(data.get("textures", {}))
        return textures

    for model, data in sorted(model_data.items()):
        if data.get("elements") and data.get("parent") in ("minecraft:item/generated", "minecraft:item/handheld"):
            errors.append(f"3D elements are discarded by the flat item generator: {model.relative_to(PROJECT)}")
        parent_path = local_parent(data)
        if parent_path is not None and parent_path not in model_data:
            errors.append(f"missing parent {data.get('parent')} referenced by {model.relative_to(PROJECT)}")

        available_textures = inherited_textures(model)
        for texture in data.get("textures", {}).values():
            if not isinstance(texture, str) or texture.startswith("#") or texture.startswith("minecraft:"):
                continue
            namespace, resource = texture.split(":", 1) if ":" in texture else ("minecraft", texture)
            if namespace != "quantumflux":
                continue
            texture_path = TEXTURE_ROOT / f"{resource}.png"
            if not texture_path.exists():
                errors.append(f"missing texture {texture} referenced by {model.relative_to(PROJECT)}")
        for override in data.get("overrides", []):
            override_model = override.get("model") if isinstance(override, dict) else None
            if isinstance(override_model, str) and override_model.startswith("quantumflux:"):
                target = MODEL_ROOT / f"{override_model.split(':', 1)[1]}.json"
                if target not in model_data:
                    errors.append(f"missing override {override_model} referenced by {model.relative_to(PROJECT)}")

        elements = data.get("elements", [])
        for index, element in enumerate(elements):
            start, end = element.get("from"), element.get("to")
            if not (
                isinstance(start, list) and isinstance(end, list) and len(start) == len(end) == 3
                and all(isinstance(value, (int, float)) for value in start + end)
            ):
                errors.append(f"invalid element bounds in {model.relative_to(PROJECT)} element {index}")
                continue
            if any(value < -16 or value > 32 for value in start + end) or any(a >= b for a, b in zip(start, end)):
                errors.append(f"out-of-range element bounds in {model.relative_to(PROJECT)} element {index}")
            for face_name, face in element.get("faces", {}).items():
                texture = face.get("texture") if isinstance(face, dict) else None
                if isinstance(texture, str) and texture.startswith("#") and texture[1:] not in available_textures:
                    errors.append(f"undefined texture {texture} on {face_name} in {model.relative_to(PROJECT)}")
                if not isinstance(face, dict):
                    errors.append(f"invalid {face_name} face in {model.relative_to(PROJECT)} element {index}")
                    continue
                uv = face.get("uv")
                if uv is None:
                    uv = default_face_uv(face_name, start, end)
                    if uv is None:
                        errors.append(f"unknown face {face_name} in {model.relative_to(PROJECT)} element {index}")
                        continue
                    uv_source = "default"
                else:
                    uv_source = "explicit"
                    if not (
                        isinstance(uv, list) and len(uv) == 4
                        and all(isinstance(value, (int, float)) for value in uv)
                    ):
                        errors.append(f"invalid explicit UV on {face_name} in {model.relative_to(PROJECT)} element {index}")
                        continue
                if any(value < 0 or value > 16 for value in uv):
                    errors.append(
                        f"{uv_source} UV outside [0,16] on {face_name} in "
                        f"{model.relative_to(PROJECT)} element {index}: {uv}"
                    )
                is_lit = isinstance(face, dict) and "neoforge_data" in face
                is_tinted = isinstance(face, dict) and "tintindex" in face
                if (is_lit or is_tinted) and texture not in ("#energy", "#indicator"):
                    errors.append(f"structural face is lit/tinted in {model.relative_to(PROJECT)} element {index}")

    forbidden_variants = tuple(MODEL_ROOT.glob("item/quantum_gadget_*.json"))
    allowed_variants = {MODEL_ROOT / "item/quantum_gadget_active.json", MODEL_ROOT / "item/quantum_gadget_body.json"}
    for variant in forbidden_variants:
        if variant not in allowed_variants:
            errors.append(f"obsolete gadget color model remains: {variant.relative_to(PROJECT)}")
    return errors


def validate_neutral_masks() -> list[str]:
    errors: list[str] = []
    for relative in ("block/pylon_energy.png", "item/gadget_energy.png", "item/gadget_indicator.png"):
        image = Image.open(TEXTURE_ROOT / relative).convert("RGBA")
        contaminated = False
        for y in range(image.height):
            for x in range(image.width):
                red, green, blue, alpha = image.getpixel((x, y))
                if alpha and not (red == green == blue):
                    errors.append(f"tint mask contains baked hue: {relative} pixel {x},{y}")
                    contaminated = True
                    break
            if contaminated:
                break
    return errors


def validate_labpbr() -> list[str]:
    errors: list[str] = []
    material_codes = {
        "block/pylon_steel": 230,
        "block/pylon_copper": 234,
        "block/pylon_energy_off": 12,
        "block/pylon_energy": 12,
        "item/gadget_body": 230,
        "item/gadget_screen_off": 10,
        "item/gadget_energy": 12,
        "item/gadget_copper": 234,
        "item/gadget_indicator": 12,
    }
    emissive = {"block/pylon_energy", "item/gadget_energy", "item/gadget_indicator"}
    for stem, expected_green in material_codes.items():
        albedo = Image.open(TEXTURE_ROOT / f"{stem}.png").convert("RGBA")
        specular = Image.open(TEXTURE_ROOT / f"{stem}_s.png").convert("RGBA")
        normal = Image.open(TEXTURE_ROOT / f"{stem}_n.png").convert("RGBA")
        if albedo.size != specular.size or albedo.size != normal.size:
            errors.append(f"LabPBR dimensions differ for {stem}")
            continue
        saw_emission = False
        for y in range(albedo.height):
            for x in range(albedo.width):
                spec_pixel = specular.getpixel((x, y))
                normal_pixel = normal.getpixel((x, y))
                if spec_pixel[1] != expected_green:
                    errors.append(f"invalid LabPBR material code for {stem} at {x},{y}")
                    break
                if spec_pixel[3] < 255:
                    saw_emission = True
                if normal_pixel[2] < 0 or normal_pixel[2] > 255 or normal_pixel[3] < 1:
                    errors.append(f"invalid LabPBR AO/height for {stem} at {x},{y}")
                    break
            if errors and errors[-1].startswith("invalid LabPBR"):
                break
        if stem in emissive and not saw_emission:
            errors.append(f"missing LabPBR emission for {stem}")
        if stem not in emissive and saw_emission:
            errors.append(f"unexpected LabPBR emission for {stem}")
    return errors


def asset_bytes_match(path: Path, actual: bytes, expected: bytes) -> bool:
    if actual == expected:
        return True
    if path.suffix != ".png":
        return False
    # PNG compression can differ between platforms without changing the image.
    try:
        with Image.open(BytesIO(actual)) as current, Image.open(BytesIO(expected)) as reference:
            return (
                current.format == reference.format == "PNG"
                and current.n_frames == reference.n_frames == 1
                and current.size == reference.size
                and current.mode == reference.mode
                and current.info == reference.info
                and current.tobytes() == reference.tobytes()
            )
    except (OSError, ValueError, SyntaxError):
        return False


def check_assets(expected: dict[Path, bytes]) -> list[str]:
    errors: list[str] = []
    for path, payload in expected.items():
        if not path.exists():
            errors.append(f"missing generated asset: {path.relative_to(PROJECT)}")
        elif not asset_bytes_match(path, path.read_bytes(), payload):
            errors.append(f"generated asset is stale: {path.relative_to(PROJECT)}")
    for path in sorted(managed_files() - set(expected)):
        errors.append(f"obsolete unmanaged texture: {path.relative_to(PROJECT)}")
    errors.extend(validate_models())
    if not errors:
        errors.extend(validate_neutral_masks())
        errors.extend(validate_labpbr())
    return errors


def write_assets(expected: dict[Path, bytes]) -> None:
    for path, payload in expected.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists() or path.read_bytes() != payload:
            path.write_bytes(payload)
    for path in sorted(managed_files() - set(expected)):
        path.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate without writing")
    args = parser.parse_args()
    expected = generated_assets()

    if not args.check:
        write_assets(expected)

    errors = check_assets(expected)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    png_count = sum(1 for path in expected if path.suffix == ".png")
    print(f"Flux Pylons assets valid: {png_count} PNGs and {len(expected) - png_count} model/animation files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
