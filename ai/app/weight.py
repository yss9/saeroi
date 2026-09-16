"""읽어낸 줄들에서 '이게 그 중량이다' 를 골라낸다.

OCR 이 잘 읽어도 여기서 틀리면 소용이 없다. 계근표에는 숫자가 잔뜩 있고, 그중 하나만
우리가 찾는 값이다.

    차량번호  12가3456     ← 숫자지만 아니다
    일자      2026-09-10   ← 아니다
    총중량     19.92 kg    ← 이것
    공차중량    2.10 kg     ← 아니다

그래서 숫자만 보고 고르지 않고, 키워드를 먼저 찾은 뒤 그 값을 가져온다.
이 모듈은 이미지에 의존하지 않으므로 단위 테스트가 가능하다 — CI 에서 모델 없이 돈다.
"""

from __future__ import annotations

import re

from app.schemas import OcrLine, Verdict, WeightExtraction

# 이 키워드 옆의 값이 우리가 찾는 중량이다. 긴 것부터 둔다 —
# "총중량" 을 "중량" 으로 먼저 잡으면 근거가 약해진 채로 확정된다.
ANCHORS: tuple[tuple[str, float], ...] = (
    ("총중량", 1.00),
    ("총 중량", 1.00),
    ("실중량", 1.00),
    ("순중량", 1.00),
    ("정미중량", 1.00),
    ("계근중량", 0.95),
    ("입고중량", 0.90),
    ("반입중량", 0.90),
    ("중량", 0.70),
    ("무게", 0.70),
)

# 이 말이 같은 줄에 있으면 그 줄의 숫자는 우리가 찾는 값이 아니다.
# 공차(빈 차 무게)를 총중량으로 잘못 읽으면 신고량이 통째로 어긋난다.
NEGATIVE = ("공차", "차량", "번호", "일자", "시간", "단가", "금액", "합계금액")

# 값과 단위. OCR 이 콤마를 찍거나 공백을 흘리는 경우가 흔해 느슨하게 잡는다.
NUMBER_UNIT = re.compile(
    r"(?P<value>\d{1,3}(?:,\d{3})*(?:\.\d+)?|\d+(?:\.\d+)?)\s*"
    r"(?P<unit>kg|KG|Kg|킬로그램|킬로|g|G|그램|t|T|톤)"
    # 단위 뒤에 영문이 이어지면 단위가 아니다 ("19.92kgm" 같은 오독 방지).
    # \b 는 폭이 0 이라 ? 를 붙일 수 없으므로 lookahead 로 쓴다.
    r"(?![A-Za-z])"
)

UNIT_TO_GRAM = {
    "kg": 1000, "KG": 1000, "Kg": 1000, "킬로그램": 1000, "킬로": 1000,
    "g": 1, "G": 1, "그램": 1,
    "t": 1_000_000, "T": 1_000_000, "톤": 1_000_000,
}

# 기본 허용 오차. 계근 오차·수분·이물로 실제 값은 늘 조금씩 다르다.
DEFAULT_TOLERANCE = 0.01


def _to_gram(value: str, unit: str) -> int | None:
    try:
        amount = float(value.replace(",", ""))
    except ValueError:
        return None
    factor = UNIT_TO_GRAM.get(unit)
    if factor is None:
        return None
    gram = round(amount * factor)
    # 1g 미만이나 100톤 초과는 계근표 값으로 볼 수 없다. OCR 오독일 가능성이 높다.
    return gram if 0 < gram <= 100_000_000 else None


def _candidates(line: OcrLine) -> list[tuple[int, str]]:
    """한 줄에서 (gram, 원문) 후보를 모두 뽑는다."""
    found = []
    for match in NUMBER_UNIT.finditer(line.text):
        gram = _to_gram(match.group("value"), match.group("unit"))
        if gram is not None:
            found.append((gram, match.group(0)))
    return found


def _has_negative(text: str) -> bool:
    return any(word in text for word in NEGATIVE)


def _anchor_in(text: str) -> tuple[str, float] | None:
    for keyword, weight in ANCHORS:
        if keyword in text.replace(" ", ""):
            return keyword, weight
    return None


def extract_weight(lines: list[OcrLine]) -> WeightExtraction:
    """읽은 줄들에서 중량을 고른다. 근거가 강한 순으로 시도한다."""
    if not lines:
        return WeightExtraction()

    # 1순위 — 키워드와 값이 같은 줄에 있다. "총중량 19.92kg"
    for line in lines:
        if _has_negative(line.text):
            continue
        anchor = _anchor_in(line.text)
        if not anchor:
            continue
        found = _candidates(line)
        if found:
            gram, source = found[0]
            return WeightExtraction(
                gram=gram,
                source_text=line.text,
                anchor=anchor[0],
                confidence=round(line.score * anchor[1], 3),
            )

    # 2순위 — 키워드는 왼쪽 칸, 값은 오른쪽 칸에 나뉘어 있다. 표 형식에서 흔하다.
    #
    #     ┌─────────┬──────────┐
    #     │ 총중량   │ 19.92kg  │
    #     └─────────┴──────────┘
    #
    # 같은 행인지는 세로 중심이 서로의 글자 높이 안에 들어오는지로 본다.
    # 사진이 기울면 y 가 조금씩 어긋나므로 정확히 같기를 요구하면 못 찾는다.
    for line in lines:
        if _has_negative(line.text) or _candidates(line):
            continue
        anchor = _anchor_in(line.text)
        if not anchor:
            continue
        for other in lines:
            if other is line or other.x_min < line.x_max:
                continue  # 오른쪽에 있는 것만
            if abs(other.y_center - line.y_center) > max(line.height, other.height) * 0.7:
                continue  # 같은 행이 아니다
            if _has_negative(other.text):
                continue
            found = _candidates(other)
            if found:
                gram, source = found[0]
                return WeightExtraction(
                    gram=gram,
                    source_text=f"{line.text} {other.text}",
                    anchor=anchor[0],
                    confidence=round(min(line.score, other.score) * anchor[1] * 0.95, 3),
                )

    # 3순위 — 키워드를 못 찾았다. 값은 있으니 가장 큰 것을 후보로 두되 신뢰도를 낮춘다.
    #
    # 계근표에서 가장 큰 무게는 대개 총중량이다. 다만 근거가 약하므로 이 경로로 나온 값은
    # 그대로 통과시키지 않고 사람이 보게 만든다 (judge 가 confidence 를 함께 본다).
    everything: list[tuple[int, str, float]] = []
    for line in lines:
        if _has_negative(line.text):
            continue
        for gram, source in _candidates(line):
            everything.append((gram, source, line.score))
    if everything:
        gram, source, score = max(everything, key=lambda item: item[0])
        return WeightExtraction(
            gram=gram, source_text=source, anchor=None, confidence=round(score * 0.4, 3)
        )

    return WeightExtraction()


def judge(
    extracted: WeightExtraction,
    declared_gram: int | None,
    tolerance: float = DEFAULT_TOLERANCE,
) -> tuple[Verdict, float | None]:
    """신고값과 대조해 판정한다.

    **막는 게 아니라 분류한다.** FLAG 는 '틀렸다' 가 아니라 '사람이 봐야 한다' 는 뜻이다.
    자동 차단을 넣으면 OCR 오독 한 번에 정상 등록이 막히고, 그 비용이 얻는 것보다 크다.
    """
    if extracted.gram is None:
        return Verdict.UNREADABLE, None

    # 근거 없이 추정한 값(3순위)은 일치해 보여도 통과로 부르지 않는다.
    if extracted.anchor is None and extracted.confidence < 0.5:
        return Verdict.FLAG, None if declared_gram is None else _ratio(extracted.gram, declared_gram)

    if declared_gram is None or declared_gram <= 0:
        # 대조할 값이 없다. 읽기는 했으므로 UNREADABLE 은 아니다.
        return Verdict.FLAG, None

    delta = _ratio(extracted.gram, declared_gram)
    return (Verdict.PASS if delta <= tolerance else Verdict.FLAG), delta


def _ratio(gram: int, declared_gram: int) -> float:
    return round(abs(gram - declared_gram) / declared_gram, 6)
