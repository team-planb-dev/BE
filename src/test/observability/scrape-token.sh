#!/usr/bin/env bash
# Prometheus scrape용 임시 JWT 파일을 만들고 지운다. 로컬 부하 측정 전용이다.
#
#   scrape-token.sh create <file>   테스트 계정을 만들고 로그인해 Bearer 접두사를 뗀 raw JWT를 <file>(0600)에 쓴다
#   scrape-token.sh remove <file>   <file>을 지운다. 없어도 성공이다
#
# BASE_URL: 애플리케이션 주소 (기본 http://localhost:8080)
#
# 관리 포트는 사용자 JWT를 요구하고 액세스 토큰은 24시간 뒤 만료된다. 그래서 이 방식은 짧은 로컬 측정에서만 쓴다.
# 토큰은 어떤 출력에도 남기지 않는다. 실패하면 파일을 만들지 않고 0이 아닌 코드로 끝난다.
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

        signup_status=$(curl -s -o /dev/null -w '%{http_code}' \
            -X POST -H 'Content-Type: application/json' \
            -d "$signup_body" "${base_url}/api/v1/user/create")

        if [ "$signup_status" != "201" ]; then
            echo "계정 생성 실패: HTTP ${signup_status}" >&2
            exit 1
        fi

        login_body=$(printf '{"username":"%s","password":"%s"}' "$username" "$password")

        # 응답 본문은 버리고 헤더만 받는다.
        login_headers=$(curl -s -D - -o /dev/null \
            -X POST -H 'Content-Type: application/json' \
            -d "$login_body" "${base_url}/login")

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
