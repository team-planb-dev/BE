#!/usr/bin/env bash
# 로컬 부하 측정 1회: Prometheus·Grafana를 띄우고 k6를 remote write로 실행한다.
#
# 전제: 애플리케이션(loadtest 프로파일), 스텁, MySQL, Redis가 이미 떠 있다.
#       재현 절차는 docs/perf/baseline-2026-09-28.md 5절, 관측 스택은 docs/perf/phase4a-observability.md 참고.
#
# 환경변수 (모두 선택):
#   BASE_URL            애플리케이션 주소 (호스트에서 본 값, 기본 http://localhost:8080)
#   MANAGEMENT_URL      관리 포트 주소 (기본 http://localhost:8081)
#   TESTID              이번 실행의 식별자. k6 지표의 testid 라벨이 된다 (기본 UTC 시각)
#   SCENARIO, PLAN_RATE, PLAN_DURATION, PRE_ALLOCATED_VUS, MAX_VUS, STUB_PLAN_START_DATE  k6 스크립트로 전달한다
#   DOCKER              docker 실행 파일 (기본 docker)
#
# 임시 JWT 파일은 성공·실패·중단과 관계없이 종료 때 지운다. 토큰이 없어지면 travel-app target은 DOWN이 되며 정상이다.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
docker="${DOCKER:-docker}"

base_url="${BASE_URL:-http://localhost:8080}"
management_url="${MANAGEMENT_URL:-http://localhost:8081}"
testid="${TESTID:-$(date -u +%Y%m%dT%H%M%SZ)}"

# 다이제스트로 고정한다. remote write 출력은 실험 기능이라 k6 버전이 바뀌면 동작이 달라질 수 있다.
k6_image='grafana/k6@sha256:e66db15b860113878fa74670e31f5e274830b7b6e42c8bff28b2f2d86a257603'

token_dir="$(mktemp -d)"

cleanup() {
    "$here/scrape-token.sh" remove "$token_dir/token" || true
    rmdir "$token_dir" 2>/dev/null || true
    echo "임시 토큰 파일 삭제 완료" >&2
}
trap cleanup EXIT

BASE_URL="$base_url" "$here/scrape-token.sh" create "$token_dir/token"

SCRAPE_TOKEN_DIR="$token_dir" "$docker" compose -f "$here/docker-compose.yml" up -d

echo "Prometheus target 확인 중..." >&2
for _ in $(seq 1 30); do
    up=$(curl -s -G 'http://localhost:9090/api/v1/query' --data-urlencode 'query=up{job="travel-app"}' \
        | tr -d ' ' | grep -o '"value":\[[0-9.]*,"1"\]' || true)
    [ -n "$up" ] && break
    sleep 1
done

if [ -z "${up:-}" ]; then
    echo "travel-app target이 30초 안에 up이 되지 않음. http://localhost:9090/targets 에서 오류를 확인" >&2
    exit 1
fi

started_ms=$(( $(date +%s) * 1000 ))

k6_env=(-e "BASE_URL=http://host.docker.internal:${base_url##*:}" -e "MANAGEMENT_URL=http://host.docker.internal:${management_url##*:}")

for name in SCENARIO PLAN_RATE PLAN_DURATION PRE_ALLOCATED_VUS MAX_VUS STUB_PLAN_START_DATE; do
    [ -n "${!name:-}" ] && k6_env+=(-e "$name=${!name}")
done

set +e
"$docker" run --rm \
    --add-host=host.docker.internal:host-gateway \
    -v "$here/../k6:/scripts:ro" \
    -e K6_PROMETHEUS_RW_SERVER_URL=http://host.docker.internal:9090/api/v1/write \
    -e "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),avg,max" \
    "$k6_image" run \
    -o experimental-prometheus-rw \
    --tag "testid=$testid" \
    "${k6_env[@]}" \
    /scripts/travel-plan.js
k6_status=$?
set -e

# 마지막 push가 Prometheus에 반영될 시간을 준다.
sleep 6
ended_ms=$(( $(date +%s) * 1000 ))

echo >&2
echo "testid=$testid  k6 종료 코드=$k6_status" >&2
echo "Grafana: http://localhost:3000/d/travel-load?var-testid=${testid}&from=${started_ms}&to=${ended_ms}" >&2
echo "중지: SCRAPE_TOKEN_DIR=/tmp docker compose -f $here/docker-compose.yml down   (지표는 volume에 남는다. 지우려면 -v)" >&2

exit "$k6_status"
