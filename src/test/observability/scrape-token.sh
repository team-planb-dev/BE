#!/usr/bin/env bash
# 로컬 부하 측정용 Prometheus scrape JWT 파일 생성·삭제

#   scrape-token.sh create <file>   테스트 계정 로그인 후 raw JWT를 <file>(0600)에 기록
#   scrape-token.sh remove <file>   <file> 삭제, 파일이 없어도 성공

# BASE_URL: 애플리케이션 주소 (기본 http://localhost:8080)

# 관리 포트의 사용자 JWT와 24시간 만료로 인한 단기 로컬 측정 한정
# 토큰 출력 금지, 실패 시 파일 미생성과 0 이외 종료 코드
set -euo pipefail

usage() {
    echo "사용법: $0 create|remove <token-file>" >&2
    exit 2
}

[ "$#" -eq 2 ] || usage

command="$1"
file="$2"
base_url="${BASE_URL:-http://localhost:8080}"

case "$command" in
    remove)
        rm -f -- "$file"
        ;;

    create)
        umask 077

        suffix="$(date +%s)-$RANDOM"
        username="planb-observe-${suffix}@example.com"
        password='test1234!'

        signup_body=$(printf '{"username":"%s","nickname":"observe-%s","password":"%s","recoveryQuestion":"FIRST_PET","recoveryAnswer":"콩이","ageRequirementAgreed":true,"serviceTermsAgreed":true,"privacyCollectionAgreed":true}' \
            "$username" "$suffix" "$password")

        # curl 연결 실패의 HTTP 000 변환과 원인 출력
        signup_status=$(curl -s -o /dev/null -w '%{http_code}' \
            -X POST -H 'Content-Type: application/json' \
            -d "$signup_body" "${base_url}/api/v1/user/create") || signup_status=000

        if [ "$signup_status" != "201" ]; then
            echo "계정 생성 실패: HTTP ${signup_status}" >&2
            exit 1
        fi

        login_body=$(printf '{"username":"%s","password":"%s"}' "$username" "$password")

        # 응답 본문 제외와 헤더 조회
        login_headers=$(curl -s -D - -o /dev/null \
            -X POST -H 'Content-Type: application/json' \
            -d "$login_body" "${base_url}/login") || login_headers=""

        token=$(printf '%s\n' "$login_headers" \
            | tr -d '\r' \
            | awk 'tolower($1) == "authorization:" && $2 == "Bearer" { print $3; exit }')

        if [ -z "$token" ]; then
            echo "로그인 응답에 Bearer 토큰이 없음" >&2
            exit 1
        fi

        printf '%s' "$token" > "$file"
        echo "scrape 토큰 파일 생성: $file" >&2
        ;;

    *)
        usage
        ;;
esac
