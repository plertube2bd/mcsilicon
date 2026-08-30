#!/usr/bin/env bash
#
# MCSilicon 리눅스 빌드 스크립트
#
# 사용법:
#   ./build.sh                 기본 빌드 (build 태스크)
#   ./build.sh --clean         clean 후 빌드
#   ./build.sh --task jar      다른 그레이들 태스크 실행 (기본: build)
#   ./build.sh --offline       그레이들 --offline
#   ./build.sh -j4             그레이들 워커 4개로 병렬 빌드
#   ./build.sh -v              그레이들 출력을 --info로 자세히
#   ./build.sh -h              도움말
#
# 필요한 것: JDK 21 이상. 그 외 그레이들은 이 스크립트가 알아서 준비한다
# (프로젝트에 ./gradlew가 있으면 그걸 쓰고, 없으면 시스템에 설치된 gradle로
#  공식 방법(`gradle wrapper`)을 통해 만들어준다).

set -euo pipefail

# ---------------------------------------------------------------------------
# 기본값 / 인자 파싱
# ---------------------------------------------------------------------------

TASK="build"
DO_CLEAN=0
OFFLINE=0
VERBOSE=0
JOBS=""
GRADLE_WRAPPER_VERSION="8.8"
REQUIRED_JAVA_MAJOR=21

print_help() {
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
}

while [ $# -gt 0 ]; do
    case "$1" in
        --clean)
            DO_CLEAN=1
            shift
            ;;
        --task)
            [ $# -ge 2 ] || { echo "오류: --task 뒤에 태스크 이름이 필요합니다" >&2; exit 1; }
            TASK="$2"
            shift 2
            ;;
        --offline)
            OFFLINE=1
            shift
            ;;
        -v|--verbose)
            VERBOSE=1
            shift
            ;;
        -j*)
            JOBS="${1#-j}"
            shift
            ;;
        -h|--help)
            print_help
            exit 0
            ;;
        *)
            echo "오류: 알 수 없는 인자 '$1' (도움말: ./build.sh -h)" >&2
            exit 1
            ;;
    esac
done

# ---------------------------------------------------------------------------
# 출력 도우미 (터미널이면 색상, 아니면 그냥 텍스트)
# ---------------------------------------------------------------------------

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    C_INFO=$'\033[1;34m'; C_OK=$'\033[1;32m'; C_ERR=$'\033[1;31m'; C_RESET=$'\033[0m'
else
    C_INFO=""; C_OK=""; C_ERR=""; C_RESET=""
fi

info() { printf '%s[빌드]%s %s\n' "$C_INFO" "$C_RESET" "$1"; }
ok()   { printf '%s[완료]%s %s\n' "$C_OK" "$C_RESET" "$1"; }
die()  { printf '%s[오류]%s %s\n' "$C_ERR" "$C_RESET" "$1" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 스크립트가 있는 디렉토리(=프로젝트 루트) 기준으로 동작
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd -P)"
cd "$SCRIPT_DIR"

[ -f "build.gradle" ] || die "build.gradle을 찾을 수 없습니다 - 이 스크립트는 프로젝트 루트에 있어야 합니다"

# ---------------------------------------------------------------------------
# 1) JDK 확인
# ---------------------------------------------------------------------------

info "Java 버전 확인 중..."

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    JAVA_BIN="$(command -v java || true)"
fi
[ -n "$JAVA_BIN" ] || die "java를 찾을 수 없습니다. JDK ${REQUIRED_JAVA_MAJOR}+를 설치하세요 (예: sdk install java 21-tem, Arch는 pacman -S jdk${REQUIRED_JAVA_MAJOR}-openjdk, Debian·Ubuntu는 apt install openjdk-${REQUIRED_JAVA_MAJOR}-jdk)"

JAVA_VERSION_STR="$("$JAVA_BIN" -version 2>&1 | head -n1)"
# "openjdk version \"21.0.3\" ..." 또는 "1.8.0_XX" 형태 둘 다 대응
JAVA_MAJOR="$(printf '%s\n' "$JAVA_VERSION_STR" | sed -n 's/^[^"]*"\([0-9]*\).*/\1/p')"
if [ "$JAVA_MAJOR" = "1" ]; then
    # 옛날 버전 표기(1.8 등) - 여기 프로젝트가 요구하는 21에는 못 미치므로 그대로 실패 처리됨
    JAVA_MAJOR="8"
fi

[ -n "$JAVA_MAJOR" ] || die "java -version 출력을 해석하지 못했습니다: $JAVA_VERSION_STR"
[ "$JAVA_MAJOR" -ge "$REQUIRED_JAVA_MAJOR" ] 2>/dev/null || die "JDK ${REQUIRED_JAVA_MAJOR} 이상이 필요합니다 (현재: $JAVA_VERSION_STR)"

ok "Java $JAVA_MAJOR 확인됨 ($JAVA_BIN)"

# ---------------------------------------------------------------------------
# 2) 그레이들 준비 - ./gradlew 있으면 그것, 없으면 시스템 gradle로 부트스트랩
# ---------------------------------------------------------------------------

info "그레이들 준비 중..."

if [ -x "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
    ok "기존 ./gradlew 사용"
else
    SYSTEM_GRADLE="$(command -v gradle || true)"
    if [ -n "$SYSTEM_GRADLE" ]; then
        info "./gradlew가 없어서 시스템 gradle로 래퍼를 생성합니다 (gradle wrapper --gradle-version ${GRADLE_WRAPPER_VERSION})..."
        # 이 프로젝트 폴더 안에서 바로 'gradle wrapper'를 돌리면, 래퍼를 만들기도 전에
        # Gradle이 먼저 build.gradle을 평가하면서 ForgeGradle 플러그인을 불러오려다
        # 실패한다(시스템 gradle이 너무 오래됐든 너무 최신이든 마찬가지). 그래서
        # 이 프로젝트와 무관한 빈 임시 디렉토리에서 래퍼만 만든 다음 여기로 복사한다.
        WRAPPER_TMP="$(mktemp -d)"
        trap 'rm -rf "$WRAPPER_TMP"' EXIT
        # Gradle 9+는 settings 파일이 없는 디렉토리에서는 아예 시작을 안 하므로 빈 걸 하나 둔다.
        : > "$WRAPPER_TMP/settings.gradle"
        if ! (cd "$WRAPPER_TMP" && "$SYSTEM_GRADLE" wrapper --gradle-version "$GRADLE_WRAPPER_VERSION" --distribution-type bin); then
            die "래퍼 생성이 실패했습니다. 시스템 gradle 버전이 이 프로젝트(ForgeGradle 6, Gradle 8.1~8.x 지원)와
너무 안 맞을 수 있습니다(너무 오래됐거나, 반대로 Gradle 9+처럼 너무 최신이거나).
SDKMAN으로 8.x 버전을 지정해서 설치한 뒤 다시 시도하세요:
  curl -s https://get.sdkman.io | bash
  sdk install gradle ${GRADLE_WRAPPER_VERSION}"
        fi
        cp "$WRAPPER_TMP"/gradlew ./gradlew
        cp "$WRAPPER_TMP"/gradlew.bat ./gradlew.bat
        mkdir -p gradle/wrapper
        cp "$WRAPPER_TMP"/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.jar
        cp "$WRAPPER_TMP"/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
        rm -rf "$WRAPPER_TMP"
        trap - EXIT
        chmod +x ./gradlew
        GRADLE_CMD="./gradlew"
        ok "./gradlew 생성 완료 - 다음 빌드부터는 이걸 그대로 씁니다"
    else
        die "그레이들이 없습니다. 다음 중 하나로 설치하세요:
  - SDKMAN:  curl -s https://get.sdkman.io | bash   그 다음  sdk install gradle ${GRADLE_WRAPPER_VERSION}
  - 배포판 패키지 관리자 (Arch: pacman -S gradle / Debian·Ubuntu: apt install gradle)
설치 후 이 스크립트를 다시 실행하면 자동으로 ./gradlew를 만들어줍니다."
    fi
fi

# ---------------------------------------------------------------------------
# 3) 그레이들 인자 구성
# ---------------------------------------------------------------------------

GRADLE_ARGS=()
[ "$OFFLINE" -eq 1 ] && GRADLE_ARGS+=("--offline")
[ "$VERBOSE" -eq 1 ] && GRADLE_ARGS+=("--info") || GRADLE_ARGS+=("--console=plain")
[ -n "$JOBS" ] && GRADLE_ARGS+=("--max-workers=$JOBS")

# ---------------------------------------------------------------------------
# 4) 빌드 실행
# ---------------------------------------------------------------------------

if [ "$DO_CLEAN" -eq 1 ]; then
    info "clean 실행 중..."
    "$GRADLE_CMD" clean "${GRADLE_ARGS[@]}"
fi

info "'$TASK' 태스크 실행 중... (처음 실행이면 Forge/Minecraft 관련 파일을 내려받느라 시간이 꽤 걸릴 수 있습니다)"
"$GRADLE_CMD" "$TASK" "${GRADLE_ARGS[@]}"

ok "빌드 완료"

# ---------------------------------------------------------------------------
# 5) 결과물 위치 안내
# ---------------------------------------------------------------------------

if [ -d "build/libs" ]; then
    JARS="$(find build/libs -maxdepth 1 -name '*.jar' 2>/dev/null || true)"
    if [ -n "$JARS" ]; then
        echo
        info "생성된 jar 파일:"
        printf '%s\n' "$JARS" | while IFS= read -r jar; do
            printf '  %s (%s)\n' "$jar" "$(du -h "$jar" | cut -f1)"
        done
        echo
        info "포지 mods 폴더에 복사하면 바로 로드됩니다 (예: ~/.minecraft/mods/)"
    fi
fi
