"""확대해 다시 읽은 글자에서 단위를 가려낸다.

작게 찍힌 g 는 확대하면 대개 "9" 로 읽힌다(실측: 숫자 높이의 0.40~0.48 배). 7세그먼트 숫자
9 라면 다른 숫자와 같은 높이라 이 크기로 나올 수 없다 — 그래서 작은 9 는 g 로 본다.
"""

from app.vision.reading import unit_from_zoom


def test_g_and_kg_are_units():
    assert unit_from_zoom([("g", 0.7, 0.4)]) == ("g", 0.7)
    assert unit_from_zoom([("Kg", 0.6, 0.35), ("kg", 0.99, 0.56)]) == ("kg", 0.99)


def test_a_small_nine_is_the_g_unit():
    assert unit_from_zoom([("9", 0.95, 0.45)]) == ("g", 0.95)


def test_a_full_height_nine_is_a_digit_not_a_unit():
    assert unit_from_zoom([("9", 0.95, 0.95)]) is None


def test_other_glyphs_are_not_units():
    assert unit_from_zoom([("6", 0.98, 0.45), ("品", 0.9, 0.2), ("+", 0.97, 0.02)]) is None


def test_the_most_confident_unit_wins():
    assert unit_from_zoom([("g", 0.57, 0.32), ("9", 0.93, 0.4)]) == ("g", 0.93)
