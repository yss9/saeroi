"""Decode and reject images that are unsuitable for scale OCR."""

from __future__ import annotations

import cv2
import numpy as np


MIN_SHORT_SIDE = 480
MIN_LONG_SIDE = 640
MIN_BRIGHTNESS = 25.0
MAX_BRIGHTNESS = 235.0
MIN_LAPLACIAN_VARIANCE = 35.0
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def _is_supported_encoded_format(data: bytes) -> bool:
    is_png = data.startswith(PNG_SIGNATURE)
    is_jpeg = len(data) >= 4 and data[:3] == b"\xff\xd8\xff" and data[-2:] == b"\xff\xd9"
    return is_png or is_jpeg


def validate_image(data: bytes) -> np.ndarray:
    """Decode image bytes and validate size, exposure, and sharpness."""
    if not data:
        raise ValueError("Image data is empty")
    if not _is_supported_encoded_format(data):
        raise ValueError("Image must be encoded as JPEG or PNG")

    encoded = np.frombuffer(data, dtype=np.uint8)
    image = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("Image data cannot be decoded")

    height, width = image.shape[:2]
    if min(height, width) < MIN_SHORT_SIDE:
        raise ValueError("Image short side must be at least 480 pixels")
    if max(height, width) < MIN_LONG_SIDE:
        raise ValueError("Image long side must be at least 640 pixels")

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    brightness = float(gray.mean())
    if not MIN_BRIGHTNESS <= brightness <= MAX_BRIGHTNESS:
        raise ValueError("Image brightness is outside the supported range")

    sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    if sharpness < MIN_LAPLACIAN_VARIANCE:
        raise ValueError("Image is too blurred or flat")

    return image
