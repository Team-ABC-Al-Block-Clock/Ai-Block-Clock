"""외부 이미지 없는 자체 제작 보고서 디자인 샘플. reportlab과 한글 TTF 필요."""
import argparse
from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import simpleSplit

W, H = 595.28, 841.89


def make(path, dark=False):
    c = canvas.Canvas(str(path), pagesize=(W, H))
    c.setTitle("브랜드 전략 보고서 - 디자인 샘플 " + ("B" if dark else "A"))
    ink = "#132F2B" if not dark else "#192B46"
    accent = "#287866" if not dark else "#4559CB"
    paper = "#F6F5EF" if not dark else "#F3F5FB"
    def box(x, y, w, h, color):
        c.setFillColor(HexColor(color)); c.rect(x, H-y-h, w, h, fill=1, stroke=0)
    def text(x, y, value, size=10, color=ink, bold=False, width=490, leading=None):
        font = "KRB" if bold else "KR"
        c.setFont(font, size); c.setFillColor(HexColor(color))
        lines = simpleSplit(value, font, size, width)
        for i, line in enumerate(lines):
            c.drawString(x, H-y-i*(leading or size*1.6), line)
        return y+len(lines)*(leading or size*1.6)
    box(0, 0, W, H, paper)
    box(0, 0, W, 222, ink)
    text(40, 43, "BRAND STRATEGY / DESIGN " + ("B" if dark else "A"), 9, "#C4D4CC", True)
    text(40, 91, "소소칫솔", 29, "#FFFFFF", True)
    text(40, 128, "매일의 교체를, 더 가볍게", 17, "#FFFFFF")
    text(40, 167, "대나무 손잡이와 교체 구독을 일상의 선택으로", 10, "#D6E0DB")
    text(40, 201, "작성 기관 · 샘플 스튜디오     |     발행일 · YYYY.MM.DD", 8, "#C4D4CC")
    text(40, 253, "01   브랜드 목적", 12, ink, True)
    labels = [("PURPOSE 01", "매일의 관리", "작은 습관이 이어지도록"),
              ("PURPOSE 02", "성능을 먼저", "편안한 사용 경험을 중심에"),
              ("PURPOSE 03", "교체를 쉽게", "구독으로 선택 부담 줄이기")]
    for i, (tag, title, desc) in enumerate(labels):
        x = 40+i*176
        box(x, 273, 164, 98, "#FFFFFF")
        box(x, 273, 164, 3, accent)
        text(x+13, 297, tag, 8, accent, True, 138)
        text(x+13, 324, title, 14, ink, True, 138)
        text(x+13, 350, desc, 8, ink, False, 140)
    text(40, 410, "02   메시지 체계", 12, ink, True)
    for y, label, value in [(447, "핵심", "바꿀 때를 놓치지 않는 칫솔"),
                            (487, "보조", "3개월마다 도착하는 교체 구독"),
                            (527, "일상", "이번 칫솔도, 잘 부탁해")]:
        text(40, y, label, 9, accent, True)
        text(98, y, value, 12, ink, False, 430)
        box(40, y+12, 515, .5, "#CDD6D0")
    text(40, 581, "03   브랜드 포지셔닝", 12, ink, True)
    for y, label, value in [(616, "고객", "교체 시점을 자주 놓치는 1인 가구를 위한 선택"),
                            (643, "제품", "칫솔 성능과 소재의 균형을 갖춘 생활용품"),
                            (670, "경험", "정해진 주기에 맞춰 자연스럽게 이어지는 관리")]:
        text(40, y, label, 9, accent, True)
        text(98, y, value, 10, ink, False, 430)
    box(40, 703, 515, 62, "#E5EBE5" if not dark else "#E2E7F8")
    text(54, 725, "전략 메모", 9, accent, True)
    text(54, 746, "환경 메시지는 사용 경험을 뒷받침한다. 성능의 근거 없는 우월성은 말하지 않는다.", 8, ink, False, 487)
    text(40, 800, "합성 브랜드 · 사람이 작성한 디자인 예시 · LLM 실측 결과 아님", 8, "#69766F")
    text(530, 800, "01", 9, accent, True)
    c.showPage()

    # 2쪽: 나머지 스키마 배열과 총평을 모두 보여 주는 상세 페이지
    box(0, 0, W, H, paper)
    box(0, 0, W, 126, ink)
    text(40, 40, "STRATEGY DETAILS", 9, "#C4D4CC", True)
    text(40, 77, "소소칫솔 - 실행을 위한 전략 근거", 20, "#FFFFFF", True)
    text(40, 107, "브랜드 목적과 메시지를 실제 선택 이유로 연결합니다.", 9, "#D6E0DB")

    text(40, 162, "04   핵심 전략", 12, ink, True)
    strategies = [("01", "교체 신호 시각화", "색 변화로 바꿀 때를 알아보게 합니다."),
                  ("02", "첫 구매 부담 완화", "단품 경험 뒤 구독을 선택하게 합니다."),
                  ("03", "성능 근거 우선", "소재보다 미세모 사용 경험을 먼저 말합니다.")]
    for i, (num, title, desc) in enumerate(strategies):
        x = 40+i*176
        box(x, 183, 164, 109, "#FFFFFF")
        box(x, 183, 164, 3, accent)
        text(x+13, 207, num, 8, accent, True, 138)
        text(x+13, 234, title, 12, ink, True, 138)
        text(x+13, 261, desc, 8, ink, False, 138, 12)

    text(40, 336, "05   선택의 이유", 12, ink, True)
    reasons = [("기능", "미세모 밀도 2배", "제품의 구체적인 사용 차이를 설명합니다."),
               ("경험", "3개월 교체 구독", "교체를 기억해야 하는 번거로움을 줄입니다."),
               ("소재", "대나무 손잡이", "매일 쓰는 물건의 소재 선택을 보여 줍니다.")]
    for i, (axis, title, desc) in enumerate(reasons):
        y = 373+i*70
        text(40, y, axis, 9, accent, True)
        text(98, y, title, 11, ink, True, 175)
        text(276, y, desc, 9, ink, False, 278)
        box(40, y+20, 515, .5, "#CDD6D0")

    text(40, 606, "06   전략 총평", 12, ink, True)
    box(40, 630, 515, 106, "#E5EBE5" if not dark else "#E2E7F8")
    text(56, 656, "교체를 기억시키는 브랜드가 아니라, 교체가 자연스럽게 이어지는 경험을 만듭니다.", 10, ink, True, 480)
    text(56, 687, "성능의 근거를 먼저 제시하고 소재 메시지는 그 선택을 뒷받침합니다. 경쟁 브랜드를", 8, ink, False, 480)
    text(56, 706, "직접 비교하거나 검증되지 않은 우월성을 말하지 않습니다.", 8, ink, False, 480)

    text(40, 780, "합성 브랜드 · 사람이 작성한 디자인 예시 · LLM 실측 결과 아님", 8, "#69766F")
    text(530, 780, "02", 9, accent, True)
    c.showPage()
    c.save()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--font", type=Path, default=Path("C:/Windows/Fonts/malgun.ttf"))
    ap.add_argument("--bold-font", type=Path, default=Path("C:/Windows/Fonts/malgunbd.ttf"))
    ap.add_argument("--out", type=Path, default=Path(__file__).resolve().parent / "output/pdf")
    args = ap.parse_args()
    pdfmetrics.registerFont(TTFont("KR", str(args.font)))
    pdfmetrics.registerFont(TTFont("KRB", str(args.bold_font)))
    args.out.mkdir(parents=True, exist_ok=True)
    make(args.out / "brand_strategy_design_a.pdf")
    make(args.out / "brand_strategy_design_b.pdf", True)


if __name__ == "__main__":
    main()
