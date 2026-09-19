"""GMS 경유 LLM 호출 최소 클라이언트 (PoC 전용).

키는 ../.env 의 LLM_API_KEY 를 읽는다. 값은 어디에도 출력하지 않는다.

  Anthropic  https://gms.ssafy.io/gmsapi/api.anthropic.com/v1/messages   (claude-sonnet-4-6 · claude-opus-4-6)
  Gemini     https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com/v1beta/models/{model}:generateContent

GMS 는 /v1/messages 만 중계한다. Files API 는 500 이 나므로 파일 회수는 쓰지 않는다.
"""
from __future__ import annotations

import json
import os
import urllib.request
from pathlib import Path

ANTHROPIC_URL = "https://gms.ssafy.io/gmsapi/api.anthropic.com/v1/messages"
GEMINI_BASE = "https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com/v1beta/models"
ENV_PATH = Path(__file__).resolve().parent.parent / ".env"


def _key() -> str:
    key = os.getenv("LLM_API_KEY")
    if not key and ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("LLM_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"').strip("'")
                break
    if not key:
        raise RuntimeError(f"LLM_API_KEY 가 없습니다. {ENV_PATH} 를 확인하세요.")
    return key


def _post(url: str, body: dict, headers: dict, timeout: int) -> dict:
    req = urllib.request.Request(
        url, data=json.dumps(body).encode("utf-8"), method="POST",
        headers={"content-type": "application/json", **headers},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def claude(blocks: list[dict], model: str = "claude-sonnet-4-6",
           max_tokens: int = 16000, timeout: int = 600) -> tuple[str, dict]:
    """Claude 호출. blocks 는 user content 블록 배열. (텍스트, usage) 반환."""
    data = _post(
        ANTHROPIC_URL,
        {"model": model, "max_tokens": max_tokens,
         "messages": [{"role": "user", "content": blocks}]},
        {"x-api-key": _key(), "anthropic-version": "2023-06-01"},
        timeout,
    )
    if "content" not in data:
        raise RuntimeError(f"예상 밖 응답: {str(data)[:300]}")
    text = "".join(b.get("text", "") for b in data["content"] if b.get("type") == "text")
    return text, data.get("usage", {})


def gemini(prompt: str, model: str = "gemini-2.5-flash", timeout: int = 300) -> tuple[str, dict]:
    """Gemini 호출. (텍스트, usage) 반환."""
    data = _post(
        f"{GEMINI_BASE}/{model}:generateContent",
        {"contents": [{"parts": [{"text": prompt}]}]},
        {"x-goog-api-key": _key()},
        timeout,
    )
    cand = data.get("candidates", [{}])[0]
    text = "".join(p.get("text", "") for p in cand.get("content", {}).get("parts", []))
    return text, data.get("usageMetadata", {})


def pdf_block(path: str | Path) -> dict:
    """PDF 를 Claude 에 넘기는 document 블록."""
    import base64
    raw = Path(path).read_bytes()
    return {"type": "document",
            "source": {"type": "base64", "media_type": "application/pdf",
                       "data": base64.standard_b64encode(raw).decode()}}


def cost_krw(usage: dict, model: str = "claude-sonnet-4-6") -> float:
    """대략 원화 환산. Sonnet 4.6 = $3/$15 per MTok, 1400원/달러 가정."""
    rates = {"claude-sonnet-4-6": (3, 15), "claude-opus-4-6": (15, 75)}
    i_rate, o_rate = rates.get(model, (3, 15))
    i = usage.get("input_tokens") or usage.get("promptTokenCount") or 0
    o = usage.get("output_tokens") or usage.get("candidatesTokenCount") or 0
    return (i * i_rate + o * o_rate) / 1e6 * 1400


def extract_block(text: str, lang: str) -> str:
    """```lang ... ``` 코드블록 안을 꺼낸다. 없으면 원문 그대로."""
    fence = f"```{lang}"
    if fence in text:
        return text.split(fence, 1)[1].split("```", 1)[0].strip()
    if "```" in text:
        return text.split("```", 1)[1].split("```", 1)[0].strip()
    return text.strip()


def image_block(path: str | Path, media_type: str = "image/png") -> dict:
    """이미지를 Claude 에 넘기는 블록. 레이아웃을 '보게' 하려면 document 가 아니라 이것."""
    import base64
    raw = Path(path).read_bytes()
    return {"type": "image",
            "source": {"type": "base64", "media_type": media_type,
                       "data": base64.standard_b64encode(raw).decode()}}


def design_to_png(src: str | Path, dst: str | Path, long_edge: int = 1450) -> Path:
    """디자인 원본(PDF·이미지)을 PNG 한 장으로. PDF 는 1쪽만 쓴다."""
    from PIL import Image
    src, dst = Path(src), Path(dst)
    if src.suffix.lower() == ".pdf":
        import pypdfium2 as pdfium
        page = pdfium.PdfDocument(str(src))[0]
        scale = long_edge / max(page.get_width(), page.get_height())
        img = page.render(scale=scale).to_pil()
    else:
        img = Image.open(src).convert("RGB")
        if max(img.size) > long_edge:
            r = long_edge / max(img.size)
            img = img.resize((int(img.width * r), int(img.height * r)), Image.LANCZOS)
    img.save(dst)
    return dst


def screenshot_html(html_path: str | Path, dst: str | Path, long_edge: int = 1450) -> Path | None:
    """만든 HTML 을 실제로 그려서 PNG 로. 이게 있어야 원본과 비교할 수 있다."""
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        return None
    html_path, dst = Path(html_path), Path(dst)
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch()
            page = browser.new_page(viewport={"width": 794, "height": 1123},
                                    device_scale_factor=long_edge / 1123)
            page.goto(html_path.resolve().as_uri())
            page.wait_for_timeout(300)
            page.screenshot(path=str(dst), clip={"x": 0, "y": 0, "width": 794, "height": 1123})
            browser.close()
    except Exception:
        return None
    return dst
