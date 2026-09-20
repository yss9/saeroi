"""Generate OCR-oriented image variants for a detected scale display."""

from __future__ import annotations

import cv2
import numpy as np


def _order_points(points: np.ndarray) -> np.ndarray:
    ordered = np.zeros((4, 2), dtype=np.float32)
    coordinate_sum = points.sum(axis=1)
    coordinate_difference = np.diff(points, axis=1).ravel()
    ordered[0] = points[np.argmin(coordinate_sum)]
    ordered[2] = points[np.argmax(coordinate_sum)]
    ordered[1] = points[np.argmin(coordinate_difference)]
    ordered[3] = points[np.argmax(coordinate_difference)]
    return ordered


def _four_point_transform(image: np.ndarray, points: np.ndarray) -> np.ndarray:
    top_left, top_right, bottom_right, bottom_left = _order_points(points)
    width = round(
        max(
            np.linalg.norm(bottom_right - bottom_left),
            np.linalg.norm(top_right - top_left),
        )
    )
    height = round(
        max(
            np.linalg.norm(top_right - bottom_right),
            np.linalg.norm(top_left - bottom_left),
        )
    )
    if width < 2 or height < 2:
        return image

    destination = np.array(
        [[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]],
        dtype=np.float32,
    )
    transform = cv2.getPerspectiveTransform(
        np.asarray([top_left, top_right, bottom_right, bottom_left]), destination
    )
    return cv2.warpPerspective(image, transform, (width, height))


def _rectify_quadrilateral(image: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if image.ndim == 3 else image
    edges = cv2.Canny(cv2.GaussianBlur(gray, (5, 5), 0), 50, 150)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    minimum_area = image.shape[0] * image.shape[1] * 0.2
    for contour in sorted(contours, key=cv2.contourArea, reverse=True):
        if cv2.contourArea(contour) < minimum_area:
            break
        perimeter = cv2.arcLength(contour, True)
        approximation = cv2.approxPolyDP(contour, 0.02 * perimeter, True)
        if len(approximation) == 4 and cv2.isContourConvex(approximation):
            return _four_point_transform(image, approximation.reshape(4, 2))
    return image.copy()


def preprocess_variants(display_image: np.ndarray) -> list[tuple[str, np.ndarray]]:
    """Rectify a display and return five complementary OCR inputs."""
    if display_image.size == 0:
        raise ValueError("Display image is empty")

    original = _rectify_quadrilateral(display_image)
    gray = cv2.cvtColor(original, cv2.COLOR_BGR2GRAY) if original.ndim == 3 else original
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)

    smallest_side = min(gray.shape[:2])
    block_size = min(31, smallest_side if smallest_side % 2 else smallest_side - 1)
    block_size = max(3, block_size)
    adaptive = cv2.adaptiveThreshold(
        clahe,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        block_size,
        5,
    )
    adaptive_inverted = cv2.bitwise_not(adaptive)

    return [
        ("original", original),
        ("gray", gray),
        ("clahe", clahe),
        ("adaptive", adaptive),
        ("adaptive_inverted", adaptive_inverted),
    ]
