"""
합성 데모 데이터셋 생성 (AI-06-6)

무엇을 만드는가.
  시연 워크플로우의 첫 입력이 되는 학원 성적 데이터를 만든다.
  H2A 노드가 읽을 **엑셀**과, 파싱 결과가 어떤 모양이어야 하는지 보여주는
  **edu.roster@1 JSON** 을 한 쌍으로 만든다.

  ★ 실제 학생 정보는 쓰지 않는다. 이름 · 점수 전부 생성값이다.

왜 두 벌인가 — 분기 때문이다.
  유즈케이스 A에는 분기가 하나 있다 (팀 합의 R-01 · C-3).

      summary.avg_delta < 0  →  경고형 가정통신문
      그 외                  →  격려형 가정통신문

  분기가 실제로 갈라지는 걸 보여주려면 두 갈래를 각각 태우는 입력이 필요하다.
  시연 본편은 `down`(하락 → 경고형)을 쓴다. 변화가 있어야 이야기가 되기 때문이다.
  `up` 은 반대 경로가 살아 있는지 확인하는 용도다.

  ★ 두 파일의 학생 명단과 과목은 동일하다. 달라지는 건 점수뿐이다.
    그래야 "분기 때문에 결과가 달라졌다"고 말할 수 있다.
    명단까지 다르면 무엇 때문에 달라졌는지 가려낼 수 없다.

seed 를 고정한다.
  시연 리허설을 세 번 돌리는데 매번 숫자가 바뀌면 "저번엔 이랬는데" 가 통하지 않는다.

실행:
    cd ai/demo
    python generate_demo_data.py
"""

import json
import random
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent / "data"

SEED = 20260916          # 시연일
N_STUDENTS = 20
SUBJECTS = ["국어", "영어", "수학", "과학"]
PERIOD = {"current": "2026-08", "previous": "2026-07"}

# 실존 인물과 겹치지 않도록 성 + 외자·두 글자 이름을 조합해 만든 가상 명단
NAMES = [
    "강윤슬", "고은채", "권도하", "김서린", "남주하",
    "노하람", "류시온", "문가온", "박새힘", "배온유",
    "서루나", "손나린", "안슬아", "오도윤", "유하진",
    "윤재이", "임소울", "정라온", "차예린", "한별하",
]

# 학원 원장이 엑셀과 함께 올리는 한 줄 코멘트.
# ★ H2A 노드는 이 문장을 가공하지 않고 payload.context.comment 로 그대로 통과시킨다
#   (팀 합의 N-3: H2A 는 LLM 을 쓰지 않는다).
COMMENTS = {
    "down": "이번 달 전체적으로 성적이 좀 떨어졌어요. 학부모님들께 보낼 안내문에 "
            "너무 겁주지는 말고, 그래도 지금 잡아야 한다는 건 분명히 전해주세요.",
    "up": "여름 특강 효과가 있었는지 대체로 올랐습니다. 아이들 칭찬 많이 해주시고, "
          "그래도 방심하지 않게 한마디씩 덧붙여 주세요.",
}


def make_students(rng: random.Random, direction: str) -> list[dict]:
    """학생 20명의 이번 달 · 지난 달 점수를 만든다.

    direction="down" 이면 전체 평균이 내려가고, "up" 이면 올라간다.
    다만 **전원이 같은 방향으로 움직이지는 않게** 한다. 20명이 전부 떨어지는
    데이터는 현실감이 없고, 가정통신문이 학생별로 달라지는 것도 보여줄 수 없다.
    """
    students = []
    for i, name in enumerate(NAMES[:N_STUDENTS]):
        scores = {}
        for subject in SUBJECTS:
            previous = rng.randint(55, 95)

            if direction == "down":
                # 대다수 하락, 4명 정도는 상승 — 격려 문장도 섞이게 한다
                delta = rng.randint(3, 12) if i % 5 == 0 else -rng.randint(2, 15)
            else:
                delta = -rng.randint(2, 9) if i % 5 == 0 else rng.randint(2, 14)

            current = max(0, min(100, previous + delta))
            scores[subject] = {"current": current, "previous": previous}

        total = rng.randint(20, 22)          # 이번 달 수업 일수
        absent = rng.choice([0, 0, 0, 1, 1, 2, 3])
        students.append({
            "name": name,
            "scores": scores,
            "attendance": {"present": total - absent, "absent": absent},
        })
    return students


def summarize(students: list[dict]) -> dict:
    """생성 결과가 의도한 방향인지 확인하려고 계산한다 (파일에는 넣지 않는다).

    ★ 이 계산은 검증용이다. 실제 통계 산출은 분석 노드가 한다.
      여기서 미리 계산해 넣어버리면 분석 노드가 일을 안 해도 통과해버린다.
    """
    def avg(key: str) -> float:
        vals = [s["scores"][sub][key] for s in students for sub in SUBJECTS]
        return sum(vals) / len(vals)

    cur, prev = avg("current"), avg("previous")
    return {"avg_current": round(cur, 2), "avg_previous": round(prev, 2),
            "avg_delta": round(cur - prev, 2)}


def to_rows(students: list[dict]) -> list[dict]:
    """엑셀 한 행 = 학생 한 명. 원장이 실제로 쓸 법한 납작한 표로 편다."""
    rows = []
    for s in students:
        row = {"학생명": s["name"]}
        for subject in SUBJECTS:
            row[f"{subject}(이번달)"] = s["scores"][subject]["current"]
            row[f"{subject}(지난달)"] = s["scores"][subject]["previous"]
        row["출석"] = s["attendance"]["present"]
        row["결석"] = s["attendance"]["absent"]
        rows.append(row)
    return rows


def main() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    try:
        import pandas as pd
    except ImportError:
        raise SystemExit("pandas 가 필요합니다:  python -m pip install pandas openpyxl")

    for direction in ("down", "up"):
        rng = random.Random(SEED if direction == "down" else SEED + 1)
        students = make_students(rng, direction)
        check = summarize(students)

        # 의도한 방향이 아니면 시연이 엉뚱한 분기를 탄다. 여기서 막는다.
        if direction == "down" and check["avg_delta"] >= 0:
            raise SystemExit(f"down 인데 평균이 올랐습니다: {check}")
        if direction == "up" and check["avg_delta"] <= 0:
            raise SystemExit(f"up 인데 평균이 내렸습니다: {check}")

        roster = {
            "schema_id": "edu.roster@1",
            "period": PERIOD,
            "subjects": SUBJECTS,
            "students": students,
            "context": {"comment": COMMENTS[direction]},
        }
        json_path = DATA_DIR / f"demo_roster_{direction}.json"
        json_path.write_text(
            json.dumps(roster, ensure_ascii=False, indent=2), encoding="utf-8"
        )

        xlsx_path = DATA_DIR / f"demo_roster_{direction}.xlsx"
        pd.DataFrame(to_rows(students)).to_excel(xlsx_path, index=False, sheet_name="성적")

        branch = "경고형" if check["avg_delta"] < 0 else "격려형"
        print(f"{direction:>4}  평균 {check['avg_previous']} → {check['avg_current']} "
              f"({check['avg_delta']:+.2f})  →  {branch} 분기")
        print(f"      {json_path.name} · {xlsx_path.name}")

    print(f"\n학생 {N_STUDENTS}명 · 과목 {len(SUBJECTS)}개 · {PERIOD['previous']} → {PERIOD['current']}")
    print("명단과 과목은 두 파일이 동일하다. 달라지는 것은 점수뿐이다.")


if __name__ == "__main__":
    main()
