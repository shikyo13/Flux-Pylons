from io import BytesIO
from pathlib import Path
import unittest

from PIL import Image
from PIL.PngImagePlugin import PngInfo

from generate_textures import asset_bytes_match


def encode(image: Image.Image, compression: int = 9, metadata: PngInfo | None = None) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", compress_level=compression, pnginfo=metadata)
    return output.getvalue()


class TextureComparisonTest(unittest.TestCase):
    def setUp(self) -> None:
        self.path = Path("texture.png")
        self.image = Image.new("RGBA", (4, 4), (40, 70, 100, 255))
        self.expected = encode(self.image)

    def test_same_pixels_with_different_compression(self) -> None:
        actual = encode(self.image, compression=0)
        self.assertNotEqual(actual, self.expected)
        self.assertTrue(asset_bytes_match(self.path, actual, self.expected))

    def test_changed_color_or_alpha_is_rejected(self) -> None:
        for pixel in [(41, 70, 100, 255), (40, 70, 100, 254)]:
            with self.subTest(pixel=pixel):
                changed = self.image.copy()
                changed.putpixel((1, 1), pixel)
                self.assertFalse(asset_bytes_match(self.path, encode(changed), self.expected))

    def test_changed_dimensions_are_rejected(self) -> None:
        changed = Image.new("RGBA", (2, 8), (40, 70, 100, 255))
        self.assertFalse(asset_bytes_match(self.path, encode(changed), self.expected))

    def test_changed_pixel_mode_is_rejected(self) -> None:
        self.assertFalse(asset_bytes_match(self.path, encode(self.image.convert("RGB")), self.expected))

    def test_changed_metadata_is_rejected(self) -> None:
        metadata = PngInfo()
        metadata.add_text("Description", "Changed metadata")
        self.assertFalse(asset_bytes_match(self.path, encode(self.image, metadata=metadata), self.expected))

    def test_invalid_png_is_rejected(self) -> None:
        self.assertFalse(asset_bytes_match(self.path, b"not a PNG", self.expected))

    def test_animation_metadata_still_requires_identical_bytes(self) -> None:
        path = Path("texture.png.mcmeta")
        expected = b'{"animation":{"frametime":3}}'
        self.assertTrue(asset_bytes_match(path, expected, expected))
        self.assertFalse(asset_bytes_match(path, b'{"animation":{"frametime":4}}', expected))


if __name__ == "__main__":
    unittest.main()
