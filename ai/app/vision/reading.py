"""OCR 결과에서 "저울이 지금 가리키는 값" 하나를 고른다.

## 왜 잘라내지 않는가

예전에는 저울 본체를 찾아 잘라내고, 그 안에서 표시창을 찾아 또 잘라낸 뒤에야 OCR 을
돌렸다. 현장 사진에서 그 전제가 무너졌다 — 산업용 저울은 표시부가 **본체와 떨어진
별도 헤드**(기둥 위나 옆)에 달려 있어서, 본체를 잘라내면 숫자가 함께 잘려 나간다.

전체 사진에 그대로 OCR 을 돌리면 읽힌다. 배경의 다른 숫자가 섞여 들어오지만, 아래
세 가지로 충분히 걸러진다.

## 무엇으로 고르는가

1. **단위가 붙어 있어야 한다.** 무게는 `kg` 이나 `g` 를 달고 있다. 창고 간판이나
   상자에 적힌 숫자는 대개 단위가 없다.
2. **TARE·MAX 같은 라벨이 붙은 값은 버린다.** 표시창에는 본값 말고도 공제중량과
   최대용량이 함께 적힌다. 그것을 집으면 화면은 "인식 성공" 이라 말하면서 0 g 을
   채운다 — 틀린 값을 자신 있게 보여주는 쪽이 못 읽었다고 말하는 것보다 나쁘다.
3. **가장 큰 글자를 고른다.** 저울은 지금 재고 있는 값을 가장 크게 보여준다.
   실제 사진 세 장에서 본값의 글자 높이는 85·117·131 이었고, 그 다음으로 큰 숫자
   (TARE 줄, MAX 표기)는 84·34 로 뚜렷이 작았다.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Iterable

import numpy as np

# **자기 숫자를 앞에 달고 다니는** 라벨만 넣는다.
#
# `NET`·`Zero`·`Stable` 은 지금 어떤 모드인지 알리는 표시등이라 옆에 자기 값이 없다.
# 그것까지 라벨로 보면 본값이 표시등 옆에 있다는 이유로 버려진다 — 실제로 18.7 kg 이
# 왼쪽 `NET` 표시등 때문에 걸러졌다.
ASIDE_LABELS = ("TARE", "MAX", "PCS", "TOTAL")

# 단위로 인정하는 글자. 소문자로 비교한다.
UNIT_TEXTS = ("kg", "g")

# 확대해 다시 읽은 글자가 이 비율(숫자 높이 대비) 이하로 작으면 7세그먼트 숫자가 아니다.
# 작게 찍힌 g 는 확대하면 대개 "9" 로 읽혔고 높이는 숫자의 0.40~0.48 배였다(실측 두 장).
# 숫자 9 라면 다른 숫자와 같은 높이(1.0 배)라 여유를 두고 0.6 배에서 자른다.
ZOOMED_UNIT_MAX_HEIGHT_RATIO = 0.6

# 옆 토큰으로 붙인 단위라도 이보다 확신이 낮으면 확대해 한 번 더 본다. 표시창의 작은 g 가 한
# 변형에서만 0.54 로 읽혀 값은 맞는데 "신뢰도 낮음" 으로 끝난 사진이 있었다.
WEAK_UNIT_CONFIDENCE = 0.9
_SMALL_G_LOOKALIKES = ("g", "9", "q")

# 숫자 토큰 하나의 오른쪽을 확대해 단위를 다시 읽는 함수. 못 읽으면 None.
ZoomUnitReader = Callable[["TextToken"], "tuple[str, float] | None"]


def unit_from_zoom(glyphs: Iterable[tuple[str, float, float]]) -> tuple[str, float] | None:
    """확대해 다시 읽은 글자들 (텍스트, 확신, 숫자 높이 대비 글자 높이) 에서 단위를 가려낸다.

    `g`·`kg` 는 그대로 단위다. 한 글자짜리 `9`·`q` 는 **작을 때만** g 로 본다 — 작게 찍힌 g 가
    확대하면 9 로 읽히는데, 같은 자리에 숫자 9 가 있었다면 다른 숫자만큼 컸을 것이다.
    여럿이면 확신이 가장 높은 것을 쓴다.
    """
    glyphs = list(glyphs)
    # kg 를 잘라 보면 g 만 읽힐 수 있다. k 가 함께 보이면 kg 다 — 1000 배 틀리지 않게.
    k_confidence = max(
        (confidence for text, confidence, _ratio in glyphs if text.strip().lower() == "k"),
        default=None,
    )
    best: tuple[str, float] | None = None
    for text, confidence, height_ratio in glyphs:
        lowered = text.strip().lower()
        looks_like_g = lowered == "g" or (
            lowered in _SMALL_G_LOOKALIKES and height_ratio <= ZOOMED_UNIT_MAX_HEIGHT_RATIO
        )
        if lowered == "kg":
            candidate = ("kg", confidence)
        elif looks_like_g and k_confidence is not None:
            candidate = ("kg", min(confidence, k_confidence))
        elif looks_like_g:
            candidate = ("g", confidence)
        else:
            continue
        if best is None or candidate[1] > best[1]:
            best = candidate
    return best


@dataclass(frozen=True)
class TextToken:
    """OCR 이 읽은 글자 한 덩어리와 그 자리."""

    text: str
    confidence: float
    left: float
    top: float
    right: float
    bottom: float

    @property
    def height(self) -> float:
        return self.bottom - self.top

    @property
    def is_unit(self) -> bool:
        return self.text.strip().lower() in UNIT_TEXTS

    def has_aside_label(self) -> bool:
        upper = self.text.upper()
        return any(label in upper for label in ASIDE_LABELS)


def tokens_from_ocr(result: Iterable) -> list[TextToken]:
    """rapidocr 의 (box, text, score) 목록을 좌표가 있는 토큰으로 바꾼다."""
    tokens: list[TextToken] = []
    for box, raw_text, raw_score in result or ():
        text = str(raw_text).strip()
        if not text:
            continue
        points = np.asarray(box, dtype=float)
        xs, ys = points[:, 0], points[:, 1]
        tokens.append(
            TextToken(
                text=text,
                confidence=float(raw_score),
                left=float(xs.min()),
                top=float(ys.min()),
                right=float(xs.max()),
                bottom=float(ys.max()),
            )
        )
    return tokens


def _overlaps_vertically(number: TextToken, unit: TextToken) -> bool:
    """단위가 숫자와 같은 줄에 있는가.

    표시창의 단위는 숫자 옆에 붙어 세로로 완전히 겹친다. 아래위 다른 줄의 `kg`
    (TARE 줄의 단위 같은 것)을 끌어오지 않으려면 이 조건이 필요하다.
    """
    overlap = min(number.bottom, unit.bottom) - max(number.top, unit.top)
    return overlap >= 0.8 * unit.height


def unit_beside(number: TextToken, tokens: Iterable[TextToken]) -> TextToken | None:
    """숫자 바로 오른쪽에 붙은 단위 토큰.

    실측한 간격은 숫자 높이의 -0.05 ~ 0.21 배였다(붙어 있거나 살짝 겹친다).

    **`g` 와 `kg` 가 둘 다 잡히면 큰 쪽을 택한다.** 표시등이 나란히 인쇄된 저울에서는
    둘 다 읽히는데, 잘못 고르면 1000 배가 어긋난다. 실측에서 점등된 주 단위가 더 크게
    찍혔다(kg 28 vs g 24). 근거가 이 한 장뿐이라 반례가 나오면 다시 봐야 한다.

    그래서 간격을 0.6 배까지 넉넉히 본다. 좁게 자르면 `g` 가 `kg` 보다 조금 앞에
    있다는 이유만으로 먼저 걸려 들어와, 높이로 가를 기회도 없이 1000 배 틀린 값이
    나간다. 세로 겹침과 "단위는 숫자보다 크지 않다" 가 이미 다른 줄을 막아 준다.
    """
    limit = 0.6 * number.height
    beside = [
        token
        for token in tokens
        if token.is_unit
        and token.height <= number.height
        and -limit <= token.left - number.right <= limit
        and _overlaps_vertically(number, token)
    ]
    if not beside:
        return None
    return max(beside, key=lambda token: (token.height, token.confidence))


def _labelled_aside(number: TextToken, tokens: Iterable[TextToken]) -> bool:
    """숫자 왼쪽 같은 줄에 TARE 같은 라벨이 있는가."""
    # 라벨은 자기 값 **바로 왼쪽**에 붙는다. 멀리 있는 것까지 끌어오면 한 화면에
    # TARE 가 있다는 이유만으로 본값이 함께 걸린다.
    reach = 2.0 * number.height
    for token in tokens:
        if not token.has_aside_label():
            continue
        if not _overlaps_vertically(number, token):
            continue
        if 0 <= number.left - token.right <= reach:
            return True
    return False


@dataclass(frozen=True)
class WeightReading:
    """고른 값 하나. 숫자와 단위가 따로 읽혔을 수 있어 둘을 함께 들고 다닌다."""

    text: str
    number_confidence: float
    unit_confidence: float
    height: float


def find_weight_readings(
    tokens: list[TextToken], zoom_unit: ZoomUnitReader | None = None
) -> list[WeightReading]:
    """단위가 붙은 무게 후보를 글자가 큰 순서로 돌려준다.

    `zoom_unit` 을 주면 옆에 단위 토큰이 없는 숫자는 그 오른쪽을 확대해 한 번 더 본다. 표시창의
    단위가 숫자보다 훨씬 작게 찍혀 전체 사진의 OCR 에서는 아예 읽히지 않는 저울이 있다.
    """
    readings: list[WeightReading] = []
    for token in tokens:
        if not any(character.isdigit() for character in token.text):
            continue
        if token.has_aside_label() or _labelled_aside(token, tokens):
            continue

        # 단위가 같은 덩어리로 읽힌 경우("0.0kg")와 따로 읽힌 경우("18.7" + "kg")가
        # 둘 다 나온다. 앞쪽은 그대로 쓰고, 뒤쪽은 옆 토큰을 붙여 준다.
        if token.is_unit:
            continue
        unit = unit_beside(token, tokens)
        already_has_unit = token.text.strip().lower().endswith(UNIT_TEXTS)
        weak_unit = unit is not None and unit.confidence < WEAK_UNIT_CONFIDENCE
        zoomed = (
            zoom_unit(token)
            if (unit is None or weak_unit) and not already_has_unit and zoom_unit is not None
            else None
        )
        # 작은 g 를 점으로 읽어 `31291.` 이 되는 일이 있다. 끝의 점은 무게에서 뜻이 없다.
        number = token.text.rstrip(".")
        if unit is not None:
            unit_confidence = unit.confidence
            # 약하게 읽힌 단위는 확대해서 같은 단위로 확인되면 그 확신을 쓴다. 다른 단위가 나오면
            # 옆에서 읽은 쪽을 믿는다 — 확대 영역에는 kg·g 표시등 같은 다른 글자가 섞일 수 있다.
            if zoomed is not None and zoomed[0] == unit.text.strip().lower():
                unit_confidence = max(unit_confidence, zoomed[1])
            text = f"{number}{unit.text}"
        elif zoomed is not None:
            text, unit_confidence = f"{number}{zoomed[0]}", zoomed[1]
        else:
            text, unit_confidence = token.text, token.confidence

        readings.append(
            WeightReading(
                text=text,
                number_confidence=token.confidence,
                unit_confidence=unit_confidence,
                height=token.height,
            )
        )

    readings.sort(key=lambda reading: (reading.height, reading.number_confidence), reverse=True)
    return readings
