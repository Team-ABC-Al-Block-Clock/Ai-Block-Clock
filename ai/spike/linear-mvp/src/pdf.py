"""마크다운 → HTML → PDF (xhtml2pdf). 한글 폰트 임베딩.

Windows 로컬 스파이크용. EC2(Linux) 배포 시 WeasyPrint 로 교체 예정.
"""
from __future__ import annotations

import os
import tempfile
import types
from datetime import datetime
from pathlib import Path

import markdown
from xhtml2pdf import files as _pisa_files
from xhtml2pdf import pisa


def _patch_xhtml2pdf_tmpfile_for_windows() -> None:
    """xhtml2pdf 0.2.x 는 @font-face 폰트를 NamedTemporaryFile(열린 상태)에 복사한 뒤
    reportlab 이 그 경로를 다시 열게 한다. Windows 는 열린 파일을 다른 핸들로 열 수 없어
    PermissionError 가 난다. 닫힌 임시 파일을 돌려주도록 교체한다. Linux 는 건드리지 않는다."""
    if os.name != "nt":
        return

    def get_named_tmp_file(self):
        data = self.get_data()
        fd, path = tempfile.mkstemp(suffix=getattr(self, "suffix", None) or "")
        with os.fdopen(fd, "wb") as f:
            if data:
                f.write(data)
        shim = types.SimpleNamespace(
            name=path,
            close=lambda p=path: os.path.exists(p) and os.unlink(p),
        )
        _pisa_files.files_tmp.append(shim)   # pisa 의 cleanFiles() 가 close() 를 호출해 지운다
        if self.path is None:
            self.path = path
        return shim

    _pisa_files.BaseFile.get_named_tmp_file = get_named_tmp_file


_patch_xhtml2pdf_tmpfile_for_windows()

TEMPLATE = Path(__file__).resolve().parents[1] / "templates" / "report.html"

# 한글 TTF 를 위에서부터 찾는다. 컨테이너(Linux)와 로컬(Windows) 양쪽에서 돌아야 한다.
# 폰트 파일을 저장소에 커밋하지 않고 Docker 이미지에서 apt 로 설치한다(fonts-nanum).
FONT_CANDIDATES = [
    Path("/usr/share/fonts/truetype/nanum/NanumGothic.ttf"),   # Docker — fonts-nanum
    Path("/usr/share/fonts/truetype/nanum/NanumBarunGothic.ttf"),
    Path("C:/Windows/Fonts/malgun.ttf"),                       # Windows — 맑은 고딕
    Path("C:/Windows/Fonts/NanumGothic.ttf"),
    Path(__file__).resolve().parents[1] / "templates" / "NotoSansKR-Regular.ttf",  # 직접 넣을 때
]


def find_font() -> Path:
    # 환경변수로 지정하면 그것을 최우선으로 쓴다. 배포 환경마다 경로가 다를 때의 탈출구다.
    override = os.getenv("PDF_FONT_PATH")
    if override:
        p = Path(override)
        if p.exists():
            return p
        raise FileNotFoundError(f"PDF_FONT_PATH 가 가리키는 파일이 없습니다: {override}")
    for p in FONT_CANDIDATES:
        if p.exists():
            return p
    raise FileNotFoundError(
        "한글 TTF 폰트를 찾지 못했습니다. Linux 라면 `apt-get install fonts-nanum`, "
        "또는 PDF_FONT_PATH 환경변수로 TTF 경로를 지정하세요."
    )


def markdown_to_html(md_text: str) -> str:
    return markdown.markdown(md_text, extensions=["tables", "fenced_code", "sane_lists"])


def markdown_to_pdf(md_text: str, title: str, out_path: str | Path) -> Path:
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    font = find_font().as_posix()
    html = TEMPLATE.read_text(encoding="utf-8")
    html = (
        html.replace("{{title}}", title)
        .replace("{{date}}", datetime.now().strftime("%Y-%m-%d"))
        .replace("{{font_path}}", font)
        .replace("{{body}}", markdown_to_html(md_text))
    )
    with out.open("wb") as f:
        status = pisa.CreatePDF(html, dest=f, encoding="utf-8")
    if status.err:
        raise RuntimeError(f"PDF 생성 오류 {status.err}건")
    return out


if __name__ == "__main__":
    import sys

    sys.stdout.reconfigure(encoding="utf-8")
    sample = "# 한글 테스트\n\n본문 문단입니다.\n\n| 이름 | 점수 |\n| --- | --- |\n| 김서준 | 88 |\n| 이하윤 | 92 |\n\n- 항목 하나\n- 항목 둘\n"
    p = markdown_to_pdf(sample, "PDF 렌더링 테스트", "out/_pdf_test/test.pdf")
    print(f"saved {p} ({p.stat().st_size} bytes)")
