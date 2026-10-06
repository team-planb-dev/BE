# Phase 6 CI 도입 결과

작성일: 2026-10-06  
Issue: [#143](https://github.com/team-planb-dev/BE/issues/143)  
PR: [#144](https://github.com/team-planb-dev/BE/pull/144) (`dev`), [#145](https://github.com/team-planb-dev/BE/pull/145) (`main`)  
계획: `docs/perf/refactoring-plan.md` Phase 6

## 1. 결론

> `dev`·`main` 대상 PR과 push마다 GitHub Actions가 `./gradlew build`를 실행함. `main`·`dev`는
> 이 `build` check가 통과해야 병합할 수 있도록 브랜치 보호를 설정함. 배포는 계속 Railway가
> `main`을 직접 배포하며 CI에 포함하지 않음. 첫 실행에서 로컬 Redis에 의존하던 테스트 1개를 발견해
> 수정함.

## 2. 배경

- 도입 전 저장소에는 CI workflow와 브랜치 보호가 없었음
- Railway가 `main`을 자동 배포하므로, 깨진 코드가 병합되면 검증 없이 운영에 반영되는 구조였음
- 테스트 결과는 로컬에서 실행한 사람이나 AI 에이전트의 보고에 의존했음
- 깨끗한 환경에서만 드러나는 문제가 실제로 있었음
  - 로컬 `build/classes`에 남은 옛 클래스로 인한 실패(#121)
  - 실행 환경변수에 따라 결과가 바뀌는 테스트(#135)

## 3. 구성

`.github/workflows/ci.yml`

| 항목 | 설정 | 이유 |
|---|---|---|
| 실행 시점 | `dev`·`main` 대상 `pull_request`, `dev`·`main` `push` | 병합 전 검증과 병합 결과 재확인 |
| 실행 명령 | `./gradlew build` | 계획서의 PR 필수 게이트가 모두 이 명령에 포함됨: compile, unit, 외부 호출 없는 integration(Testcontainers MySQL 8.4·Redis 7.2), invariant 회귀 하네스 |
| 외부 API 테스트 | 제외 | 기존 `test` 설정의 `excludeTags 'external'`. 실제 키·비용이 필요해 수동 실행으로 유지 |
| JDK·Gradle | Temurin 21, `gradle/actions/setup-gradle` | Gradle 캐시와 wrapper jar 검증. 캐시 쓰기는 기본 브랜치에서만(action 기본값) |
| 권한 | `contents: read` | 빌드에 쓰기 권한이 필요 없음 |
| 중복 실행 | 같은 PR·브랜치의 이전 실행 취소 | 새 커밋이 오면 이전 결과는 의미가 없음 |
| 실패 진단 | 실패 시에만 테스트 리포트 artifact 업로드, 7일 보관 | runner 로그만으로는 실패 테스트 확인이 번거로움 |
| action 고정 | 모든 action을 commit SHA로 고정하고 버전 태그를 주석으로 남김 | 태그 이동에 따른 공급망 위험 방지. Docker 이미지를 digest로 고정하는 저장소 관례와 맞춤 |
| secret | 사용하지 않음 | 환경변수를 비운 `env -i HOME=… PATH=… ./gradlew clean build`가 로컬에서 810건 통과 |
| 배포 | 포함하지 않음 | Railway가 `main`을 직접 배포함. CI에 배포를 넣으면 배포가 이중이 됨 |
| 성능·k6 | 포함하지 않음 | 공유 runner의 지연 편차가 커서 지연 게이트를 두지 않는다는 계획서 원칙 |

## 4. 브랜치 보호

2026-10-06 `main`·`dev`에 같은 설정을 적용함.

| 항목 | 값 | 이유 |
|---|---|---|
| 필수 status check | `build` (GitHub Actions) | CI 통과 전 병합 차단 |
| 브랜치 최신화 강제(strict) | 끔 | 다른 PR이 먼저 병합될 때마다 갱신을 강제하면 부담이 큼. `dev → main` 동기화 PR에서 다시 검증됨 |
| 관리자에게도 적용 | 켬 | 사용자 토큰을 쓰는 AI 에이전트도 같은 조건으로 병합하게 하기 위함. 끄면 관리자 우회가 가능해 필수 조건이 아니게 됨 |
| 리뷰 승인 | 요구하지 않음 | 1인 운영 저장소라 필수로 두면 병합할 수 없음 |
| force push·브랜치 삭제 | 금지 | 기존 작업 규칙과 같음 |

필수 check가 있으면 보호 브랜치에 check를 통과하지 않은 커밋을 직접 push할 수 없음. 변경은 PR을 통해서만
들어감. 긴급 상황에서 CI 자체가 동작하지 않으면 저장소 관리자가 보호 설정을 일시 해제해야 함.

## 5. 검증

| 실행 | 대상 | 결과 |
|---|---|---|
| [37449764074](https://github.com/team-planb-dev/BE/actions/runs/37449764074) | #144 PR 첫 실행 | ❌ 테스트 810건 중 4건 실패(§6) |
| [37450608577](https://github.com/team-planb-dev/BE/actions/runs/37450608577) | #144 PR 수정 후 | ✅ 5분 50초 |
| [37451333852](https://github.com/team-planb-dev/BE/actions/runs/37451333852) | `dev` push | ✅ 5분 44초 |
| [37451381841](https://github.com/team-planb-dev/BE/actions/runs/37451381841) | #145 PR (`main` 대상) | ✅ 5분 31초 |
| [37452032796](https://github.com/team-planb-dev/BE/actions/runs/37452032796) | `main` push | ✅ 6분 2초 |

- runner에서 건너뛰는 테스트는 1건임. `ObservabilityConfigTest.prometheusConfigPassesPromtoolSyntaxCheck`는
  `promtool`이 있을 때만 실행하는 `assumeTrue` 테스트이고, hosted runner에는 `promtool`이 없음
- 병합 후 `dev`와 `main`의 파일 내용이 같음(tree 동일)

## 6. 첫 실행에서 발견한 문제

**증상:** `UserAuthCacheRepositoryTest` 4건이 `RedisConnectionFailureException`으로 실패함.

**원인:**
- 테스트는 Testcontainers Redis를 띄우고 `spring.data.redis.host`·`port`만 등록했음
- 기본 `local` 프로필의 `spring.data.redis.url: ${REDIS_URL:redis://localhost:6379}`이 함께 적용됐고,
  Spring Boot에서는 `url`이 host·port보다 우선함
- 그래서 테스트는 컨테이너가 아니라 `localhost:6379`에 연결하고 있었음

**로컬에서 통과한 이유:** 개발 장비에 Redis가 6379로 실행 중이었음. 이 테스트의 `@AfterEach`가 `FLUSHALL`을
호출하므로, 로컬에서 테스트를 실행할 때마다 개발 장비의 Redis 데이터가 지워졌을 수 있음.

**재현:** `REDIS_URL=redis://localhost:6390`(사용하지 않는 포트)으로 실행하자 로컬에서도 4건 모두 같은 오류로 실패함.

**수정:** `spring.data.redis.url`도 컨테이너 주소로 등록함. `IntegrationTest`와
`UserTokenCacheRepositoryTest`가 이미 쓰던 방식임. `REDIS_URL`을 빈 포트로 지정한 경우와 지정하지 않은
경우 모두 통과하고, CI에서도 통과함.

## 7. 하지 않은 것과 남은 선택

| 항목 | 상태 | 비고 |
|---|---|---|
| Railway의 CI 대기 설정 | 미설정 | Railway 서비스 설정에서 GitHub check 통과 후 배포하는 옵션을 확인해 켤 수 있음. 브랜치 보호로 병합 단계는 이미 막혀 있음 |
| k6 smoke·지연 게이트 | 미도입 | 고정 실행 환경 확보 전에는 두지 않음 |
| 실제 LLM 품질·외부 API·부하 테스트 | 수동 실행 유지 | 키·비용·실행 환경 필요 |
| `promtool` 문법 검사 | CI에서 건너뜀 | 필요하면 runner에 `promtool` 설치 단계를 추가할 수 있음 |
| `ubuntu-latest` 이미지 변경 | 관찰 | GitHub 안내상 2026-10-19부터 Ubuntu 26으로 이전됨. 실패하면 runner 버전을 고정함 |
| Gradle 9.2.1 | 관찰 | `setup-gradle`이 업데이트 권고 경고를 남김. 빌드에는 영향 없음 |
