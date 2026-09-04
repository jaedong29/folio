#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_PATH="${1:-$PROJECT_ROOT/docs/CODE_SNAPSHOT_2026-08-10.md}"
TEMP_PATH="$(mktemp)"

cleanup() {
  rm -f "$TEMP_PATH"
}
trap cleanup EXIT

language_for() {
  case "$1" in
    *.java) echo java ;;
    *.js) echo javascript ;;
    *.json) echo json ;;
    *.py) echo python ;;
    *.css) echo css ;;
    *.html) echo html ;;
    *.yml|*.yaml) echo yaml ;;
    *.gradle) echo groovy ;;
    *.properties) echo properties ;;
    *.sh|gradlew) echo bash ;;
    *.bat) echo batch ;;
    *.sql) echo sql ;;
    *.md) echo markdown ;;
    Dockerfile) echo dockerfile ;;
    *) echo text ;;
  esac
}

is_text_project_file() {
  case "$1" in
    *.java|*.js|*.json|*.py|*.css|*.html|*.yml|*.yaml|*.gradle|*.properties|*.sh|*.bat|*.sql|*.md|Dockerfile|gradlew)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

cd "$PROJECT_ROOT"

FILES="$(
  git ls-files --cached --others --exclude-standard \
    | LC_ALL=C sort \
    | while IFS= read -r file; do
        if [ "$file" != "docs/CODE_SNAPSHOT_2026-08-10.md" ] && is_text_project_file "$file"; then
          printf '%s\n' "$file"
        fi
      done
)"

FILE_COUNT="$(printf '%s\n' "$FILES" | sed '/^$/d' | wc -l | tr -d ' ')"
GENERATED_AT="$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S KST')"
# 생성 스크립트 자체도 스냅샷에 포함되므로, 소스 안에 완성된 fence 문자열을 쓰지 않는다.
# 6개의 물결표를 런타임에 조합하면 기존 Markdown의 ``` 블록도 그대로 담을 수 있다.
CODE_FENCE="$(printf '%s%s' '~~~' '~~~')"

{
  printf '# Asset Dashboard — 전체 코드·문서 스냅샷\n\n'
  printf '생성 시각: %s  \n' "$GENERATED_AT"
  printf '포함 파일: %s개  \n' "$FILE_COUNT"
  printf '생성 스크립트: `scripts/export-code-to-markdown.sh`\n\n'
  printf '> 회사 환경의 GPT에 현재 프로젝트를 단일 Markdown으로 전달하기 위한 스냅샷이다.  \n'
  printf '> `build/`, `.gradle/`, `.git/`, Gradle Wrapper JAR 및 기타 바이너리는 포함하지 않았다.  \n'
  printf '> 코드 블록 위의 경로가 원래 파일 위치다. 실제 실행·수정 시에는 첨부한 프로젝트 원본을 우선한다.\n\n'
  printf '## 사용 방법\n\n'
  printf '이 파일과 `docs/HANDOFF_2026-08-10.md`를 함께 읽도록 요청한다. 코드 수정 전 실제 프로젝트에서 `./gradlew clean test`를 실행하고, 스냅샷과 원본 파일이 일치하는지 확인한다.\n\n'
  printf '## 포함 파일 목록\n\n'
  printf '```text\n%s\n```\n\n' "$FILES"

  printf '%s\n' "$FILES" | while IFS= read -r file; do
    [ -n "$file" ] || continue
    language="$(language_for "$file")"
    checksum="$(shasum -a 256 "$file" | awk '{print $1}')"
    lines="$(wc -l < "$file" | tr -d ' ')"

    printf -- '---\n\n'
    printf '## `%s`\n\n' "$file"
    printf 'SHA-256: `%s` · %s lines\n\n' "$checksum" "$lines"
    printf '%s%s\n' "$CODE_FENCE" "$language"
    sed -e '$a\' "$file"
    printf '%s\n\n' "$CODE_FENCE"
  done
} > "$TEMP_PATH"

mkdir -p "$(dirname "$OUTPUT_PATH")"
mv "$TEMP_PATH" "$OUTPUT_PATH"
trap - EXIT

printf 'Created %s (%s files)\n' "$OUTPUT_PATH" "$FILE_COUNT"
