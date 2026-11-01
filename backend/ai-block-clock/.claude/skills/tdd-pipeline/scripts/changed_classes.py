"""작업 트리에서 바뀐 운영 클래스를 모듈별로 모으고, 그 범위로 PIT 를 돌리는 명령을 만든다.

백엔드 루트(backend/ai-block-clock)에서 실행한다.

    python .claude/skills/tdd-pipeline/scripts/changed_classes.py
    python .claude/skills/tdd-pipeline/scripts/changed_classes.py --since <커밋> --json

기본은 HEAD 대비 수정·추가된 파일과 추적되지 않는 파일이다. --since 를 주면 그 커밋 이후 커밋된 변경도 포함한다.
삭제된 파일은 돌연변이 대상이 아니므로 뺀다.
"""
import argparse
import json
import subprocess
import sys
from collections import defaultdict
from pathlib import PurePosixPath

MAIN_MARKER = "/src/main/java/"
TEST_MARKER = "/src/test/java/"


def git_lines(*args):
    out = subprocess.run(
        ["git", "-c", "core.quotepath=false", *args],
        capture_output=True, text=True, encoding="utf-8", check=True,
    ).stdout
    return [line for line in out.splitlines() if line.strip()]


def changed_paths(since):
    paths = set()

    # --relative: 백엔드 루트 기준 경로로 받는다.
    for line in git_lines("diff", "--name-only", "--relative", "--diff-filter=ACMR", since or "HEAD"):
        paths.add(line)

    for line in git_lines("ls-files", "--others", "--exclude-standard"):
        paths.add(line)

    return sorted(paths)


def split(path, marker):
    """'core/core-api/src/main/java/com/x/Foo.java' -> (':core:core-api', 'com.x.Foo')"""
    normalized = "/" + path.replace("\\", "/")
    module_dir, _, rest = normalized.partition(marker)
    module = ":" + module_dir.strip("/").replace("/", ":")
    fqcn = str(PurePosixPath(rest).with_suffix("")).replace("/", ".")

    return module, fqcn


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--since", help="이 커밋 이후의 변경도 포함한다 (기본: HEAD 대비 작업 트리만)")
    parser.add_argument("--json", action="store_true", help="JSON 으로 출력한다")
    args = parser.parse_args()

    main_classes = defaultdict(list)
    test_classes = defaultdict(list)

    for path in changed_paths(args.since):
        if not path.endswith(".java"):
            continue

        if MAIN_MARKER in "/" + path:
            module, fqcn = split(path, MAIN_MARKER)
            main_classes[module].append(fqcn)
        elif TEST_MARKER in "/" + path:
            module, fqcn = split(path, TEST_MARKER)
            test_classes[module].append(fqcn)

    modules = []

    for module in sorted(set(main_classes) | set(test_classes)):
        classes = sorted(main_classes.get(module, []))
        # 내부 클래스(Foo$Bar)까지 포함한다. Foo* 로 쓰면 FooBar 같은 다른 클래스까지 걸린다.
        patterns = [p for c in classes for p in (c, c + "$*")]
        command = None

        if classes:
            command = f"./gradlew {module}:pitest -PpitTargetClasses='{','.join(patterns)}' --console=plain"

        modules.append({
            "module": module,
            "moduleDir": module.lstrip(":").replace(":", "/"),
            "mainClasses": classes,
            "testClasses": sorted(test_classes.get(module, [])),
            "pitestCommand": command,
        })

    if args.json:
        json.dump({"modules": modules}, sys.stdout, ensure_ascii=False, indent=2)
        print()
        return

    if not modules:
        print("바뀐 Java 파일이 없다.")
        return

    for m in modules:
        print(f"## {m['module']}")
        print("운영 클래스:", ", ".join(m["mainClasses"]) or "(없음)")
        print("테스트 클래스:", ", ".join(m["testClasses"]) or "(없음)")
        if m["pitestCommand"]:
            print("PIT:", m["pitestCommand"])
        print()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
