"""실제 산업용 저울 사진으로 인식 파이프라인 전체를 검증한다.

합성 이미지만으로 맞춘 임계값은 현장 사진에서 무너진다. 실제로 그랬다 — 세 장 중
두 장이 저울을 찾지 못했고, 찾은 한 장은 본값(34.8 kg) 대신 TARE 줄의 0.0 을 읽었다.

그래서 여기서는 **촬영된 그대로의 사진**을 쓴다. 이 테스트가 느린 것은 OCR 을 실제로
돌리기 때문이고, 그 느림이 이 테스트의 값어치다.

세 장이 각각 다른 것을 지킨다.
  - weighted_clothes    : 표시부가 저울 본체 밖(옆 기둥)에 있다
  - weighted_clothes_2  : 본값과 TARE 줄이 한 화면에 함께 있다
  - weighted_denim      : 표시부가 화면 최상단이고 MAX:30kg 표기가 섞여 있다
"""

from pathlib import Path

import pytest

from app.vision.schemas import VisionStatus
from app.vision.service import WeightRecognitionService

FIXTURES = Path(__file__).parent / "fixtures"

# (파일, 화면에 찍힌 값, 그램 환산)
REAL_PHOTOS = [
    ("weighted_clothes.jpg", "18.7 kg", 18_700),
    ("weighted_clothes_2.jpg", "34.8 kg", 34_800),
    ("weighted_denim.jpg", "12.45 kg", 12_450),
]


@pytest.mark.parametrize("filename,displayed,expected_gram", REAL_PHOTOS)
def test_real_scale_photo_is_recognized(filename, displayed, expected_gram):
    data = (FIXTURES / filename).read_bytes()

    result = WeightRecognitionService().recognize(data)

    assert result.status is VisionStatus.SUCCESS, (
        f"{filename}: 화면에는 {displayed} 가 찍혀 있는데 "
        f"{result.status.value} ({result.message}) 로 끝났다"
    )
    assert result.weight is not None
    assert result.weight.value == expected_gram
    assert result.weight.unit == "g"


def test_tare_line_is_not_mistaken_for_the_weight():
    """본값과 TARE 가 함께 보이는 사진에서 TARE 를 집지 않는다.

    0.0 을 무게로 집으면 화면은 "인식 성공" 이라 말하면서 0 g 을 채운다 — 틀린 값을
    자신 있게 보여주는 쪽이 못 읽었다고 말하는 것보다 나쁘다.
    """
    data = (FIXTURES / "weighted_clothes_2.jpg").read_bytes()

    result = WeightRecognitionService().recognize(data)

    assert result.weight is not None
    assert result.weight.value != 0
    assert result.ocr is not None
    assert "0.0" != result.ocr.raw_text.strip()
