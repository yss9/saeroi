from __future__ import annotations

import cv2
import numpy as np
import pytest

from app.vision.image_validation import validate_image
from app.vision.preprocessing import preprocess_variants


def synthetic_scale_image(width: int = 800, height: int = 600) -> np.ndarray:
    image = np.full((height, width, 3), 205, dtype=np.uint8)
    cv2.rectangle(image, (120, 310), (680, 560), (45, 45, 45), -1)
    cv2.rectangle(image, (145, 335), (655, 535), (125, 125, 125), 5)
    cv2.rectangle(image, (235, 390), (565, 475), (232, 232, 232), -1)
    cv2.rectangle(image, (235, 390), (565, 475), (15, 15, 15), 5)
    cv2.putText(
        image,
        "2.35",
        (275, 452),
        cv2.FONT_HERSHEY_SIMPLEX,
        1.8,
        (15, 15, 15),
        5,
        cv2.LINE_AA,
    )
    return image


def encoded_image(image: np.ndarray, extension: str = ".png") -> bytes:
    ok, encoded = cv2.imencode(extension, image)
    assert ok
    return encoded.tobytes()


def test_validate_image_decodes_valid_800_by_600_image():
    decoded = validate_image(encoded_image(synthetic_scale_image()))

    assert decoded.shape == (600, 800, 3)
    assert decoded.dtype == np.uint8


@pytest.mark.parametrize("extension", [".webp", ".bmp", ".tiff"])
def test_validate_image_rejects_decodable_non_jpeg_png_formats(extension):
    with pytest.raises(ValueError, match="JPEG or PNG"):
        validate_image(encoded_image(synthetic_scale_image(), extension))


def test_validate_image_rejects_short_side_below_480():
    image = synthetic_scale_image(width=800, height=479)

    with pytest.raises(ValueError):
        validate_image(encoded_image(image))


def test_validate_image_rejects_long_side_below_640():
    image = synthetic_scale_image(width=500, height=500)

    with pytest.raises(ValueError):
        validate_image(encoded_image(image))


def test_validate_image_rejects_all_black_image():
    image = np.zeros((600, 800, 3), dtype=np.uint8)

    with pytest.raises(ValueError):
        validate_image(encoded_image(image))


def test_validate_image_rejects_flat_blurred_image():
    image = np.full((600, 800, 3), 128, dtype=np.uint8)

    with pytest.raises(ValueError):
        validate_image(encoded_image(image))


def test_preprocess_variants_returns_exactly_five_named_images():
    image = synthetic_scale_image()[390:476, 235:566]

    variants = preprocess_variants(image)

    assert [name for name, _ in variants] == [
        "original",
        "gray",
        "clahe",
        "adaptive",
        "adaptive_inverted",
    ]
    assert len(variants) == 5
    assert variants[0][1].ndim == 3
    assert all(variant.size > 0 for _, variant in variants)
    assert all(variant.dtype == np.uint8 for _, variant in variants)
