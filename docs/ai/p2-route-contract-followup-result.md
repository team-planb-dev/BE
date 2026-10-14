# P2 경로 실패 분류·선택/최종 조회 계약 — 1·2단계 결과

작성일: 2026-10-13
작업 경로: `/private/tmp/planb-p24d-time-contract`
브랜치: `codex/p2-4d-time-contract-recovery`
기준 HEAD: `096616e27a865e437647738b1ba5adf016c77f63`
상태: 로컬 구현·검증. 실제 API 품질 채택과 dev 병합은 후속 작업

## 1. 범위와 기존 변경의 구분

- 사용자 확정 범위: 후속 작업 1·2단계만 수행. 3/4·5/6·7은 Claude 위임
- 시작 시점에 Claude 경로 관측·복약 계약·재현 테스트 등 미커밋 변경 존재. 기존 변경 보존
- 이번 코드 변경: 10개 기존 파일. 새 범용 오류 계층·캐시·공유 경로 저장·의존성 추가 없음
- 기존 `KakaoRouteFailure`·handler의 상태/코드/좌표 로그·meter·resolver의 실패 정보 전달을 활용. 이 기반의 신규 작성자로 Codex를 표시하지 않는 기준
- 이번 전용 패치: `evidence/p2-contract-followup-20261013/codex-stage12-only.patch`. 깨끗한 dev가 아닌 시작 당시 미커밋 상태에 적용하는 패치
- 전체 시작 상태는 `before-tracked.patch`, 운영 소스 해시는 `before-manifest.json`, 이번 변경의 전후 해시는 `handoff-manifest.json` 참조

## 2. 1단계 — 실패 원인의 최종 전달

- `PlanEditExceptionEnum`: `PLAN.EXCEPTION.ROUTE_LOOKUP_FAILED` 추가
- `PlanService`: 공급자 실패 정보가 있는 최종 구간/장소 재선택 실패를 새 코드로 전달. 공급자 실패 정보 없는 검증 실패는 기존 `INVALID_AI_PLACE` 유지
- 생성·편집·날짜 재구성은 기존 공통 경로 계산 사용. 미확인 원거리 이동시간을 0·자동차·직선거리로 대체하지 않는 계약 유지
- 공급자 오류에서 AI 날짜 재구성을 반복하지 않는 계약. 오류 코드와 `rebuildDay` 1회 호출 단언
- `ChatAiReplyMessageHelper`: 경로 확인 실패 안내, 내부 장소명·오류 코드 미노출. 메시지 전송 구조는 변경 없음
- `TravelController`: 공개 오류 코드 설명 갱신
- HTTP400만으로 쿼터·장애 단정 금지. 이전 장애의 본문 누락 구간은 UNKNOWN 유지

## 3. 2단계 — Tool 선택 비교와 Java 최종 확인

- `TravelPlanPrompt`·`RebuildPlanDayPrompt`: 모든 구간 getRoute 의무를 후보 순서·선택 비교가 필요한 경우로 제한
- `PlanTourismTool`의 getRoute 설명도 동일 책임으로 정렬. 이름·인자 schema·Tool 등록 유지
- 생성 선택 DTO의 travelMinutes 미출력·Java 확정 흐름 유지
- 실제 Tool 호출 성공과 Java 최종 확인 결과는 별개의 값. Tool5분→최종35분에서35분 적용 fixture
- Tool 비교 성공 후 Java 최종400 발생 fixture에서 이전5분 재사용 금지. 누락과 typed failure 보존
- 기존 Tool 없는 조회·날짜 연결·좌표·이동수단·500m 이내 추정·조회 동시성·동일 resolve 내 구간 중복 제거 테스트 유지
- DB/Redis 경로 캐시 및 Tool 결과의 추가 공유 저장 미도입. 현재 같은 resolve 안의 기존 중복 제거만 유지
- 위 테스트는 책임 계약 증거. 실제 Tool 호출 감소·실제 AI 선택 품질 개선·과거400 인과의 증거로 해석 금지

## 4. TDD와 리뷰

- 공급자 실패 코드 기존 두 서비스 테스트: 변경 전 RED → 구현 후 GREEN
- 채팅 안내 신규 테스트: 변경 전 RED → 구현 후 GREEN
- 날짜 재구성 신규 테스트: 기존 분류 대조 RED → 현재 공급자 즉시 종료 GREEN
- Tool→Java 신규2건은 기존 책임을 고정하는 특성 테스트. 구현 결함 RED로 표시하지 않는 기준
- 관련6클래스:114건, 실패0·오류0·스킵0
- `INVALID_AI_PLACE` 재구성 catch는 코드상 유지. 기존 잘못된 날짜·변경 없는 날짜 테스트는 다른 재시도 분기이며 해당 catch의 직접 증거로 표시 금지
- 구현 review-loop:1차8/10 →2차9/10. 최종 Critical0·Important0. 좌표 인자 들여쓰기 Minor 수정
- 전체 무료 회귀: 1015건, 실패0·오류0·스킵0. 클래스140개, BUILD SUCCESSFUL(3분55초)
- `git diff --check` 통과. 전용 패치 적용 후10개 파일 사후SHA256 일치

## 5. 실제 환경과 후속 위임

- 이번 작업에서 실제 OpenAI·Kakao·TourAPI 호출·externalTest·Grafana 실측 없음
- 공급자400의 과거 근본 원인·실제 중복 구간 수·실행 중STOMP 종료 원인은 미확정
- 3/4: 편집 대상 외 원본 보존(P2-6A), 장시간STOMP 전달(P2-6B)
- 5/6: 알레르기 후보 제외 계약(P2-4E E1), 잔여 식사 창의 실행 가능성(P2-4E E2)
- 7: 통합 무료 회귀 → 승인 범위 실제API/REST/STOMP 품질 재측정 → 채택 판정·dev 반영
- main은 배포 환경. 별도 지시 전 main sync 금지
- 공급자 실패 분리는 진단 개선이며 실제 생성 품질 전체 해결로 보고하지 않는 기준

## 6. 공식 근거와 보관

- [Kakao 오류 코드](https://developers.kakao.com/docs/ko/rest-api/error-code): HTTP상태와 공급자 본문code의 별도 확인
- [Kakao 담당자 답변](https://devtalk.kakao.com/t/api/151435/2): DB/캐시 저장·재사용 제외 근거. 같은 요청의 추가 공유 저장 허용 여부는 미확정
- [Spring AI Tool 명세](https://docs.spring.io/spring-ai/reference/api/tools.html): `@Tool.description`에 용도·호출 조건 명시. 현재 문서는2.0.1이므로 실행 동작은2.0.0 source와 별도 대조
- 버전 고정·재시도·STOMP 근거: `p2-orchestration-contract-followup-research.md`
- Gradle 실제 해석: spring-messaging7.0.3. `p2-stage12-spring-version.log` 참조
- 구조 검토 HTML: `/private/tmp/architecture-review-20261013-route-contract.html`
- 프로젝트와 보관 디렉터리에 문서·검증자료 사본 유지. 계획서2종 스테이징 금지

이번 전용 패치 SHA256: `44a9bcb2490c6df3ad2293deed3005489e65b7b56491acc69c7e1602030ba2f4`


## dev 통합 준비 (2026-10-13, #190)

- 기준: dev `096616e27a865e437647738b1ba5adf016c77f63`. P2-4D 시간 복구·복약 중첩 허용, 경로 오류 분류와 Tool 계약, P2-6A 대상 외 선택 보존, P2-6B 하네스 수신·관측, P2-4E 알레르기 문자열 정책·점심 여유 압축의 통합
- 코드 45개 파일을 실측 작업 트리와 바이트 단위 대조. 도메인별 코드 커밋 5개와 결과 문서 커밋 분리. 실제 API 추가 호출 없이 무료 회귀 재검증
- 이번 통합은 품질 개선 완료 판정이 아님. 생성 18/18→17/18, 시간 계약 16/18→15/18, 필수 식사 창 70/72→66/68, 복약 78/78→74/74. 식사·복약 분모는 성공 반환 일정에 한정
- 등록명·승인 분류어 위반 3→0. 식재료 판정 불가 102슬롯은 별도이며 알레르기 안전성 증명과 구분
- 성공 미리보기 REST 2건·STOMP 1건의 범위 보존 3/3. 날짜 미지정 재질문 1건, 메뉴만 변경 `EDIT_NOT_APPLIED` 1건 미해결
- STOMP 취소 후 늦은 캐시 재생성·이전 요청의 덮어쓰기는 기존 문제의 특성 테스트이며 해결 완료가 아님. 브라우저·운영 프록시 검증과 과거 연결 종료 원인 확정은 범위 밖
- 잔여 점심 실패, 영업시간 미평가, 알레르기 문자열 오탐·미확인 범위 유지
- raw·JSONL·키·토큰·계획서·DAG 설계는 이번 Git 반영 대상에서 제외. 원본 증거와 분석 helper는 지정 보관 디렉터리에 보존
- main 배포 환경은 변경 대상에서 제외. DAG 구조 개편은 Claude 교차검증 후 별도 결정

- dev 통합 전 재실행: `./gradlew test --rerun --console=plain`, 143클래스·1073건, 실패0·오류0·건너뜀0, 4분19초. GitHub CI는 PR 생성 후 확인
