from __future__ import annotations

import re
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP

from app.vision.schemas import ParsedWeight


WEIGHT_TOKEN = re.compile(
    r"(?<![0-9A-Za-z.|,+-])"
    r"(?P<value>[0-9OoIl|SsB]+(?:\.[0-9OoIl|SsB]+)?)"
    r"(?P<space>\s*)"
    r"(?P<unit>(?i:kg|g))(?![0-9A-Za-z.|])",
)
CORRECTIONS = str.maketrans({"O": "0", "o": "0", "I": "1", "l": "1", "|": "1", "S": "5", "s": "5", "B": "8"})


def parse_weight(text: str) -> ParsedWeight | None:
    """Parse the first complete OCR weight token in an OCR line into grams."""
    for match in WEIGHT_TOKEN.finditer(text):
        normalized_value = match.group("value").translate(CORRECTIONS)
        try:
            value = Decimal(normalized_value)
        except InvalidOperation:
            continue

        gram = (value * (1000 if match.group("unit").lower() == "kg" else 1)).quantize(
            Decimal("1"), rounding=ROUND_HALF_UP
        )
        if not 1 <= gram <= 100_000:
            continue

        return ParsedWeight(
            gram=int(gram),
            raw_text=match.group(0),
            normalized_text=f"{normalized_value}{match.group('space')}{match.group('unit')}",
        )
    return None
