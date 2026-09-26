"""실패 3케이스 시연 자료 생성 — 판매자(supplier) · 플랫폼(platform) 실패용 참고 파일을 만든다.

사용자(consumer) 실패는 파일이 필요 없다 — `ai/demo/brand/brief_bad.json`(가격대 "초고가")을 입력으로 쓴다.
나머지 둘은 판매자가 노드에 붙이는 참고 파일로 재현한다. 실제 AI가 형식을 어기기를 기다리지 않고도
같은 판정 경로(계약 §7.1 · §9.2)를 결정적으로 보여 줄 수 있다.

    cd ai/spike/linear-mvp
    uv run python ../../demo/brand/failure/make_failure_cases.py

만드는 것 (`ai/demo/brand/failure/generated/`, 커밋하지 않는다)
    브랜드가이드.docx     허용하지 않는 형식(pdf · txt · md · csv 만 허용) → REF_FILE_REJECTED · supplier
    참고자료_대용량.txt   약 42만 자 → 입력 상한 MAX_INPUT_CHARS(40만 자) 초과 → INPUT_TOO_LARGE · platform
"""
from __future__ import annotations

from pathlib import Path

OUT = Path(__file__).resolve().parent / "generated"
LARGE_CHARS = 420_000            # Runtime 기본 상한 400,000자보다 조금 크게


def main() -> None:
    OUT.mkdir(exist_ok=True)

    # 내용은 상관없다 — 확장자로 거부된다. 실제 Word 파일처럼 보이게 zip 머리만 둔다
    docx = OUT / "브랜드가이드.docx"
    docx.write_bytes(b"PK\x03\x04" + "판매자가 Word 로 만든 가이드 (시연용)".encode("utf-8"))

    # 판매자가 노드에 과도하게 긴 자료를 붙였다는 설정. 10MB 파일 상한 안이지만 입력 상한은 넘는다
    line = "카피 참고 사례: 매일 쓰는 물건은 성능이 먼저다. 대나무 손잡이, 미세모 밀도 두 배, 석 달마다 교체.\n"
    body = line * (LARGE_CHARS // len(line) + 1)
    large = OUT / "참고자료_대용량.txt"
    large.write_text(body[:LARGE_CHARS], encoding="utf-8")

    print(f"{docx}  {docx.stat().st_size:,} bytes")
    print(f"{large}  {len(body[:LARGE_CHARS]):,}자 · {large.stat().st_size / 1024 / 1024:.1f}MB")


if __name__ == "__main__":
    main()
