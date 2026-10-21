"""테스트 파일이 바뀌지 않았는지 지킨다. 구현 단계 전에 찍고, 끝난 뒤 대조한다.

백엔드 루트에서 실행한다.

    python .claude/skills/tdd-pipeline/scripts/test_guard.py snapshot build/tdd/<slug>/test-snapshot.json
    python .claude/skills/tdd-pipeline/scripts/test_guard.py verify   build/tdd/<slug>/test-snapshot.json
    python .claude/skills/tdd-pipeline/scripts/test_guard.py restore  build/tdd/<slug>/test-snapshot.json

모든 모듈의 src/test 아래 파일을 해시하고, 사본을 <스냅샷>.files/<해시> 로 남긴다.
경로를 그대로 쓰면 윈도우의 경로 길이 제한(260자)에 걸리므로 해시 이름으로 평평하게 둔다.
- verify: 추가·삭제·변경된 파일을 출력한다. 하나라도 있으면 종료 코드 1.
- restore: 스냅샷 시점으로 되돌린다. git checkout 으로 되돌리면 아직 커밋하지 않은 테스트(test-writer 가 쓴 것)까지 사라지므로 사본을 쓴다.
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path

SKIP_DIRS = {"build", ".gradle", ".git", "node_modules", "out", ".idea"}


def test_files(root):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        rel_dir = "/" + Path(dirpath).relative_to(root).as_posix() + "/"

        if "/src/test/" not in rel_dir:
            continue

        for name in filenames:
            path = Path(dirpath) / name
            yield path.relative_to(root).as_posix(), path


def hash_tests(root):
    return {rel: hashlib.sha256(path.read_bytes()).hexdigest() for rel, path in test_files(root)}


def diff(saved, current):
    added = sorted(set(current) - set(saved))
    removed = sorted(set(saved) - set(current))
    changed = sorted(p for p in set(saved) & set(current) if saved[p] != current[p])

    return added, removed, changed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["snapshot", "verify", "restore"])
    parser.add_argument("file")
    parser.add_argument("--root", default=".")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    snapshot = Path(args.file)
    copies = snapshot.with_name(snapshot.name + ".files")

    if args.command == "snapshot":
        if copies.exists():
            shutil.rmtree(copies)

        hashes = {}
        copies.mkdir(parents=True)

        for rel, path in test_files(root):
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            hashes[rel] = digest
            shutil.copy2(path, copies / digest)

        snapshot.parent.mkdir(parents=True, exist_ok=True)
        snapshot.write_text(json.dumps(hashes, indent=1, ensure_ascii=False), encoding="utf-8")
        print(f"테스트 파일 {len(hashes)}개를 기록했다: {snapshot}")
        return

    saved = json.loads(snapshot.read_text(encoding="utf-8"))
    added, removed, changed = diff(saved, hash_tests(root))

    if not (added or removed or changed):
        print(f"테스트 파일 변경 없음 ({len(saved)}개)")
        return

    if args.command == "verify":
        for label, paths in (("추가", added), ("삭제", removed), ("변경", changed)):
            for p in paths:
                print(f"{label}: {p}")

        sys.exit(1)

    for p in added:
        (root / p).unlink()
        print(f"지움: {p}")

    for p in removed + changed:
        (root / p).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(copies / saved[p], root / p)
        print(f"되돌림: {p}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
