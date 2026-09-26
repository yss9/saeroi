"""OCR 실행부. 사진에서 '글자와 그 위치' 를 뽑는 데까지만 책임진다.

무엇이 중량인지 고르는 일은 weight.py 가 한다. 둘을 나눈 이유는 실패 원인이 다르기
때문이다 — 여기서 실패하면 재촬영을 요구해야 하고, weight.py 에서 실패하면 우리 규칙을
고쳐야 한다. 섞어 두면 로그만 보고 어느 쪽인지 구분할 수 없다.
"""

from __future__ import annotations

import io
import logging
import threading
import time
from typing import TYPE_CHECKING

from app.schemas import OcrLine

if TYPE_CHECKING:  # 타입 힌트용. 런타임에는 불러오지 않는다.
    from PIL.Image import Image

logger = logging.getLogger(__name__)

_engine = None
_lock = threading.Lock()

# 긴 변을 이 크기로 맞춘다. 원본 그대로 넣으면 요즘 폰 사진(4000px 이상)에서 느리고,
# 너무 줄이면 작은 숫자가 뭉개져 검출 단계에서 아예 빠진다.
MAX_SIDE = 1600


def get_engine():
    """모델을 한 번만 올린다.

    RapidOCR 생성자가 ONNX 세션 세 개(검출·방향·인식)를 만드는데 수 초가 걸린다.
    요청마다 만들면 그 시간이 매번 붙는다. 첫 요청이 느린 것은 감수한다 — 컨테이너
    기동 시 미리 올리면 헬스체크가 그만큼 늦어지고, 배포 판정이 컨테이너 상태로만
    이뤄지므로 기동을 가볍게 두는 편이 낫다.
    """
    global _engine
    if _engine is None:
        with _lock:
            if _engine is None:
                from rapidocr_onnxruntime import RapidOCR

                logger.info("RapidOCR 모델 로딩 시작")
                started = time.perf_counter()
                _engine = RapidOCR()
                logger.info("RapidOCR 로딩 완료 %.1fs", time.perf_counter() - started)
    return _engine


def preprocess(data: bytes) -> "Image":
    """EXIF 회전 보정 + 크기 정규화.

    폰으로 찍은 사진은 실제 픽셀은 가로인데 '세로로 돌려서 보라' 는 EXIF 태그만 붙어 있는
    경우가 많다. 그대로 넣으면 글자가 90도 누운 채로 들어가고, 방향 분류기는 180도만
    다루므로 복구되지 않는다. 읽기 전에 픽셀 자체를 돌려 놓는다.
    """
    # 무거운 의존성은 실제로 쓸 때 불러온다. app.main 이 이 모듈을 임포트하는데,
    # 모듈 최상단에서 끌어오면 OCR 을 쓰지 않는 테스트까지 PIL 설치를 요구하게 된다.
    from PIL import Image, ImageOps

    image = Image.open(io.BytesIO(data))
    image = ImageOps.exif_transpose(image)
    if image.mode != "RGB":
        image = image.convert("RGB")

    longest = max(image.size)
    if longest > MAX_SIDE:
        ratio = MAX_SIDE / longest
        image = image.resize(
            (int(image.width * ratio), int(image.height * ratio)), Image.LANCZOS
        )
    return image


def read_lines(data: bytes) -> tuple[list[OcrLine], int]:
    """이미지 바이트 → 읽은 줄 목록. (줄, 소요 ms)"""
    import numpy as np

    image = preprocess(data)
    started = time.perf_counter()
    result, _ = get_engine()(np.array(image))
    elapsed_ms = int((time.perf_counter() - started) * 1000)

    # 글자를 하나도 못 찾으면 None 이 온다. 빈 리스트와 구분하지 않고 똑같이 다룬다.
    if not result:
        return [], elapsed_ms

    lines: list[OcrLine] = []
    for box, text, score in result:
        xs = [point[0] for point in box]
        ys = [point[1] for point in box]
        lines.append(
            OcrLine(
                text=text,
                score=float(score),
                x_min=min(xs),
                y_min=min(ys),
                x_max=max(xs),
                y_max=max(ys),
            )
        )
    # 위에서 아래로, 같은 높이면 왼쪽에서 오른쪽으로. 사람이 읽는 순서와 맞춘다.
    lines.sort(key=lambda line: (round(line.y_center / 10), line.x_min))
    return lines, elapsed_ms
