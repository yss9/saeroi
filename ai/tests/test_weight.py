"""중량 추출 규칙 테스트.

이미지도 모델도 쓰지 않는다 — OcrLine 을 직접 만들어 넣는다. 그래서 CI 에서
모델 다운로드 없이 빠르게 돌고, 실패하면 원인이 '우리 규칙' 하나로 좁혀진다.
"""

from app.schemas import OcrLine, Verdict
from app.weight import extract_weight, judge


def line(text: str, *, x: float = 0, y: float = 0, w: float = 100, h: float = 20, score: float = 0.95):
    return OcrLine(text=text, score=score, x_min=x, y_min=y, x_max=x + w, y_max=y + h)


class TestExtract:
    def test_같은_줄에_키워드와_값이_있으면_뽑는다(self):
        result = extract_weight([line("총중량 19.92kg")])
        assert result.gram == 19920
        assert result.anchor == "총중량"
        assert result.confidence > 0.9

    def test_표_형식으로_칸이_나뉘어도_같은_행이면_뽑는다(self):
        result = extract_weight([
            line("총중량", x=0, y=100),
            line("19.92kg", x=200, y=102),
        ])
        assert result.gram == 19920
        assert result.anchor == "총중량"

    def test_공차중량은_고르지_않는다(self):
        result = extract_weight([
            line("공차중량 2.10kg", y=0),
            line("총중량 19.92kg", y=40),
        ])
        assert result.gram == 19920

    def test_차량번호를_중량으로_읽지_않는다(self):
        result = extract_weight([
            line("차량번호 12가3456", y=0),
            line("총중량 19.92kg", y=40),
        ])
        assert result.gram == 19920

    def test_콤마가_있어도_읽는다(self):
        assert extract_weight([line("총중량 1,250kg")]).gram == 1_250_000

    def test_단위별로_gram_으로_환산한다(self):
        assert extract_weight([line("중량 500g")]).gram == 500
        assert extract_weight([line("중량 1.5t")]).gram == 1_500_000

    def test_키워드가_없으면_가장_큰_값을_낮은_신뢰도로_추정한다(self):
        result = extract_weight([line("19.92kg"), line("2.10kg")])
        assert result.gram == 19920
        assert result.anchor is None
        assert result.confidence < 0.5

    def test_읽을_것이_없으면_비운다(self):
        assert extract_weight([]).gram is None
        assert extract_weight([line("발행일 2026-09-10")]).gram is None


class TestJudge:
    def test_오차_안이면_통과(self):
        extracted = extract_weight([line("총중량 19.92kg")])
        verdict, delta = judge(extracted, 20000, tolerance=0.01)
        assert verdict is Verdict.PASS
        assert delta == 0.004

    def test_오차를_넘으면_사람에게_넘긴다(self):
        extracted = extract_weight([line("총중량 15.00kg")])
        verdict, delta = judge(extracted, 20000, tolerance=0.01)
        assert verdict is Verdict.FLAG
        assert delta == 0.25

    def test_못_읽으면_UNREADABLE(self):
        verdict, delta = judge(extract_weight([]), 20000)
        assert verdict is Verdict.UNREADABLE
        assert delta is None

    def test_근거_없이_추정한_값은_일치해도_통과시키지_않는다(self):
        extracted = extract_weight([line("20.00kg")])
        verdict, _ = judge(extracted, 20000)
        assert verdict is Verdict.FLAG

    def test_신고값이_없으면_대조하지_못한다(self):
        extracted = extract_weight([line("총중량 19.92kg")])
        verdict, delta = judge(extracted, None)
        assert verdict is Verdict.FLAG
        assert delta is None
